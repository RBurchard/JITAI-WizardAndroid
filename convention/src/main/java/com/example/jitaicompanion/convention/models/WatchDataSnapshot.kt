package com.example.jitaicompanion.convention.models

import kotlinx.serialization.Serializable

@Serializable
data class WatchDataSnapshot(
    val timestamp: Long,
    val heartRate: Float = 0f,
    val accelX: Float = 0f,
    val accelY: Float = 0f,
    val accelZ: Float = 0f,
    val stepCount: Int = 0,
    val sessionId: String = ""
)
