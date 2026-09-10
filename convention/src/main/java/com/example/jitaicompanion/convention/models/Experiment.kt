package com.example.jitaicompanion.convention.models

import kotlinx.serialization.Serializable

@Serializable
data class ExperimentEvent(
    val id: String,
    val duration: Float = 0f,
    val type: String = "",
    val intervention: Intervention? = null,
    val gameType: GameType? = null,
    val kind: EventKind = EventKind.Timed
)

@Serializable
data class Experiment(
    val id: String,
    val name: String,
    val version: Long = 1L,
    val updatedAt: Long = System.currentTimeMillis(),
    val notes: String = "",
    val events: List<ExperimentEvent> = emptyList(),
    val triggers: List<Trigger> = emptyList(),
    /**
     * Microgame difficulty for this schedule.
     *
     * Loading a schedule applies these to the watch, so a condition's difficulty travels with
     * the condition instead of being something a researcher has to remember to set by hand on
     * each participant's wrist. Defaulted rather than nullable: an experiment file written
     * before this field existed lands on the documented defaults, which is a known state, where
     * "leave whatever the last participant had" would not be.
     */
    val gameSettings: MicrogameSettings = MicrogameSettings()
)

@Serializable
data class ExperimentEnvelope(
    val etag: String,
    val experiment: Experiment
)

@Serializable
data class ExperimentPutRequest(
    val ifMatch: String? = null,
    val experiment: Experiment
)
