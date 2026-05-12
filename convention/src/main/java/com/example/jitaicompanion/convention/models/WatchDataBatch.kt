package com.example.jitaicompanion.convention.models

import kotlinx.serialization.Serializable

@Serializable
data class WatchDataBatch(
    val sessionId: String,
    val snapshots: List<WatchDataSnapshot>
)
