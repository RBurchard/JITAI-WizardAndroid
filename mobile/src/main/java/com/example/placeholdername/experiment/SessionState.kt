package com.BWPStudio.JITAIWizard.experiment

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether a run is in progress, from the phone's point of view.
 *
 * There are two ways to run this study and they were tracked separately: the Experiment tab's
 * own Start button, which drives a wizard stepping through events by hand, and
 * [ExperimentEngine], which drives the automatic and Control Station modes. Neither knew about
 * the other, which was fine while nothing outside the screen needed the answer.
 *
 * The watch needs the answer. It powers its sensors down when no run is on
 * (`WatchPowerPolicy`), so "is a run on" has to mean the same thing everywhere, including when
 * a wizard starts a manual run from the phone while the engine sits idle. Hence one flag per
 * source and an OR across them, rather than a single boolean that the last writer wins.
 */
object SessionState {

    private const val TAG = "SessionState"

    private var manualRun = false
    private var engineRun = false

    private val _active = MutableStateFlow(false)

    /** True while either the manual run controls or the engine has a run in progress. */
    val active: StateFlow<Boolean> = _active.asStateFlow()

    /** The Experiment tab's Start/Stop button. */
    @Synchronized
    fun setManualRun(running: Boolean) {
        if (manualRun == running) return
        manualRun = running
        recompute("manual")
    }

    /** [ExperimentEngine] status, mapped to running or not by whoever observes it. */
    @Synchronized
    fun setEngineRun(running: Boolean) {
        if (engineRun == running) return
        engineRun = running
        recompute("engine")
    }

    private fun recompute(source: String) {
        val next = manualRun || engineRun
        if (next == _active.value) return
        _active.value = next
        Log.i(TAG, "Session ${if (next) "started" else "ended"} (via $source)")
    }
}
