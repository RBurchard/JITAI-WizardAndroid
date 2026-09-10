package com.BWPStudio.JITAIWizard.datalayer

import android.util.Log
import android.widget.Toast
import com.example.jitaicompanion.convention.Protocol
import com.example.jitaicompanion.convention.models.WatchDataBatch
import com.BWPStudio.JITAIWizard.JITAIWizardApp
import com.BWPStudio.JITAIWizard.R
import com.BWPStudio.JITAIWizard.experiment.InterventionResponse
import com.BWPStudio.JITAIWizard.experiment.InterventionResponseBus
import com.BWPStudio.JITAIWizard.experiment.LogEvent
import com.BWPStudio.JITAIWizard.experiment.SessionLogStore
import com.BWPStudio.JITAIWizard.server.ServerState
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

class WearMessageListener : WearableListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val syncLogger by lazy { (application as JITAIWizardApp).wearSyncLogger }
    private val nodeClient by lazy { Wearable.getNodeClient(this) }

    override fun onCreate() {
        super.onCreate()
        refreshConnection("startup")
    }

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            Protocol.PATH_INTERVENTION_RESPONSE -> handleResponse(String(event.data))
            Protocol.PATH_WATCH_DATA -> handleWatchData(String(event.data))
            Protocol.PATH_PONG -> handlePong(String(event.data))
            Protocol.PATH_GAME_SETTINGS_ACK -> handleGameSettingsAck(String(event.data))
        }
    }

    override fun onPeerConnected(node: Node) {
        refreshConnection("peer_connected:${node.id}")
        // A watch that was asleep or out of range missed every push made while it was gone.
        // Re-pushing on reconnect is what stops it from quietly running the previous
        // participant's difficulty for a whole session.
        runCatching { (application as JITAIWizardApp).gameSettingsStore.pushCurrent("peer_connected") }
    }

    override fun onPeerDisconnected(node: Node) {
        refreshConnection("peer_disconnected:${node.id}")
    }

    private fun handleResponse(data: String) {
        val logger = (application as JITAIWizardApp).logger
        val sentAt = ServerState.lastInterventionSentAt
        val reactionMs = if (sentAt > 0L) System.currentTimeMillis() - sentAt else null

        logger.log(LogEvent(eventType = "Intervention", value = "watch_response", details = data))
        syncLogger.log(
            eventType = "wear_receive",
            value = "intervention_response",
            path = Protocol.PATH_INTERVENTION_RESPONSE,
            payloadBytes = data.toByteArray().size,
            details = data
        )

        scope.launch(Dispatchers.Main) {
            val base = applicationContext.getString(R.string.wear_toast_watch_response, data)
            val message = reactionMs?.let { base + applicationContext.getString(R.string.wear_toast_reaction_suffix, it) } ?: base
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }

        reactionMs?.let { ServerState.lastReactionTimeMs = it }
        ServerState.lastAction = data
        scope.launch {
            InterventionResponseBus.emit(
                InterventionResponse(
                    ts = System.currentTimeMillis(),
                    payload = data,
                    reactionMs = reactionMs
                )
            )
            val escaped = JsonPrimitive(data).toString()
            SessionLogStore.append(
                source = "watch",
                kind = "intervention-response",
                payloadJson = "{\"payload\":$escaped,\"reactionMs\":${reactionMs ?: "null"}}"
            )
        }
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
                syncLogger.log(
                    eventType = "wear_receive",
                    value = "watch_data",
                    path = Protocol.PATH_WATCH_DATA,
                    payloadBytes = data.toByteArray().size,
                    details = "snapshots=${batch.snapshots.size}"
                )
                Log.d("WearMessageListener", "Received watch batch: ${batch.snapshots.size} snapshots")
            } catch (e: Exception) {
                syncLogger.log(
                    eventType = "wear_receive",
                    value = "watch_data_parse_failed",
                    path = Protocol.PATH_WATCH_DATA,
                    payloadBytes = data.toByteArray().size,
                    details = e.message ?: "unknown_error"
                )
                Log.e("WearMessageListener", "Failed to parse watch data", e)
            }
        }
    }

    private fun handleGameSettingsAck(data: String) {
        syncLogger.log(
            eventType = "wear_receive",
            value = "game_settings_ack",
            path = Protocol.PATH_GAME_SETTINGS_ACK,
            payloadBytes = data.toByteArray().size,
            details = data
        )
        runCatching { (application as JITAIWizardApp).gameSettingsStore.onWatchAck(data) }
        Log.d("WearMessageListener", "Settings ack: $data")
    }

    private fun handlePong(data: String) {
        syncLogger.log(
            eventType = "wear_receive",
            value = "pong",
            path = Protocol.PATH_PONG,
            payloadBytes = data.toByteArray().size,
            details = data
        )
        Log.d("WearMessageListener", "Pong received: $data")
    }

    private fun refreshConnection(reason: String) {
        scope.launch {
            try {
                val nodes = nodeClient.connectedNodes.await()
                val connected = nodes.isNotEmpty()
                val previous = ServerState.watchConnected
                ServerState.watchConnected = connected
                if (connected != previous) {
                    val nodeIds = nodes.joinToString(",") { it.id }
                    syncLogger.log(
                        eventType = "wear_connection",
                        value = if (connected) "connected" else "disconnected",
                        details = "nodes=${nodes.size}; ids=$nodeIds; reason=$reason"
                    )
                }
            } catch (e: Exception) {
                syncLogger.log(
                    eventType = "wear_connection",
                    value = "error",
                    details = "${reason}: ${e.message}"
                )
            }
        }
    }
}
