package com.BWPStudio.JITAIWizard.experiment

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class ExperimentLogger(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var participant = "unknown"
    private lateinit var fileUri: Uri
    private lateinit var legacyFile: File
    var saveLogs = false

    fun createLogFile(experimentName: String, participantName: String, timestamp: String, saveLogs: Boolean = true) {
        this.saveLogs = saveLogs
        if (!saveLogs) return
        val expName = experimentName.ifEmpty { "defaultExperiment" }
        val partName = participantName.ifEmpty { "defaultParticipant" }
        participant = partName
        val fileName = "${expName}_log_${partName}_$timestamp.tsv"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/tab-separated-values")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/JITAI_WIZARD_logs")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { it.write("timestamp\tparticipant\tevent_type\tvalue\tdetails\n".toByteArray()) }
                fileUri = uri
                Log.d("ExperimentLogger", "Log file: $uri")
            }
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "JITAI_WIZARD_logs")
            if (!dir.exists()) dir.mkdirs()
            legacyFile = File(dir, fileName).also { it.writeText("timestamp\tparticipant\tevent_type\tvalue\tdetails\n") }
        }
    }

    fun log(event: LogEvent) {
        if (!saveLogs) return
        scope.launch {
            val line = "${event.timestamp}\t$participant\t${event.eventType}\t${event.value}\t${event.details}\n"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.openOutputStream(fileUri, "wa")?.use { it.write(line.toByteArray()) }
            } else {
                legacyFile.appendText(line)
            }
        }
    }
}

data class LogEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String,
    val value: String,
    val details: String = ""
)
