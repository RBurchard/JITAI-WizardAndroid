package com.example.jitaicompanion.power

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
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
 * Idle needs three conditions, and they are deliberately different in kind:
 *
 * - **No session is running.** The phone says so ([onSessionState]); the watch never guesses.
 *   Guessing from "no intervention for a while" would be wrong on any schedule with a long wait
 *   in it, and the cost of being wrong is a hole in the study data.
 * - **Nobody has touched it for [IDLE_AFTER_MS].** Any screen of the app being open, and any
 *   message from the phone, counts as being touched.
 * - **No wake request is outstanding.** The wizard can ask for full rate for a fixed window
 *   ([onWakeRequested]) while setting up, without starting a run.
 *
 * The session flag carries a deadline rather than a boolean. The phone refreshes it while a run
 * is on, so a phone that crashes or wanders out of Bluetooth range mid-session leaves the watch
 * at full rate for [SESSION_TTL_MS] and no longer. A stale "a session is running" is the one
 * failure mode that reproduces exactly the battery drain this class exists to prevent.
 *
 * The wake window is a deadline too, and the three deadlines are independent: idle waits for
 * all of them. That is what makes "session started inside the wake window" need no special
 * handling. The window expiring changes nothing while the session claim is still live, and the
 * session stopping counts as a touch, so the usual five quiet minutes follow.
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

    /** Bounds on a wake request. Below the idle timeout it would be a no-op; above an hour it is
     *  a forgotten button and the battery problem this file exists to fix. */
    const val WAKE_MIN_MS = IDLE_AFTER_MS
    const val WAKE_MAX_MS = 60 * 60 * 1000L

    /**
     * Upper bound on how long the driver may sleep between [evaluate] calls when nothing is
     * scheduled. Belt and braces: every state change also kicks [ticks], so this only matters
     * if a kick were ever lost, and then it costs one wakeup an hour.
     */
    const val MAX_SLEEP_MS = 60 * 60 * 1000L

    private val _profile = MutableStateFlow(PowerProfile.ACTIVE)
    val profile: StateFlow<PowerProfile> = _profile.asStateFlow()

    /**
     * Fires whenever a deadline moves, so whoever drives [evaluate] on a timer can recompute
     * its sleep instead of polling. Conflated: two kicks before the driver wakes are one kick.
     */
    private val _ticks = Channel<Unit>(Channel.CONFLATED)
    val ticks: ReceiveChannel<Unit> get() = _ticks

    /** Activities of this app currently resumed. Counted, not flagged, so a screen change does
     *  not read as the app being closed. */
    private var foregroundScreens = 0
    private var lastTouchedAt = SystemClock.elapsedRealtime()
    private var sessionActiveUntil = 0L
    private var wakeUntil = 0L

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

    /**
     * Hold the active profile for [durationMs], clamped to [[WAKE_MIN_MS], [WAKE_MAX_MS]].
     *
     * A second request replaces the first rather than extending it, so pressing the button
     * twice gives one window from the second press, not two windows end to end.
     */
    @Synchronized
    fun onWakeRequested(durationMs: Long) {
        val clamped = durationMs.coerceIn(WAKE_MIN_MS, WAKE_MAX_MS)
        wakeUntil = SystemClock.elapsedRealtime() + clamped
        Log.i(TAG, "Wake requested for ${clamped / 1000}s")
        touch("wake_requested")
    }

    /** Re-checks the clock. Called on a timer, since idling is triggered by nothing happening. */
    @Synchronized
    fun evaluate() {
        val now = SystemClock.elapsedRealtime()
        val sessionRunning = now < sessionActiveUntil
        val awake = now < wakeUntil
        val quietFor = now - lastTouchedAt
        val idle = !sessionRunning && !awake && foregroundScreens == 0 && quietFor >= IDLE_AFTER_MS
        val next = if (idle) PowerProfile.IDLE else PowerProfile.ACTIVE
        if (next != _profile.value) {
            Log.i(
                TAG,
                "Power profile ${_profile.value} -> $next " +
                    "(session=$sessionRunning, awake=$awake, screens=$foregroundScreens, " +
                    "quiet=${quietFor / 1000}s)"
            )
            _profile.value = next
        }
    }

    /**
     * How long until the profile could next change on its own, for a driver to sleep.
     *
     * Only the drop into idle happens by the clock; everything else is an event, and events
     * kick [ticks]. So: already idle, or a screen open, means nothing is scheduled and the
     * answer is [MAX_SLEEP_MS]. Otherwise it is the latest of the three deadlines. Never less
     * than a second, so a deadline that has just passed cannot spin the driver.
     */
    @Synchronized
    fun msUntilNextTransition(): Long {
        if (_profile.value == PowerProfile.IDLE || foregroundScreens > 0) return MAX_SLEEP_MS
        val now = SystemClock.elapsedRealtime()
        val idleAt = maxOf(lastTouchedAt + IDLE_AFTER_MS, sessionActiveUntil, wakeUntil)
        return (idleAt - now).coerceIn(1_000L, MAX_SLEEP_MS)
    }

    private fun touch(reason: String) {
        lastTouchedAt = SystemClock.elapsedRealtime()
        if (_profile.value == PowerProfile.IDLE) Log.i(TAG, "Waking from idle: $reason")
        evaluate()
        _ticks.trySend(Unit)
    }
}
