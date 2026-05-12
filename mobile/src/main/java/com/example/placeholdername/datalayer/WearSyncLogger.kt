package com.BWPStudio.JITAIWizard.datalayer

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class WearSyncLogger(context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val logFile = File(context.filesDir, "wear_sync_logs.tsv")
    private val lock = Any()

    fun log(
        eventType: String,
        value: String,
        path: String? = null,
        nodeId: String? = null,
        payloadBytes: Int? = null,
        details: String = ""
    ) {
        val safeDetails = details.replace("\n", " ").replace("\r", " ")
        val safePath = path ?: ""
        val safeNode = nodeId ?: ""
        val safeBytes = payloadBytes?.toString() ?: ""
        val line = "${System.currentTimeMillis()}\t$eventType\t$value\t$safePath\t$safeNode\t$safeBytes\t$safeDetails\n"
        scope.launch {
            try {
                synchronized(lock) {
                    ensureHeader()
                    logFile.appendText(line)
                }
            } catch (e: Exception) {
                Log.e("WearSyncLogger", "Failed to write wear sync log", e)
            }
        }
    }

    private fun ensureHeader() {
        if (logFile.exists() && logFile.length() > 0) return
        logFile.parentFile?.mkdirs()
        logFile.writeText("timestamp\tevent_type\tvalue\tpath\tnode_id\tpayload_bytes\tdetails\n")
    }
}
