package com.example.jitaicompanion.convention.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("kind")
sealed class TriggerKind {
    @Serializable
    @SerialName("RapidMovement")
    data class RapidMovement(
        val accelThreshold: Float = 2.5f,
        val windowMs: Int = 500
    ) : TriggerKind()

    @Serializable
    @SerialName("RepeatingMovement")
    data class RepeatingMovement(
        val minHz: Float = 1.0f,
        val maxHz: Float = 4.0f,
        val sustainSec: Float = 3.0f
    ) : TriggerKind()

    @Serializable
    @SerialName("HeartRate")
    data class HeartRate(
        val bpm: Int = 110,
        val above: Boolean = true,
        val sustainSec: Float = 3.0f
    ) : TriggerKind()

    @Serializable
    @SerialName("Manual")
    data object Manual : TriggerKind()
}

@Serializable
data class Trigger(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val kind: TriggerKind
)
