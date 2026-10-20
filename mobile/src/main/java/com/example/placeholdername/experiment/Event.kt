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

/**
 * Moves the event with id [fromKey] to the slot currently held by the event with id [toKey].
 * Matches by id, not index: the schedule's LazyColumn has a header item ahead of the events, so
 * the indices the reorder library reports run ahead of positions in this list. Returns the list
 * unchanged if either id is not in it.
 */
fun List<Event>.movedByKey(fromKey: Any?, toKey: Any?): List<Event> {
    val from = indexOfFirst { it.id == fromKey }
    val to = indexOfFirst { it.id == toKey }
    if (from < 0 || to < 0 || from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}
