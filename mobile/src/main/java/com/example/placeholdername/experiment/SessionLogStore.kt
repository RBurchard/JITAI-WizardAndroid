package com.BWPStudio.JITAIWizard.experiment

import com.example.jitaicompanion.convention.models.SessionLogEntry
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicLong

object SessionLogStore {
    private const val RING_CAPACITY = 2000
    private val ring = ArrayDeque<SessionLogEntry>(RING_CAPACITY)
    private val idGen = AtomicLong(0L)
    private val lock = Any()
    private val _tail = MutableSharedFlow<SessionLogEntry>(replay = 0, extraBufferCapacity = 256)
    val tail = _tail.asSharedFlow()

    var currentRunId: String? = null

    suspend fun append(
        source: String,
        kind: String,
        payloadJson: String = "",
        level: String = "INFO",
        ts: Long = System.currentTimeMillis()
    ): SessionLogEntry {
        val entry = SessionLogEntry(
            id = idGen.incrementAndGet(),
            runId = currentRunId,
            ts = ts,
            source = source,
            level = level,
            kind = kind,
            payloadJson = payloadJson
        )
        synchronized(lock) {
            if (ring.size >= RING_CAPACITY) ring.removeFirst()
            ring.addLast(entry)
        }
        _tail.emit(entry)
        return entry
    }

    fun query(since: Long, limit: Int): List<SessionLogEntry> {
        synchronized(lock) {
            return ring.asSequence().filter { it.id > since }.take(limit).toList()
        }
    }

    fun latestId(): Long = idGen.get()
}
