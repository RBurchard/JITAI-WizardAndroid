package com.BWPStudio.JITAIWizard.experiment

import android.content.Context
import com.example.jitaicompanion.convention.models.Experiment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class ExperimentStore(context: Context) {
    private val file = File(context.filesDir, "active_experiment.json")
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; prettyPrint = false }

    private val _active = MutableStateFlow(loadInitial())
    val active: StateFlow<Experiment> = _active.asStateFlow()

    private var _etag: String = computeEtag(json.encodeToString(_active.value))
    val etag: String get() = _etag

    var lastSyncedEtag: String? = null
        private set

    private fun loadInitial(): Experiment {
        return try {
            if (file.exists()) json.decodeFromString(file.readText()) else freshExperiment()
        } catch (e: Exception) {
            freshExperiment()
        }
    }

    private fun freshExperiment() = Experiment(
        id = UUID.randomUUID().toString(),
        name = "Untitled",
        version = 1L,
        updatedAt = System.currentTimeMillis(),
        events = emptyList(),
        triggers = emptyList()
    )

    @Synchronized
    fun replace(experiment: Experiment, fromSync: Boolean = false): String {
        val bumped = experiment.copy(
            version = experiment.version + 1,
            updatedAt = System.currentTimeMillis()
        )
        val bytes = json.encodeToString(bumped)
        file.writeText(bytes)
        _active.value = bumped
        _etag = computeEtag(bytes)
        if (fromSync) lastSyncedEtag = _etag
        return _etag
    }

    @Synchronized
    fun markSynced() { lastSyncedEtag = _etag }

    private fun computeEtag(payload: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(payload.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.substring(0, 16)
    }
}
