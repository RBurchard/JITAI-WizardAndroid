package com.example.jitaicompanion.ui.games

import android.content.Context

/**
 * Player-tunable microgame settings, persisted on the watch.
 *
 * These are deliberately watch-local (SharedPreferences, not synced over the Data Layer):
 * they change how the *presentation* of a game feels for one wearer — how fast Simon Says
 * flashes and how many rounds it runs — not what the study measures. A researcher setting a
 * participant up can dial them in on the wrist in a few seconds, and the participant can then
 * sit and practise while the phone side is still being configured (see
 * [SimonSaysGameActivity]'s practice mode).
 */
object GameSettings {

    private const val PREFS = "microgame_settings"
    private const val KEY_SIMON_DIFFICULTY = "simon_difficulty"
    private const val KEY_SIMON_ROUNDS = "simon_rounds"
    private const val KEY_LOCK_DIFFICULTY = "lock_difficulty"
    private const val KEY_LOCK_PENALTY = "lock_penalty"

    /** Difficulty steps offered by the settings slider. */
    const val MIN_DIFFICULTY = 1
    const val MAX_DIFFICULTY = 5
    const val DEFAULT_DIFFICULTY = 2

    /** Round-count steps offered by the settings slider. */
    const val MIN_ROUNDS = 2
    const val MAX_ROUNDS = 8
    const val DEFAULT_ROUNDS = 4

    /** Lock-picking difficulty steps. Shares the 1..5 range with Simon for a consistent UI. */
    const val DEFAULT_LOCK_DIFFICULTY = 2

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getSimonDifficulty(context: Context): Int =
        prefs(context).getInt(KEY_SIMON_DIFFICULTY, DEFAULT_DIFFICULTY)
            .coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    fun setSimonDifficulty(context: Context, value: Int) {
        prefs(context).edit()
            .putInt(KEY_SIMON_DIFFICULTY, value.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY))
            .apply()
    }

    fun getSimonRounds(context: Context): Int =
        prefs(context).getInt(KEY_SIMON_ROUNDS, DEFAULT_ROUNDS)
            .coerceIn(MIN_ROUNDS, MAX_ROUNDS)

    fun setSimonRounds(context: Context, value: Int) {
        prefs(context).edit()
            .putInt(KEY_SIMON_ROUNDS, value.coerceIn(MIN_ROUNDS, MAX_ROUNDS))
            .apply()
    }

    fun getLockDifficulty(context: Context): Int =
        prefs(context).getInt(KEY_LOCK_DIFFICULTY, DEFAULT_LOCK_DIFFICULTY)
            .coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    fun setLockDifficulty(context: Context, value: Int) {
        prefs(context).edit()
            .putInt(KEY_LOCK_DIFFICULTY, value.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY))
            .apply()
    }

    fun getLockPenalty(context: Context): LockPenalty =
        LockPenalty.fromName(prefs(context).getString(KEY_LOCK_PENALTY, null))

    fun setLockPenalty(context: Context, value: LockPenalty) {
        prefs(context).edit().putString(KEY_LOCK_PENALTY, value.name).apply()
    }

    // ── Lock picking, stage 1: how still is still enough ──────────────────────

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

    // ── Lock picking, stage 2: how long the corridor is ───────────────────────

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
}

/**
 * What a lock-picking mistake costs, in ascending severity.
 *
 * Split out from the difficulty slider because severity and sensitivity are independent knobs:
 * a participant with a tremor may need a forgiving *threshold* but still benefit from a real
 * consequence, while a study arm testing frustration may want the opposite.
 */
enum class LockPenalty {
    /** Progress freezes for a moment. Nothing is lost. */
    STUN,

    /** Progress freezes and slides back a little. */
    PUSHBACK,

    /** The stage starts over. */
    RESET;

    companion object {
        val DEFAULT = STUN

        fun fromName(name: String?): LockPenalty =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
