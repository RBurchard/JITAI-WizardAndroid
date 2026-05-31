package com.example.jitaicompanion.convention.models

import kotlinx.serialization.Serializable

@Serializable
data class WsEnvelope(
    val type: String,
    val payloadJson: String
) {
    companion object {
        const val TYPE_STATE = "state"
        const val TYPE_LOG = "log"
        const val TYPE_TRIGGER = "trigger"
        const val TYPE_EVENT_ADVANCE = "event-advance"
        const val TYPE_RUN_STARTED = "run-started"
        const val TYPE_RUN_STOPPED = "run-stopped"
        const val TYPE_INTERVENTION_STARTED = "intervention-started"
        const val TYPE_INTERVENTION_FINISHED = "intervention-finished"
    }
}

@Serializable
data class EngineStateSnapshot(
    val status: String,
    val mode: String,
    val currentEventIndex: Int,
    val currentEventId: String? = null,
    val currentEventKind: String? = null,
    val elapsedSecInEvent: Float = 0f,
    val waitingForActionId: String? = null,
    val waitingForTriggerId: String? = null,
    val randomWaitRemainingSec: Float? = null,
    val runId: String? = null,
    val experimentId: String? = null,
    val experimentName: String? = null
)
