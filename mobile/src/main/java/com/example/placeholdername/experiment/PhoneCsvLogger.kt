package com.BWPStudio.JITAIWizard.experiment

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.BWPStudio.JITAIWizard.JITAIWizardApp
import com.BWPStudio.JITAIWizard.datalayer.WatchDataRelay
import com.BWPStudio.JITAIWizard.server.ServerState
import com.BWPStudio.JITAIWizard.settings.GameSettingsStore
import com.example.jitaicompanion.convention.models.MicrogameSettings
import com.example.jitaicompanion.convention.models.SessionLogEntry
import com.example.jitaicompanion.convention.models.WatchDataSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.OutputStream
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Phone-side CSV exporter that reproduces the Control Station's multi-section CSV
 * (SESSION / WATCH DATA / SESSION LOGS / INTERVENTIONS / EXPERIMENT RUNS / DISTRACTION RUNS)
 * so a researcher can pull a 1:1 export with only the phone present.
 *
 * The heavy section, WATCH DATA, is streamed to a private temp file as batches arrive from the
 * watch. The watch already groups snapshots into ~100 ms batches (~5 snapshots each at the 50 Hz
 * cap), so we do one buffered append per batch — ~10 IO flushes/second regardless of sensor rate,
 * which keeps the phone comfortable even on long sessions. The smaller sections are assembled from
 * in-memory phone state ([SessionLogStore], [ServerState]) at [finalizeExport] time.
 *
 * INTERVENTIONS / EXPERIMENT RUNS / DISTRACTION RUNS are synthesised from the session-log stream
 * (the phone doesn't keep the Control Station's dedicated tables), so game/notification columns are
 * left blank — the timing, response and reaction-time columns are faithful.
 */
