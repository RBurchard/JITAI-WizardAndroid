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
    val events: List<ExperimentEvent> = emptyList(),
    val triggers: List<Trigger> = emptyList()
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
