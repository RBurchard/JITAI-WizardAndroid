package com.example.jitaicompanion.convention.models

import kotlinx.serialization.Serializable

@Serializable
data class SessionEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String,
    val details: String = "",
    val reactionTimeMs: Long? = null
)
