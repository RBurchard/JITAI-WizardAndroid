package com.example.jitaicompanion.power

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How hard the watch is working. See [WatchPowerPolicy]. */
enum class PowerProfile { ACTIVE, IDLE }

/**
 * Decides whether the watch should be collecting at full rate or ticking over.
 *
 * The app used to run one way: three motion sensors at 50 Hz, a continuous heart rate
 * measurement, and a Data Layer message every 100 ms, from the moment it was opened until it was
 * force quit. That is the right behaviour during a session and ruinous outside one, which is why
 * a watch left on after a run was flat within the hour.
 *
 * Idle needs two conditions, and they are deliberately different in kind:
 *
 * - **No session is running.** The phone says so ([onSessionState]); the watch never guesses.
 *   Guessing from "no intervention for a while" would be wrong on any schedule with a long wait
 *   in it, and the cost of being wrong is a hole in the study data.
 * - **Nobody has touched it for [IDLE_AFTER_MS].** Any screen of the app being open, and any
 *   message from the phone, counts as being touched.
 *
 * The session flag carries a deadline rather than a boolean. The phone refreshes it while a run
 * is on, so a phone that crashes or wanders out of Bluetooth range mid-session leaves the watch
 * at full rate for [SESSION_TTL_MS] and no longer. A stale "a session is running" is the one
 * failure mode that reproduces exactly the battery drain this class exists to prevent.
 *
 * Everything here is measured on [SystemClock.elapsedRealtime], so a clock adjustment cannot
 * push the watch into or out of idle.
 */
object WatchPowerPolicy {

    private const val TAG = "WatchPowerPolicy"

    /** Quiet time before the watch drops to the idle profile. */
    const val IDLE_AFTER_MS = 5 * 60 * 1000L

    /** How long a "session is running" claim stays good without a refresh from the phone. */
    const val SESSION_TTL_MS = 3 * 60 * 1000L

    private val _profile = MutableStateFlow(PowerProfile.ACTIVE)
    val profile: StateFlow<PowerProfile> = _profile.asStateFlow()

    /** Activities of this app currently resumed. Counted, not flagged, so a screen change does
     *  not read as the app being closed. */
    private var foregroundScreens = 0
    private var lastTouchedAt = SystemClock.elapsedRealtime()
    private var sessionActiveUntil = 0L

    @Synchronized
    fun onActivityResumed() {
        foregroundScreens++
        touch("screen_opened")
    }

    @Synchronized
    fun onActivityPaused() {
        foregroundScreens = (foregroundScreens - 1).coerceAtLeast(0)
        // The quiet timer starts from here, not from when the screen opened, so putting the
        // watch down starts a fresh five minutes.
        touch("screen_closed")
    }

    /** Any message from the phone. Someone is working with this watch. */
    @Synchronized
    fun onPhoneContact() = touch("phone_contact")

    /**
     * The phone's view of whether a run is in progress.
     *
     * Also counts as contact: the wizard pressing stop is a person at the desk, and the five
     * minutes after a run are exactly when someone is most likely to pick the watch up.
     */
    @Synchronized
    fun onSessionState(active: Boolean) {
        sessionActiveUntil = if (active) SystemClock.elapsedRealtime() + SESSION_TTL_MS else 0L
        touch(if (active) "session_running" else "session_stopped")
    }

    /** Re-checks the clock. Called on a timer, since idling is triggered by nothing happening. */
    @Synchronized
    fun evaluate() {
        val now = SystemClock.elapsedRealtime()
        val sessionRunning = now < sessionActiveUntil
        val quietFor = now - lastTouchedAt
        val idle = !sessionRunning && foregroundScreens == 0 && quietFor >= IDLE_AFTER_MS
        val next = if (idle) PowerProfile.IDLE else PowerProfile.ACTIVE
        if (next != _profile.value) {
            Log.i(
                TAG,
                "Power profile ${_profile.value} -> $next " +
                    "(session=$sessionRunning, screens=$foregroundScreens, quiet=${quietFor / 1000}s)"
            )
            _profile.value = next
        }
    }

    private fun touch(reason: String) {
        lastTouchedAt = SystemClock.elapsedRealtime()
        if (_profile.value == PowerProfile.IDLE) Log.i(TAG, "Waking from idle: $reason")
        evaluate()
    }
}
