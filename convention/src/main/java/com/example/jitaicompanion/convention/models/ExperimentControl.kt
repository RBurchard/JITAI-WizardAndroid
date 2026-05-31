package com.example.jitaicompanion.convention.models

import kotlinx.serialization.Serializable

@Serializable
data class ExperimentControl(
    val command: String,
    val runId: String? = null,
    val targetEventIndex: Int? = null
)

@Serializable
data class TriggerFireRequest(
    val triggerId: String,
    val source: String = "manual-pc"
)
