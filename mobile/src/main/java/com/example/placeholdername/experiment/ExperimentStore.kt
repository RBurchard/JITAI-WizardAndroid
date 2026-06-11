package com.BWPStudio.JITAIWizard.experiment

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.jitaicompanion.convention.models.Experiment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
        // Public Downloads sub-folder for exported experiments (mirrors the logs folder style).
        const val EXPORT_DIR = "JITAI_WIZARD_experiments"
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

    /**
     * Parses an experiment from raw JSON (e.g. a file the researcher picked from the phone's
     * storage) and saves it as a schedule slot under its own name, so it shows up in the Load
     * list. Returns the slot name used, or null if the text was not a valid Experiment.
     */
    fun importSchedule(rawJson: String): String? = runCatching {
        val exp = json.decodeFromString<Experiment>(rawJson)
        val slot = sanitize(exp.name)
        saveSchedule(slot, exp)
        slot
    }.getOrNull()

    /**
     * Exports [experiment] as a pretty-printed .json into the public Downloads folder under
     * [EXPORT_DIR] (the same place style as the session logs, so it is visible in the Files
     * app and over USB). Returns a user-facing path to show the researcher, or null on failure.
     */
    fun exportToDownloads(experiment: Experiment): String? = runCatching {
        val safeName = sanitize(experiment.name).ifBlank { "experiment" }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "${safeName}_$stamp.json"
        val bytes = json.encodeToString(experiment).toByteArray()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/$EXPORT_DIR")
            }
            val resolver = appContext.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore insert returned null")
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Could not open output stream")
            "Downloads/$EXPORT_DIR/$fileName"
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), EXPORT_DIR)
            if (!dir.exists()) dir.mkdirs()
            val out = File(dir, fileName)
            out.writeText(String(bytes))
            out.absolutePath
        }
    }.getOrNull()

    /** Deletes a saved schedule slot (the DefaultExperiment slot is protected). */
    fun deleteSchedule(name: String) {
        if (sanitize(name) == DEFAULT_SCHEDULE) return
        runCatching { scheduleFile(name).delete() }
    }

    // ── Bundled experiment templates ────────────────────────────────────────
    // Extra experiment variants shipped with the APK (kept in sync with the Control
    // Station's Assets/experiment_*.json). They are seeded into schedule slots so the
    // phone can pick between them in the Load dialog. Maps asset file -> slot name shown.
    private val bundledTemplates = linkedMapOf(
        "experiment_distraction_combo.json" to "Distraction Combo - No Games",
        "experiment_alt_microgames.json"    to "Alternate Microgame - Lock Picking",
        "experiment_standstill_only.json"   to "Stand Still Only"
    )

    // Records which template assets have already been seeded, so a researcher who deletes
    // a seeded slot does not get it forced back every launch. New templates seed once.
    private val templateSeedMarker = File(appContext.filesDir, "seeded_templates.txt")

    private fun seedBundledTemplates() {
        val seeded = runCatching {
            templateSeedMarker.readLines().filter { it.isNotBlank() }.toMutableSet()
        }.getOrDefault(mutableSetOf())
        var changed = false
        for ((asset, slot) in bundledTemplates) {
            if (asset in seeded) continue
            runCatching {
                appContext.assets.open(asset).bufferedReader().use { r ->
                    val exp = json.decodeFromString<Experiment>(r.readText())
                    // Only seed if the researcher hasn't already created a slot of that name.
                    if (!scheduleFile(slot).exists()) saveSchedule(slot, exp)
                }
            }
            // Mark seeded regardless, so a parse failure or a later deletion never loops.
            seeded.add(asset)
            changed = true
        }
        if (changed) runCatching { templateSeedMarker.writeText(seeded.joinToString("\n")) }
    }

    private fun computeEtag(payload: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(payload.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.substring(0, 16)
    }

    init {
        // Runs last (declared at class end) so json/schedulesDir/markers are all ready.
        // Seeds bundled templates without touching the active experiment.
        runCatching { seedBundledTemplates() }
    }
}
