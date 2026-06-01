package com.BWPStudio.JITAIWizard.experiment

import android.content.Context
import android.util.Log
import com.BWPStudio.JITAIWizard.datalayer.WearMessageSender
import com.BWPStudio.JITAIWizard.triggers.TriggerBus
import com.example.jitaicompanion.convention.models.EngineStateSnapshot
import com.example.jitaicompanion.convention.models.EventKind
import com.example.jitaicompanion.convention.models.Experiment
import com.example.jitaicompanion.convention.models.ExperimentEvent
import com.example.jitaicompanion.convention.models.WsEnvelope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

enum class EngineStatus { IDLE, RUNNING, PAUSED, FINISHED }
enum class EngineMode { MANUAL, AUTO }

data class EngineState(
    val status: EngineStatus = EngineStatus.IDLE,
    val mode: EngineMode = EngineMode.MANUAL,
    val experiment: Experiment? = null,
    val currentEventIndex: Int = 0,
    val elapsedSecInEvent: Float = 0f,
    val waitingForActionId: String? = null,
    val waitingForTriggerId: String? = null,
    val randomWaitRemainingSec: Float? = null,
    val runId: String? = null
) {
    val currentEvent: ExperimentEvent?
        get() = experiment?.events?.getOrNull(currentEventIndex)
}

