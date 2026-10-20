package com.BWPStudio.JITAIWizard.experiment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class EventReorderTest {
    private fun events(vararg ids: String) = ids.map { Event(id = it, duration = 10f, type = "t-$it") }

    private fun List<Event>.ids() = map { it.id }

    @Test
    fun movesDownToTargetSlot() {
        val result = events("a", "b", "c", "d").movedByKey("a", "c")
        assertEquals(listOf("b", "c", "a", "d"), result.ids())
    }

    @Test
    fun movesUpToTargetSlot() {
        val result = events("a", "b", "c", "d").movedByKey("d", "b")
        assertEquals(listOf("a", "d", "b", "c"), result.ids())
    }

    @Test
    fun movesLastEventWithoutIndexingPastTheEnd() {
        // The old index-based move threw here: the last event's lazy index was list.size.
        val result = events("a", "b", "c").movedByKey("c", "a")
        assertEquals(listOf("c", "a", "b"), result.ids())
    }

    @Test
    fun movesTheDraggedEventNotItsNeighbour() {
        // Lazy-list indices are one ahead of list positions (header item at 0). Dragging "b"
        // over "a" is lazy 2 -> 1; applied as list indices it would move "c" instead.
        val result = events("a", "b", "c").movedByKey("b", "a")
        assertEquals(listOf("b", "a", "c"), result.ids())
    }

    @Test
    fun unknownKeysLeaveTheListAlone() {
        val list = events("a", "b")
        assertSame(list, list.movedByKey("__triggers__", "a"))
        assertSame(list, list.movedByKey("a", "gone"))
    }

    @Test
    fun movingOntoItselfLeavesTheListAlone() {
        val list = events("a", "b")
        assertSame(list, list.movedByKey("a", "a"))
    }
}
