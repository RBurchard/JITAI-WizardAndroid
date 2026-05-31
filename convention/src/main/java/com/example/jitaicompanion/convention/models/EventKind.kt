package com.example.jitaicompanion.convention.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("kind")
sealed class EventKind {
    @Serializable
    @SerialName("Timed")
    data object Timed : EventKind()

    @Serializable
    @SerialName("RandomWait")
    data class RandomWait(val minSec: Float, val maxSec: Float) : EventKind()

    @Serializable
    @SerialName("WaitForAction")
    data class WaitForAction(val actionId: String) : EventKind()

    @Serializable
    @SerialName("WaitForTrigger")
    data class WaitForTrigger(val triggerId: String) : EventKind()

    @Serializable
    @SerialName("WaitForPrevious")
    data object WaitForPrevious : EventKind()
}