class PhoneCsvLogger(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    private var collectJob: Job? = null
    private var tempFile: File? = null
    private var writer: BufferedWriter? = null

    // Run metadata captured at start().
    @Volatile private var active = false
    private var rowCount = 0L
    private var runId = ""
    private var ownsRunId = false
    private var experimentName = ""
    // The participant/session name. On the Control Station this is the SessionName, which is also
    // used as the participant_key — so the phone uses participantId for the SESSION `name`,
    // `participants`, and every watch/intervention participant_label + participant_key column.
    private var participantName = ""
    private var labelCsv = ""
    private var keyCsv = ""
    private var startedAt = 0L
    // Captured at start() and refreshed at finalize: the export has to state the settings the
    // run was played at, which is not necessarily what is loaded by the time someone exports.
    private var finalSettings = MicrogameSettings()

    companion object {
        const val EXPORT_DIR = "JITAI_WIZARD_csv"
        private const val WATCH_HEADER =
            "participant_label,participant_key,timestamp,heart_rate,accel_x,accel_y,accel_z," +
            "gyro_x,gyro_y,gyro_z,rot_w,rot_x,rot_y,rot_z,baro_hpa,light_lux,step_count"
        // The ring buffer caps at 2000; this comfortably drains every retained entry.
        private const val LOG_FETCH_LIMIT = 5000
    }

    /**
     * Begins capturing the watch stream for a run. Safe to call when "Save Logs" is on; pair every
     * call with [finalizeExport]. [participantName] is the participant/session name (used for the
     * SESSION `name`/`participants` and every participant column); [experimentName] feeds only the
     * EXPERIMENT RUNS section. The session `notes` are gathered at finalize from the run's Note
     * entries, mirroring the Control Station's session-notes field. Tags session logs with a run id
     * (claiming one only if the engine isn't already running its own).
     */
    fun start(experimentName: String, participantName: String) {
        synchronized(lock) {
            if (active) return
            finalSettings = runCatching {
                (appContext as? JITAIWizardApp)?.gameSettingsStore?.settings?.value
            }.getOrNull() ?: MicrogameSettings()
            this.experimentName = experimentName.ifBlank { "defaultExperiment" }
            this.participantName = participantName.ifBlank { "defaultParticipant" }
            this.labelCsv = esc(this.participantName)
            this.keyCsv = esc(this.participantName)
            this.startedAt = System.currentTimeMillis()
            this.rowCount = 0L

            val existingRun = SessionLogStore.currentRunId
            if (existingRun == null) {
                runId = UUID.randomUUID().toString()
                SessionLogStore.currentRunId = runId
                ownsRunId = true
            } else {
                runId = existingRun
                ownsRunId = false
            }

            tempFile = File(appContext.cacheDir, "watchdata_$runId.csv")
            writer = runCatching { BufferedWriter(FileWriter(tempFile!!)) }
                .onFailure { Log.e("PhoneCsvLogger", "temp file open failed", it) }
                .getOrNull()
            active = writer != null
            if (!active) return
        }

        collectJob = scope.launch {
            WatchDataRelay.flow.collect { batch ->
                synchronized(lock) {
                    val w = writer ?: return@collect
                    val sb = StringBuilder(batch.snapshots.size * 128)
                    for (s in batch.snapshots) { appendSnapshotRow(sb, s); rowCount++ }
                    runCatching { w.write(sb.toString()); w.flush() }
                        .onFailure { Log.e("PhoneCsvLogger", "watch row write failed", it) }
                }
            }
        }
    }

    /**
     * Stops capturing and writes the assembled multi-section CSV into Downloads/[EXPORT_DIR].
     * Returns a user-facing path, or null if nothing was being captured or the write failed.
     */
    fun finalizeExport(): String? {
        synchronized(lock) {
            if (!active) return null
            active = false
            runCatching { writer?.flush(); writer?.close() }
            writer = null
        }
        collectJob?.cancel(); collectJob = null
        if (ownsRunId && SessionLogStore.currentRunId == runId) SessionLogStore.currentRunId = null

        finalSettings = runCatching {
            (appContext as? JITAIWizardApp)?.gameSettingsStore?.settings?.value
        }.getOrNull() ?: finalSettings

        val path = runCatching { assembleAndWrite() }
            .onFailure { Log.e("PhoneCsvLogger", "CSV assembly failed", it) }
            .getOrNull()
        runCatching { tempFile?.delete() }
        tempFile = null
        return path
    }

    // ── Assembly ────────────────────────────────────────────────────────────

    private fun assembleAndWrite(): String? {
        // Mirror the Control Station's "{sessionName}_{date}.csv" naming.
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "${sanitize(participantName)}_$stamp.csv"
        val target = openDownloadsStream(fileName) ?: return null

        val logs = SessionLogStore.query(0, LOG_FETCH_LIMIT).filter { it.runId == runId }
        val sessionNote = extractSessionNote(logs)
        val interventions = synthesizeInterventions(logs)

        target.stream.bufferedWriter().use { w ->
            writeSession(w, sessionNote, logs.size)
            writeGameSettings(w, logs)
            writeWatchData(w)
            writeSessionLogs(w, logs)
            writeInterventions(w, interventions)
            writeRuns(w)
            writeDistractionRuns(w, interventions)
        }
        return target.displayPath
    }

    private fun writeSession(w: Writer, sessionNote: String, logCount: Int) {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sessionId = ServerState.sessionId.ifBlank { runId }
        val start = if (ServerState.sessionStartedAt > 0) ServerState.sessionStartedAt else startedAt
        w.write("=== SESSION ===\n")
        w.write("id,${esc(sessionId)}\n")
        w.write("name,${esc(participantName)}\n")
        w.write("notes,${esc(sessionNote)}\n")
        w.write("started_at,${fmt.format(Date(start))}\n")
        w.write("ended_at,${fmt.format(Date())}\n")
        w.write("participants,${esc(participantName)}\n")
        w.write("\n")
        w.write("# diagnostic: source=phone, watch_rows=$rowCount, session_logs=$logCount, run_id=$runId\n")
        w.write("\n")
    }

    /**
     * The session notes are the free-text Note entries the researcher jotted during the run (the
     * Control Station keeps a single overwriteable notes field; the phone records discrete notes,
     * so all of the run's notes are joined newline-separated). Mirrors `sessions.notes`.
     */
    private fun extractSessionNote(logs: List<SessionLogEntry>): String =
        logs.asSequence()
            .filter { it.source == "experiment" && it.kind == "Note" }
            .mapNotNull { runCatching { json.parseToJsonElement(it.payloadJson).jsonObject }.getOrNull()?.str("value") }
            .filter { it.isNotBlank() }
            .joinToString("\n")

    /**
     * The microgame difficulty the participant actually faced.
     *
     * Two sections' worth of information in one: the `final` row is the settings in force at
     * export time, and the `change` rows are every adjustment during the run, in order, with the
     * origin that caused it. Without the history a mid-session tweak would be invisible in the
     * export, and a run's game results could not be attributed to the difficulty they were
     * played at.
     *
     * Read back out of the session log rather than from live state so the export reflects the
     * run being exported, not whatever the phone happens to hold now.
     */
    private fun writeGameSettings(w: Writer, logs: List<SessionLogEntry>) {
        w.write("=== MINIGAME SETTINGS ===\n")
        w.write("participant_label,scope,ts,origin,simon_difficulty,simon_rounds,lock_difficulty,lock_penalty\n")

        val changes = logs.filter { it.kind == GameSettingsStore.LOG_KIND }
        for (e in changes) {
            val obj = runCatching { json.parseToJsonElement(e.payloadJson).jsonObject }.getOrNull()
            w.write(
                "$labelCsv,change,${e.ts},${esc(obj?.str("origin") ?: "")}," +
                    "${obj?.str("simon_difficulty") ?: ""},${obj?.str("simon_rounds") ?: ""}," +
                    "${obj?.str("lock_difficulty") ?: ""},${esc(obj?.str("lock_penalty") ?: "")}\n"
            )
        }

        val f = finalSettings
        w.write(
            "$labelCsv,final,${System.currentTimeMillis()},," +
                "${f.simonDifficulty},${f.simonRounds},${f.lockDifficulty},${esc(f.lockPenalty.name)}\n"
        )
        w.write("\n")
    }

    private fun writeWatchData(w: Writer) {
        w.write("=== WATCH DATA ===\n")
        w.write(WATCH_HEADER); w.write("\n")
        // Stream the temp file straight through so a long session never sits fully in memory.
        tempFile?.takeIf { it.exists() }?.bufferedReader()?.use { it.copyTo(w) }
        w.write("\n")
    }

    private fun writeSessionLogs(w: Writer, logs: List<SessionLogEntry>) {
        w.write("=== SESSION LOGS ===\n")
        w.write("id,run_id,ts,source,level,kind,payload_json\n")
        for (e in logs)
            w.write("${e.id},${e.runId ?: ""},${e.ts},${esc(e.source)},${esc(e.level)},${esc(e.kind)},${esc(e.payloadJson)}\n")
        w.write("\n")
    }

    private fun writeInterventions(w: Writer, interventions: List<Synth>) {
        w.write("=== INTERVENTIONS ===\n")
        w.write("id,participant_label,participant_key,sent_at,game_type,notification_type,response,reaction_time_ms\n")
        for (i in interventions)
            w.write("${esc(i.id)},$labelCsv,$keyCsv,${i.sentAt},,,${esc(i.response ?: "")},${i.reactionMs ?: ""}\n")
        w.write("\n")
    }

    private fun writeRuns(w: Writer) {
        w.write("=== EXPERIMENT RUNS ===\n")
        w.write("run_id,experiment_name,participant_label,started_at,ended_at\n")
        w.write("${esc(runId)},${esc(experimentName)},$labelCsv,$startedAt,${System.currentTimeMillis()}\n")
        w.write("\n")
    }

    private fun writeDistractionRuns(w: Writer, interventions: List<Synth>) {
        w.write("=== DISTRACTION RUNS ===\n")
        w.write("distraction_id,run_id,event_id,intervention_id,started_at,ended_at,response,reaction_time_ms\n")
        for (i in interventions) {
            val endedAt = i.reactionMs?.let { i.sentAt + it }?.toString() ?: ""
            w.write("${esc(UUID.randomUUID().toString())},${esc(runId)},,${esc(i.id)},${i.sentAt},$endedAt,${esc(i.response ?: "")},${i.reactionMs ?: ""}\n")
        }
        w.write("\n")
    }

    // ── Intervention synthesis ──────────────────────────────────────────────

    private class Synth(val id: String, val sentAt: Long) {
        var response: String? = null
        var reactionMs: Long? = null
    }

    /**
     * Reconstructs intervention send/response pairs from the session-log stream. A "send" is an
     * engine `intervention-sent` or a manual experiment `Intervention`/`start`; a "response" is a
     * watch `intervention-response`, matched LIFO to the most recent open send (Wizard-of-Oz runs
     * are sequential, so newest-open is the right pairing).
     */
    private fun synthesizeInterventions(logs: List<SessionLogEntry>): List<Synth> {
        val result = ArrayList<Synth>()
        val open = ArrayDeque<Synth>()
        for (e in logs) {
            val obj = runCatching { json.parseToJsonElement(e.payloadJson).jsonObject }.getOrNull()
            val isSent = (e.source == "engine" && e.kind == "intervention-sent") ||
                (e.source == "experiment" && e.kind == "Intervention" && obj?.str("value") == "start")
            val isResp = e.source == "watch" && e.kind == "intervention-response"
            if (isSent) {
                val s = Synth(obj?.str("id") ?: UUID.randomUUID().toString(), e.ts)
                result.add(s); open.addLast(s)
            } else if (isResp) {
                val s = open.removeLastOrNull() ?: result.lastOrNull { it.response == null }
                if (s != null) {
                    s.response = obj?.str("payload") ?: "response"
                    s.reactionMs = obj?.long("reactionMs")
                }
            }
        }
        return result
    }

    /** Reads a field as text whether it was written as a JSON string or a number. */
    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.long(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull

    // ── Output helpers ──────────────────────────────────────────────────────

    private fun appendSnapshotRow(sb: StringBuilder, s: WatchDataSnapshot) {
        // Raw Float/Int toString keeps the decimal point locale-independent (a German locale must
        // not emit comma decimals into a CSV). Column order matches the Control Station export.
        sb.append(labelCsv).append(',').append(keyCsv).append(',')
            .append(s.timestamp).append(',')
            .append(s.heartRate).append(',')
            .append(s.accelX).append(',').append(s.accelY).append(',').append(s.accelZ).append(',')
            .append(s.gyroX).append(',').append(s.gyroY).append(',').append(s.gyroZ).append(',')
            .append(s.rotationW).append(',').append(s.rotationX).append(',')
            .append(s.rotationY).append(',').append(s.rotationZ).append(',')
            .append(s.barometer).append(',').append(s.light).append(',').append(s.stepCount)
            .append('\n')
    }

    private class Target(val stream: OutputStream, val displayPath: String)

    private fun openDownloadsStream(fileName: String): Target? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/$EXPORT_DIR")
            }
            val resolver = appContext.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            val os = resolver.openOutputStream(uri) ?: return null
            Target(os, "Downloads/$EXPORT_DIR/$fileName")
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), EXPORT_DIR)
            if (!dir.exists()) dir.mkdirs()
            val out = File(dir, fileName)
            Target(FileOutputStream(out), out.absolutePath)
        }
    }

    private fun sanitize(name: String): String =
        name.trim().ifBlank { "export" }.replace(Regex("[^A-Za-z0-9 _-]"), "_").take(64)

    /** CSV-escapes a field exactly like the Control Station: quote-wrap if it holds , " or newline. */
    private fun esc(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r'))
            "\"${value.replace("\"", "\"\"")}\""
        else value
}
