package com.example.jitaicompanion.ui.games

import android.content.Context
import com.example.jitaicompanion.convention.models.LockPenalty
import com.example.jitaicompanion.convention.models.MicrogameSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The watch's copy of [MicrogameSettings], and the difficulty curves derived from it.
 *
 * Storage is watch-local SharedPreferences, but the *value* is no longer watch-local: the phone
 * pushes it over the Data Layer (see `WearMessageListener`), and loading an experiment schedule
 * pushes that schedule's own settings. A researcher can still adjust it on the wrist, which is
 * what the watch Settings screen edits; the next push from the phone wins, which is what keeps a
 * participant's session consistent with the schedule loaded for them.
 *
 * The curves below (flash timings, motion limits, run length) live here rather than in the
 * shared model because they are presentation detail this watch build owns. Only the 1..5 dial
 * positions have to mean the same thing on both devices.
 */
object GameSettings {

    private const val PREFS = "microgame_settings"
    private const val KEY_SIMON_DIFFICULTY = "simon_difficulty"
    private const val KEY_SIMON_ROUNDS = "simon_rounds"
    private const val KEY_LOCK_DIFFICULTY = "lock_difficulty"
    private const val KEY_LOCK_PENALTY = "lock_penalty"

    const val MIN_DIFFICULTY = MicrogameSettings.MIN_DIFFICULTY
    const val MAX_DIFFICULTY = MicrogameSettings.MAX_DIFFICULTY
    const val MIN_ROUNDS = MicrogameSettings.MIN_ROUNDS
    const val MAX_ROUNDS = MicrogameSettings.MAX_ROUNDS

    private val _current = MutableStateFlow(MicrogameSettings())

    /**
     * Live settings, so a Settings screen left open while the phone pushes an update redraws
     * instead of showing a value the games are no longer using.
     */
    val current: StateFlow<MicrogameSettings> = _current.asStateFlow()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Reads the stored settings, refreshing [current] as a side effect. */
    fun load(context: Context): MicrogameSettings {
        val p = prefs(context)
        val loaded = MicrogameSettings(
            simonDifficulty = p.getInt(KEY_SIMON_DIFFICULTY, MicrogameSettings.DEFAULT_SIMON_DIFFICULTY),
            simonRounds = p.getInt(KEY_SIMON_ROUNDS, MicrogameSettings.DEFAULT_SIMON_ROUNDS),
            lockDifficulty = p.getInt(KEY_LOCK_DIFFICULTY, MicrogameSettings.DEFAULT_LOCK_DIFFICULTY),
            lockPenalty = LockPenalty.fromName(p.getString(KEY_LOCK_PENALTY, null)),
        ).sanitized()
        _current.value = loaded
        return loaded
    }

    /** Writes [settings] (clamped) and publishes them to [current]. */
    fun save(context: Context, settings: MicrogameSettings): MicrogameSettings {
        val clean = settings.sanitized()
        prefs(context).edit()
            .putInt(KEY_SIMON_DIFFICULTY, clean.simonDifficulty)
            .putInt(KEY_SIMON_ROUNDS, clean.simonRounds)
            .putInt(KEY_LOCK_DIFFICULTY, clean.lockDifficulty)
            .putString(KEY_LOCK_PENALTY, clean.lockPenalty.name)
            .apply()
        _current.value = clean
        return clean
    }

    /** Applies one edit on top of whatever is stored. */
    fun update(context: Context, transform: (MicrogameSettings) -> MicrogameSettings): MicrogameSettings =
        save(context, transform(load(context)))

    // ── Simon Says curves ────────────────────────────────────────────────────

    /**
     * Timing for one Simon Says flash at [difficulty].
     *
     * Difficulty only compresses the show phase — it never shortens the *input* window — so a
     * higher setting tests recall speed rather than punishing slow tapping, which would make
     * the game harder for exactly the motor-impaired users the rework is meant to help.
     */
    fun simonFlashMillis(difficulty: Int): Long = when (difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)) {
        1 -> 800L
        2 -> 620L
        3 -> 480L
        4 -> 380L
        else -> 300L
    }

    /** Dark gap between two flashes at [difficulty]. Never 0 — a repeated colour must read as two taps. */
    fun simonGapMillis(difficulty: Int): Long = when (difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)) {
        1 -> 420L
        2 -> 320L
        3 -> 240L
        4 -> 180L
        else -> 140L
    }

    // ── Lock picking, stage 1: how still is still enough ─────────────────────

    /**
     * Fraction of the steadiness meter above which the alarm trips, at [difficulty].
     *
     * Higher difficulty means a *lower* limit, i.e. less movement tolerated. The meter itself
     * always spans the same acceleration range, so the marker visibly moves left as difficulty
     * rises rather than the whole scale silently rescaling under the player.
     */
    fun lockMotionLimit(difficulty: Int): Float = when (difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)) {
        1 -> 0.82f
        2 -> 0.62f
        3 -> 0.48f
        4 -> 0.36f
        else -> 0.26f
    }

    /** Seconds of steady holding needed to clear stage 1, at [difficulty]. */
    fun lockCreepSeconds(difficulty: Int): Float = when (difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)) {
        1 -> 4f
        2 -> 6f
        3 -> 7f
        4 -> 8.5f
        else -> 10f
    }

    // ── Lock picking, stage 2: how long the corridor is ──────────────────────

    /**
     * Length of the stage-2 corridor in screen-widths, at [difficulty].
     *
     * Above 1 the corridor no longer fits on screen and the view scrolls to follow the thief,
     * which is what lets a harder run hold more beams without cramming them together.
     */
    fun lockRunScreens(difficulty: Int): Float = when (difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)) {
        1 -> 1.0f
        2 -> 1.6f
        3 -> 2.2f
        4 -> 2.8f
        else -> 3.4f
    }

    /** Number of guillotine beams on the stage-2 run, at [difficulty]. */
    fun lockBeamCount(difficulty: Int): Int =
        difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY) + 1
}