class ExperimentEngine(
    private val context: Context,
    private val store: ExperimentStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _state = MutableStateFlow(EngineState(experiment = store.active.value))
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _envelopes = MutableSharedFlow<WsEnvelope>(replay = 0, extraBufferCapacity = 64)
    val envelopes = _envelopes.asSharedFlow()

    private var tickerJob: Job? = null
    private var triggerCollectJob: Job? = null
    private var responseCollectJob: Job? = null

    init {
        scope.launch {
            store.active.collect { exp ->
                if (_state.value.status == EngineStatus.IDLE || _state.value.status == EngineStatus.FINISHED) {
                    _state.value = _state.value.copy(experiment = exp)
                }
            }
        }
        triggerCollectJob = scope.launch {
            TriggerBus.flow.collect { evt ->
                runCatching { emitEnvelope(WsEnvelope.TYPE_TRIGGER, json.encodeToString(evt)) }
                    .onFailure { Log.e("ExperimentEngine", "encode trigger failed", it) }
                val s = _state.value
                if (s.status == EngineStatus.RUNNING && s.waitingForTriggerId == evt.triggerId) {
                    advance("trigger:${evt.triggerId}")
                }
            }
        }
        responseCollectJob = scope.launch {
            InterventionResponseBus.flow.collect { resp ->
                val s = _state.value
                runCatching { emitEnvelope(WsEnvelope.TYPE_INTERVENTION_FINISHED, json.encodeToString(resp)) }
                    .onFailure { Log.e("ExperimentEngine", "encode response failed", it) }
                if (s.status == EngineStatus.RUNNING) {
                    val waitingFor = s.waitingForActionId
                    val advance = waitingFor != null && (
                        waitingFor == "*" ||           // WaitForPrevious: any response
                        resp.actionId == null ||       // legacy: null actionId matches any
                        resp.actionId == waitingFor    // WaitForAction: exact id match
                    )
                    if (advance) advance("action:$waitingFor")
                }
            }
        }
    }

    fun dispatch(command: String, targetEventIndex: Int? = null) {
        when (command.lowercase()) {
            "start" -> start(mode = EngineMode.MANUAL)
            "auto" -> start(mode = EngineMode.AUTO)
            "pause" -> pause()
            "stop" -> stop()
            "next" -> advance("manual-next")
            "prev" -> previous()
            "jump" -> targetEventIndex?.let { jumpTo(it) }
        }
    }

    @Synchronized
    fun start(mode: EngineMode) {
        val exp = store.active.value
        if (exp.events.isEmpty()) return
        val runId = UUID.randomUUID().toString()
        SessionLogStore.currentRunId = runId
        _state.value = EngineState(
            status = EngineStatus.RUNNING,
            mode = mode,
            experiment = exp,
            currentEventIndex = 0,
            elapsedSecInEvent = 0f,
            runId = runId
        )
        emitEnvelope(WsEnvelope.TYPE_RUN_STARTED, "{\"runId\":\"$runId\",\"experimentId\":\"${exp.id}\",\"mode\":\"$mode\"}")
        enterEvent(0)
        ensureTicker()
        emitState()
    }

    @Synchronized
    fun pause() {
        if (_state.value.status == EngineStatus.RUNNING) {
            _state.value = _state.value.copy(status = EngineStatus.PAUSED)
            emitState()
        }
    }

    @Synchronized
    fun stop() {
        val prevRunId = _state.value.runId
        tickerJob?.cancel(); tickerJob = null
        _state.value = _state.value.copy(
            status = EngineStatus.IDLE,
            elapsedSecInEvent = 0f,
            waitingForActionId = null,
            waitingForTriggerId = null,
            randomWaitRemainingSec = null,
            runId = null
        )
        SessionLogStore.currentRunId = null
        emitEnvelope(WsEnvelope.TYPE_RUN_STOPPED, "{\"runId\":${prevRunId?.let { "\"$it\"" } ?: "null"}}")
        emitState()
    }

    @Synchronized
    fun previous() {
        val s = _state.value
        if (s.status != EngineStatus.RUNNING && s.status != EngineStatus.PAUSED) return
        val target = (s.currentEventIndex - 1).coerceAtLeast(0)
        jumpTo(target)
    }

    @Synchronized
    fun jumpTo(index: Int) {
        val s = _state.value
        val exp = s.experiment ?: return
        if (index !in exp.events.indices) return
        enterEvent(index)
    }

    private fun advance(reason: String) {
        val s = _state.value
        val exp = s.experiment ?: return
        val next = s.currentEventIndex + 1
        scope.launch {
            SessionLogStore.append(source = "engine", kind = "advance", payloadJson = "{\"reason\":\"$reason\",\"from\":${s.currentEventIndex},\"to\":$next}")
        }
        if (next >= exp.events.size) {
            tickerJob?.cancel(); tickerJob = null
            _state.value = s.copy(status = EngineStatus.FINISHED, elapsedSecInEvent = 0f, waitingForActionId = null, waitingForTriggerId = null, randomWaitRemainingSec = null)
            emitEnvelope(WsEnvelope.TYPE_EVENT_ADVANCE, "{\"index\":-1,\"finished\":true}")
            emitState()
            return
        }
        enterEvent(next)
    }

    private fun enterEvent(index: Int) {
        val s = _state.value
        val exp = s.experiment ?: return
        val event = exp.events.getOrNull(index) ?: return
        val (waitAction, waitTrigger, randomRemaining) = when (val k = event.kind) {
            EventKind.Timed -> Triple(null, null, null)
            is EventKind.RandomWait -> {
                val span = (k.maxSec - k.minSec).coerceAtLeast(0f)
                val sec = k.minSec + (Math.random().toFloat() * span)
                Triple(null, null, sec)
            }
            is EventKind.WaitForAction -> Triple(k.actionId, null, null)
            is EventKind.WaitForTrigger -> Triple(null, k.triggerId, null)
            // "*" = advance on any intervention response, regardless of actionId
            EventKind.WaitForPrevious -> Triple("*", null, null)
        }
        _state.value = s.copy(
            currentEventIndex = index,
            elapsedSecInEvent = 0f,
            waitingForActionId = waitAction,
            waitingForTriggerId = waitTrigger,
            randomWaitRemainingSec = randomRemaining
        )
        emitEnvelope(WsEnvelope.TYPE_EVENT_ADVANCE, "{\"index\":$index,\"id\":\"${event.id}\"}")
        if (event.intervention != null && (event.kind is EventKind.WaitForAction || event.kind == EventKind.Timed)) {
            scope.launch {
                runCatching {
                    WearMessageSender(context).sendIntervention(Json.encodeToString(event.intervention!!))
                    SessionLogStore.append(source = "engine", kind = "intervention-sent", payloadJson = "{\"id\":\"${event.intervention!!.id}\"}")
                    emitEnvelope(WsEnvelope.TYPE_INTERVENTION_STARTED, "{\"id\":\"${event.intervention!!.id}\"}")
                }
            }
        }
        emitState()
    }

    private fun ensureTicker() {
        if (tickerJob != null) return
        tickerJob = scope.launch {
            val intervalMs = 250L
            while (isActive) {
                delay(intervalMs)
                val s = _state.value
                if (s.status != EngineStatus.RUNNING) continue
                val event = s.currentEvent ?: continue
                val newElapsed = s.elapsedSecInEvent + intervalMs / 1000f
                when (val k = event.kind) {
                    EventKind.Timed -> {
                        if (newElapsed >= event.duration) {
                            if (s.mode == EngineMode.AUTO) advance("timed-complete")
                            else _state.value = s.copy(elapsedSecInEvent = event.duration)
                        } else _state.value = s.copy(elapsedSecInEvent = newElapsed)
                    }
                    is EventKind.RandomWait -> {
                        val remaining = (s.randomWaitRemainingSec ?: 0f) - intervalMs / 1000f
                        if (remaining <= 0f) {
                            _state.value = s.copy(randomWaitRemainingSec = 0f)
                            advance("random-wait-elapsed")
                        } else _state.value = s.copy(randomWaitRemainingSec = remaining, elapsedSecInEvent = newElapsed)
                    }
                    is EventKind.WaitForAction,
                    is EventKind.WaitForTrigger,
                    EventKind.WaitForPrevious -> {
                        _state.value = s.copy(elapsedSecInEvent = newElapsed)
                        val timeout = if (event.duration > 0f) event.duration else 60f
                        if (newElapsed >= timeout && s.mode == EngineMode.AUTO) advance("timeout")
                    }
                }
                if (s.elapsedSecInEvent.toInt() != _state.value.elapsedSecInEvent.toInt()) emitState()
            }
        }
    }

    private fun emitState() {
        val s = _state.value
        val snap = EngineStateSnapshot(
            status = s.status.name,
            mode = s.mode.name,
            currentEventIndex = s.currentEventIndex,
            currentEventId = s.currentEvent?.id,
            currentEventKind = s.currentEvent?.kind?.let { it::class.simpleName },
            elapsedSecInEvent = s.elapsedSecInEvent,
            waitingForActionId = s.waitingForActionId,
            waitingForTriggerId = s.waitingForTriggerId,
            randomWaitRemainingSec = s.randomWaitRemainingSec,
            runId = s.runId,
            experimentId = s.experiment?.id,
            experimentName = s.experiment?.name
        )
        emitEnvelope(WsEnvelope.TYPE_STATE, json.encodeToString(snap))
    }

    private fun emitEnvelope(type: String, payloadJson: String) {
        scope.launch { _envelopes.emit(WsEnvelope(type, payloadJson)) }
    }
}
