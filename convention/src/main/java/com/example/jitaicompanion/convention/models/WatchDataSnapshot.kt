package com.example.jitaicompanion.convention.models

import kotlinx.serialization.Serializable

@Serializable
data class WatchDataSnapshot(
    val timestamp: Long,
    val heartRate: Float = 0f,
    val accelX: Float = 0f,
    val accelY: Float = 0f,
    val accelZ: Float = 0f,
    val gyroX: Float = 0f,
    val gyroY: Float = 0f,
    val gyroZ: Float = 0f,
    val rotationX: Float = 0f,
    val rotationY: Float = 0f,
    val rotationZ: Float = 0f,
    val rotationW: Float = 0f,
    val barometer: Float = 0f,
    val light: Float = 0f,
    val stepCount: Int = 0,
    val sessionId: String = ""
)
