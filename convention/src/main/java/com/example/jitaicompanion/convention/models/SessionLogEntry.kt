package com.example.jitaicompanion.convention.models

import kotlinx.serialization.Serializable

@Serializable
data class SessionLogEntry(
    val id: Long,
    val runId: String? = null,
    val ts: Long,
    val source: String,
    val level: String = "INFO",
    val kind: String,
    val payloadJson: String = ""
)

@Serializable
data class SessionLogPage(
    val entries: List<SessionLogEntry>,
    val nextSince: Long
)
