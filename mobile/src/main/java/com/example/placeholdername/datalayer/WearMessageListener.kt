package com.BWPStudio.JITAIWizard.datalayer

import android.util.Log
import android.widget.Toast
import com.example.jitaicompanion.convention.Protocol
import com.example.jitaicompanion.convention.models.WatchDataBatch
import com.BWPStudio.JITAIWizard.OcdWizardApp
import com.BWPStudio.JITAIWizard.experiment.LogEvent
import com.BWPStudio.JITAIWizard.server.ServerState
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class WearMessageListener : WearableListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            Protocol.PATH_INTERVENTION_RESPONSE -> handleResponse(String(event.data))
            Protocol.PATH_WATCH_DATA -> handleWatchData(String(event.data))
        }
    }

    private fun handleResponse(data: String) {
        val logger = (application as OcdWizardApp).logger
        val sentAt = ServerState.lastInterventionSentAt
        val reactionMs = if (sentAt > 0L) System.currentTimeMillis() - sentAt else null

        logger.log(LogEvent(eventType = "Intervention", value = "watch_response", details = data))

        scope.launch(Dispatchers.Main) {
            Toast.makeText(applicationContext, "Watch: $data${reactionMs?.let { " (${it}ms)" } ?: ""}", Toast.LENGTH_SHORT).show()
        }

        reactionMs?.let { ServerState.lastReactionTimeMs = it }
        ServerState.lastAction = data
        Log.d("WearMessageListener", "Response: $data, reaction: ${reactionMs}ms")
    }

    private fun handleWatchData(data: String) {
        scope.launch {
            try {
                val batch = json.decodeFromString<WatchDataBatch>(data)
                WatchDataRelay.emit(batch)
                batch.snapshots.lastOrNull()?.heartRate?.let { hr ->
                    ServerState.lastHeartRate = hr
                }
                Log.d("WearMessageListener", "Received watch batch: ${batch.snapshots.size} snapshots")
            } catch (e: Exception) {
                Log.e("WearMessageListener", "Failed to parse watch data", e)
            }
        }
    }
}
