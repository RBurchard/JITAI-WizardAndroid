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
    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "active_experiment.json")
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; prettyPrint = true; encodeDefaults = true }

    // Folder holding named, persistent experiment schedules. "DefaultExperiment" is the
    // rolling auto-saved copy of whatever schedule was last active (incl. ControlStation
    // syncs), so the phone always has the last schedule even with no ControlStation around.
    private val schedulesDir = File(appContext.filesDir, "experiment_schedules").apply { mkdirs() }

    companion object {
        const val DEFAULT_SCHEDULE = "DefaultExperiment"
    }

    private val _active = MutableStateFlow(loadInitial())
    val active: StateFlow<Experiment> = _active.asStateFlow()

    private var _etag: String = computeEtag(json.encodeToString(_active.value))
    val etag: String get() = _etag

    var lastSyncedEtag: String? = null
        private set

    private fun loadInitial(): Experiment {
        return try {
            when {
                file.exists() -> json.decodeFromString(file.readText())
                // No active file yet — fall back to the persisted default schedule.
                scheduleFile(DEFAULT_SCHEDULE).exists() ->
                    json.decodeFromString(scheduleFile(DEFAULT_SCHEDULE).readText())
                // Fresh install — seed from the bundled default asset.
                else -> defaultExperiment()
            }
        } catch (e: Exception) {
            defaultExperiment()
        }
    }

    // Fresh installs start from the bundled default (kept identical to the Control Station's
    // Assets/default_experiment.json). Falls back to an empty experiment if the asset is missing.
    private fun defaultExperiment(): Experiment = try {
        appContext.assets.open("default_experiment.json").bufferedReader().use { r ->
            json.decodeFromString<Experiment>(r.readText())
        }
    } catch (e: Exception) {
        Experiment(
            id = UUID.randomUUID().toString(),
            name = "Untitled",
            version = 1L,
            updatedAt = System.currentTimeMillis(),
            events = emptyList(),
            triggers = emptyList()
        )
    }

    @Synchronized
    fun replace(experiment: Experiment, fromSync: Boolean = false): String {
        val bumped = experiment.copy(
            version = experiment.version + 1,
            updatedAt = System.currentTimeMillis()
        )
        val bytes = json.encodeToString(bumped)
        file.writeText(bytes)
        // Always keep the rolling "DefaultExperiment" schedule in sync with whatever is
        // active, so the last-used schedule is recoverable even without a ControlStation.
        runCatching { scheduleFile(DEFAULT_SCHEDULE).writeText(bytes) }
        _active.value = bumped
        _etag = computeEtag(bytes)
        if (fromSync) lastSyncedEtag = _etag
        return _etag
    }

    @Synchronized
    fun markSynced() { lastSyncedEtag = _etag }

    // ── Named schedule slots ────────────────────────────────────────────────
    // Lets researchers keep multiple saved schedules on the phone and load any of them.

    private fun sanitize(name: String): String =
        name.trim().ifBlank { DEFAULT_SCHEDULE }
            .replace(Regex("[^A-Za-z0-9 _-]"), "_")
            .take(64)

    private fun scheduleFile(name: String) = File(schedulesDir, "${sanitize(name)}.json")

    /** Slot names of every saved schedule, alphabetically (DefaultExperiment first). */
    fun listSchedules(): List<String> =
        (schedulesDir.listFiles { f -> f.isFile && f.extension == "json" }
            ?.map { it.nameWithoutExtension } ?: emptyList())
            .sortedWith(compareByDescending<String> { it == DEFAULT_SCHEDULE }.thenBy { it.lowercase() })

    /** Persists [experiment] under the given slot [name]. Does not change the active schedule. */
    fun saveSchedule(name: String, experiment: Experiment) {
        runCatching { scheduleFile(name).writeText(json.encodeToString(experiment)) }
    }

    /** Loads a saved schedule, or null if the slot is missing/corrupt. */
    fun loadSchedule(name: String): Experiment? =
        runCatching { json.decodeFromString<Experiment>(scheduleFile(name).readText()) }.getOrNull()

    /** Deletes a saved schedule slot (the DefaultExperiment slot is protected). */
    fun deleteSchedule(name: String) {
        if (sanitize(name) == DEFAULT_SCHEDULE) return
        runCatching { scheduleFile(name).delete() }
    }

    private fun computeEtag(payload: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(payload.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.substring(0, 16)
    }
}
