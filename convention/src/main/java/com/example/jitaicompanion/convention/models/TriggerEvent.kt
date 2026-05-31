package com.example.jitaicompanion.convention.models

import kotlinx.serialization.Serializable

@Serializable
data class TriggerEvent(
    val triggerId: String,
    val ts: Long,
    val source: String,
    val details: String = ""
)
