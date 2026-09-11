package com.BWPStudio.JITAIWizard.datalayer

import android.content.Context
import android.util.Log
import com.BWPStudio.JITAIWizard.experiment.EngineStatus
import com.BWPStudio.JITAIWizard.experiment.ExperimentEngine
import com.BWPStudio.JITAIWizard.experiment.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Tells the watch whether a run is in progress, so it knows when it may power down.
 *
 * Two jobs, because the watch treats the answer as perishable:
 *
 * - Every change to [SessionState.active] is pushed immediately, so starting a run wakes the
 *   watch to full collection before the first event fires.
 * - While a run is on, the same message repeats every [HEARTBEAT_MS]. The watch expires the
 *   claim if it stops hearing it, which is what stops a phone crash, a force quit or a walk out
 *   of Bluetooth range from leaving the watch collecting at full rate until its battery is flat.
 *   That failure is precisely the one this whole mechanism exists to prevent, so it is not worth
 *   trusting a single fire-and-forget message to avoid it.
 *
 * Also mirrors [ExperimentEngine]'s status into [SessionState], since the engine is one of the
 * two ways a run starts.
 */
class WatchPowerSync(
    context: Context,
    private val engine: ExperimentEngine,
) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sender by lazy { WearMessageSender(appContext) }

    companion object {
        private const val TAG = "WatchPowerSync"

        /** Refresh cadence while a run is on. Well inside the watch's three minute expiry. */
        private const val HEARTBEAT_MS = 60_000L
    }

    fun start() {
        scope.launch {
            engine.state
                .map { it.status == EngineStatus.RUNNING || it.status == EngineStatus.PAUSED }
                .distinctUntilChanged()
                .collect { SessionState.setEngineRun(it) }
        }

        scope.launch {
            SessionState.active.collect { active ->
                Log.i(TAG, "Pushing session state to watch: active=$active")
                sender.sendSessionState(active)
            }
        }

        scope.launch {
            while (true) {
                delay(HEARTBEAT_MS)
                if (SessionState.active.value) sender.sendSessionState(true)
            }
        }
    }

    /** Re-states the current value, for app start and watch reconnect. */
    fun pushCurrent(reason: String) {
        Log.i(TAG, "Pushing session state to watch ($reason): active=${SessionState.active.value}")
        sender.sendSessionState(SessionState.active.value)
    }
}
