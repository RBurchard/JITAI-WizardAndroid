package com.BWPStudio.JITAIWizard.triggers

import com.BWPStudio.JITAIWizard.datalayer.WatchDataRelay
import com.BWPStudio.JITAIWizard.triggers.detectors.HeartRateDetector
import com.BWPStudio.JITAIWizard.triggers.detectors.RapidMovementDetector
import com.BWPStudio.JITAIWizard.triggers.detectors.RepeatingMovementDetector
import com.example.jitaicompanion.convention.models.Trigger
import com.example.jitaicompanion.convention.models.TriggerEvent
import com.example.jitaicompanion.convention.models.TriggerKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object TriggerEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _triggers = MutableStateFlow<List<Trigger>>(emptyList())
    val triggers: StateFlow<List<Trigger>> = _triggers.asStateFlow()

    private var collectorJob: Job? = null
    private var detectors: List<Pair<Trigger, Any>> = emptyList()

    fun setTriggers(list: List<Trigger>) {
        _triggers.value = list
        rebuildDetectors()
        ensureCollector()
    }

    private fun rebuildDetectors() {
        detectors = _triggers.value
            .filter { it.enabled }
            .mapNotNull { t ->
                when (t.kind) {
                    is TriggerKind.RapidMovement -> t to RapidMovementDetector(t)
                    is TriggerKind.RepeatingMovement -> t to RepeatingMovementDetector(t)
                    is TriggerKind.HeartRate -> t to HeartRateDetector(t)
                    is TriggerKind.Manual -> null
                }
            }
    }

    private fun ensureCollector() {
        if (collectorJob != null) return
        collectorJob = scope.launch {
            WatchDataRelay.flow.collect { batch ->
                for (snap in batch.snapshots) {
                    for ((trigger, detector) in detectors) {
                        val fired = when (detector) {
                            is RapidMovementDetector -> detector.onSnapshot(snap)
                            is RepeatingMovementDetector -> detector.onSnapshot(snap)
                            is HeartRateDetector -> detector.onSnapshot(snap)
                            else -> false
                        }
                        if (fired) {
                            TriggerBus.emit(
                                TriggerEvent(
                                    triggerId = trigger.id,
                                    ts = snap.timestamp,
                                    source = "watch",
                                    details = trigger.name
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
