package com.BWPStudio.JITAIWizard.experiment

import com.example.jitaicompanion.convention.models.EventKind
import com.example.jitaicompanion.convention.models.ExperimentEvent
import com.example.jitaicompanion.convention.models.GameType
import com.example.jitaicompanion.convention.models.Intervention
import kotlinx.serialization.Serializable

@Serializable
data class Event(
    val id: String,
    val duration: Float,
    val type: String,
    var intervention: Intervention? = null,
    val gameType: GameType? = null,
    val kind: EventKind = EventKind.Timed
) {
    fun toCsv(newLine: Boolean = false) = "$id,$duration,$type,$intervention" + if (newLine) "\n" else ""

    fun toShared(): ExperimentEvent = ExperimentEvent(
        id = id,
        duration = duration,
        type = type,
        intervention = intervention,
        gameType = gameType,
        kind = kind
    )

    companion object {
        fun fromShared(s: ExperimentEvent): Event = Event(
            id = s.id,
            duration = s.duration,
            type = s.type,
            intervention = s.intervention,
            gameType = s.gameType,
            kind = s.kind
        )
    }
}
