package com.example.jitaicompanion.convention.models

import kotlinx.serialization.Serializable

@Serializable
data class ParticipantInfo(
    val id: String,
    val sessionId: String,
    val label: String,
    val deviceIp: String = ""
)
