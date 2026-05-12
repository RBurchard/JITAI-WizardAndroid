package com.example.jitaicompanion.convention.models

import kotlinx.serialization.Serializable

@Serializable
data class Intervention(
    val id: String,
    val type: String,
    val notification: NotificationType,
    var message: String,
    val durationSeconds: Int,
    val gameType: GameType? = null,
    val phoneTaskType: PhoneTaskType? = null
)
