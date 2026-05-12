package com.BWPStudio.JITAIWizard.experiment

import com.example.jitaicompanion.convention.models.GameType
import com.example.jitaicompanion.convention.models.Intervention
import kotlinx.serialization.Serializable

@Serializable
data class Event(
    val id: String,
    val duration: Float,
    val type: String,
    var intervention: Intervention? = null,
    val gameType: GameType? = null
) {
    fun toCsv(newLine: Boolean = false) = "$id,$duration,$type,$intervention" + if (newLine) "\n" else ""
}
