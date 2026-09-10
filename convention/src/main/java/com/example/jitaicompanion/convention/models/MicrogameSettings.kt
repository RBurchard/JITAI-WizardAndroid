package com.example.jitaicompanion.convention.models

import kotlinx.serialization.Serializable

/**
 * What a lock-picking mistake costs, in ascending severity.
 *
 * Kept separate from the difficulty scale because severity and sensitivity are independent
 * knobs: a participant with a tremor may need a forgiving *threshold* but still benefit from a
 * real consequence, while a study arm testing frustration may want the opposite.
 */
@Serializable
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
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DEFAULT
    }
}

/**
 * How the watch's microgames are tuned for one participant.
 *
 * This lives in `convention` rather than in either app because it has to be the *same* value in
 * four places at once: the watch that plays the games, the phone screen a researcher edits it
 * on, the experiment file that pins it for a condition, and the exported log that says what the
 * participant actually faced. Three of those are on the other side of a serialization boundary
 * from the watch, so a shared serializable type is the only way they cannot drift.
 *
 * It is carried on [Experiment], so loading a schedule sets the difficulty for that condition in
 * the same action that loads the events. Every field has a default, so an experiment file
 * written before this existed still parses, and lands on the documented defaults rather than on
 * whatever the last participant happened to leave on the watch.
 */
@Serializable
data class MicrogameSettings(
    /** Simon Says show-phase speed. Higher is faster; the input window is never shortened. */
    val simonDifficulty: Int = DEFAULT_SIMON_DIFFICULTY,

    /** How many Simon Says rounds must be cleared to finish the game. */
    val simonRounds: Int = DEFAULT_SIMON_ROUNDS,

    /** Lock picking stand-still sensitivity and corridor length. Higher is harder. */
    val lockDifficulty: Int = DEFAULT_LOCK_DIFFICULTY,

    /** What a lock-picking mistake costs. */
    val lockPenalty: LockPenalty = LockPenalty.STUN,
) {
    /**
     * The same values with every field forced into range.
     *
     * Applied on the way in from anywhere untrusted (a hand-edited experiment file, an older
     * Control Station, a future field this build does not know about), so a bad number degrades
     * to a playable game instead of, say, a Simon sequence that can never be completed.
     */
    fun sanitized(): MicrogameSettings = copy(
        simonDifficulty = simonDifficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY),
        simonRounds = simonRounds.coerceIn(MIN_ROUNDS, MAX_ROUNDS),
        lockDifficulty = lockDifficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY),
    )

    /** Flat key/value view for logs and CSV export, in a stable column order. */
    fun toLogFields(): List<Pair<String, String>> = listOf(
        "simon_difficulty" to simonDifficulty.toString(),
        "simon_rounds" to simonRounds.toString(),
        "lock_difficulty" to lockDifficulty.toString(),
        "lock_penalty" to lockPenalty.name,
    )

    /** One-line summary for a status row or a log detail column. */
    fun summary(): String = toLogFields().joinToString(", ") { (k, v) -> "$k=$v" }

    companion object {
        /** Difficulty scale shared by both games, so one slider style covers them. */
        const val MIN_DIFFICULTY = 1
        const val MAX_DIFFICULTY = 5

        const val MIN_ROUNDS = 2
        const val MAX_ROUNDS = 8

        const val DEFAULT_SIMON_DIFFICULTY = 2
        const val DEFAULT_SIMON_ROUNDS = 4
        const val DEFAULT_LOCK_DIFFICULTY = 2
    }
}
