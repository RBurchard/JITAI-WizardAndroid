package com.BWPStudio.JITAIWizard.datalayer

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.BWPStudio.JITAIWizard.JITAIWizardApp
import com.BWPStudio.JITAIWizard.R
import com.example.jitaicompanion.convention.Protocol
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class WearMessageSender(private val context: Context) {

    companion object {
        private const val TAG = "WearMessageSender"
    }

    private val messageClient: MessageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)
    private val capabilityClient = Wearable.getCapabilityClient(context)
    private val syncLogger = (context.applicationContext as? JITAIWizardApp)?.wearSyncLogger
    private val noWearMessage: String by lazy { context.getString(R.string.wear_error_no_watch_found) }

    private suspend fun resolveNodes(): List<Node> {
        // Try capability-based discovery first — more reliable on Wear OS 4/5 because
        // it filters to nodes that have the watch app actually installed.
        return try {
            val capNodes = capabilityClient
                .getCapability("jitai_wizard_wear", CapabilityClient.FILTER_REACHABLE)
                .await()
                .nodes
            if (capNodes.isNotEmpty()) {
                Log.d(TAG, "Resolved ${capNodes.size} node(s) via capability")
                capNodes.toList()
            } else {
                // Fallback: all directly connected nodes (catches watches without capability
                // declared yet, e.g. first run before Data Layer re-syncs)
                val allNodes = nodeClient.connectedNodes.await()
                Log.d(TAG, "Capability returned 0 nodes, fallback to connectedNodes: ${allNodes.size}")
                allNodes
            }
        } catch (e: Exception) {
            Log.w(TAG, "CapabilityClient failed, falling back to connectedNodes: ${e.message}")
            nodeClient.connectedNodes.await()
        }
    }

    fun sendIntervention(message: String, onError: ((String) -> Unit)? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            val payload = message.toByteArray()
            val nodes = try {
                resolveNodes()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve connected nodes", e)
                syncLogger?.log(
                    eventType = "wear_send",
                    value = "intervention_failed",
                    path = Protocol.PATH_INTERVENTION,
                    payloadBytes = payload.size,
                    details = "resolve_node_failed: ${e.message}"
                )
                reportError(buildFailureMessage(e), onError)
                return@launch
            }

            if (nodes.isEmpty()) {
                syncLogger?.log(
                    eventType = "wear_send",
                    value = "intervention_failed",
                    path = Protocol.PATH_INTERVENTION,
                    payloadBytes = payload.size,
                    details = "no_connected_node"
                )
                reportError(noWearMessage, onError)
                return@launch
            }

            val nodeIds = nodes.joinToString(",") { it.id }
            syncLogger?.log(
                eventType = "wear_send",
                value = "intervention_nodes",
                path = Protocol.PATH_INTERVENTION,
                details = "count=${nodes.size}; ids=$nodeIds"
            )

            var failures = 0
            nodes.forEach { node ->
                try {
                    messageClient.sendMessage(node.id, Protocol.PATH_INTERVENTION, payload).await()
                    syncLogger?.log(
                        eventType = "wear_send",
                        value = "intervention_success",
                        path = Protocol.PATH_INTERVENTION,
                        nodeId = node.id,
                        payloadBytes = payload.size
                    )
                } catch (e: Exception) {
                    failures++
                    Log.e(TAG, "Failed to send intervention", e)
                    syncLogger?.log(
                        eventType = "wear_send",
                        value = "intervention_failed",
                        path = Protocol.PATH_INTERVENTION,
                        nodeId = node.id,
                        payloadBytes = payload.size,
                        details = e.message ?: "unknown_error"
                    )
                }
            }

            if (failures == nodes.size) {
                reportError(context.getString(R.string.wear_error_intervention_send_failed), onError)
            } else {
                Log.d(TAG, "Intervention sent to ${nodes.size - failures}/${nodes.size} nodes")
            }
        }
    }

    fun sendPing(onError: ((String) -> Unit)? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            val payload = "ping:${System.currentTimeMillis()}".toByteArray()
            val nodes = try {
                resolveNodes()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve connected nodes", e)
                syncLogger?.log(
                    eventType = "wear_send",
                    value = "ping_failed",
                    path = Protocol.PATH_PING,
                    payloadBytes = payload.size,
                    details = "resolve_node_failed: ${e.message}"
                )
                reportError(buildFailureMessage(e), onError)
                return@launch
            }

            if (nodes.isEmpty()) {
                syncLogger?.log(
                    eventType = "wear_send",
                    value = "ping_failed",
                    path = Protocol.PATH_PING,
                    payloadBytes = payload.size,
                    details = "no_connected_node"
                )
                reportError(noWearMessage, onError)
                return@launch
            }

            val nodeIds = nodes.joinToString(",") { it.id }
            syncLogger?.log(
                eventType = "wear_send",
                value = "ping_nodes",
                path = Protocol.PATH_PING,
                details = "count=${nodes.size}; ids=$nodeIds"
            )

            var failures = 0
            nodes.forEach { node ->
                try {
                    messageClient.sendMessage(node.id, Protocol.PATH_PING, payload).await()
                    syncLogger?.log(
                        eventType = "wear_send",
                        value = "ping_success",
                        path = Protocol.PATH_PING,
                        nodeId = node.id,
                        payloadBytes = payload.size
                    )
                } catch (e: Exception) {
                    failures++
                    Log.e(TAG, "Failed to send ping", e)
                    syncLogger?.log(
                        eventType = "wear_send",
                        value = "ping_failed",
                        path = Protocol.PATH_PING,
                        nodeId = node.id,
                        payloadBytes = payload.size,
                        details = e.message ?: "unknown_error"
                    )
                }
            }

            if (failures == nodes.size) {
                reportError(context.getString(R.string.wear_error_ping_failed), onError)
            } else {
                Log.d(TAG, "Ping sent to ${nodes.size - failures}/${nodes.size} nodes")
            }
        }
    }

    /**
     * Pushes microgame settings to every reachable watch.
     *
     * Deliberately silent on failure: this fires on app start, on every watch reconnect and on
     * every schedule load, so surfacing a dialog each time the watch happens to be asleep would
     * be noise. The watch's acknowledgement (and the in-sync indicator built on it) is how a
     * researcher learns whether it landed.
     */
    fun sendGameSettings(settingsJson: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val payload = settingsJson.toByteArray()
            val nodes = try {
                resolveNodes()
            } catch (e: Exception) {
                Log.w(TAG, "Settings push could not resolve nodes: ${e.message}")
                return@launch
            }
            if (nodes.isEmpty()) {
                syncLogger?.log(
                    eventType = "wear_send",
                    value = "game_settings_failed",
                    path = Protocol.PATH_GAME_SETTINGS,
                    payloadBytes = payload.size,
                    details = "no_connected_node"
                )
                return@launch
            }
            nodes.forEach { node ->
                try {
                    messageClient.sendMessage(node.id, Protocol.PATH_GAME_SETTINGS, payload).await()
                    syncLogger?.log(
                        eventType = "wear_send",
                        value = "game_settings_success",
                        path = Protocol.PATH_GAME_SETTINGS,
                        nodeId = node.id,
                        payloadBytes = payload.size,
                        details = settingsJson
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send game settings", e)
                    syncLogger?.log(
                        eventType = "wear_send",
                        value = "game_settings_failed",
                        path = Protocol.PATH_GAME_SETTINGS,
                        nodeId = node.id,
                        payloadBytes = payload.size,
                        details = e.message ?: "unknown_error"
                    )
                }
            }
        }
    }

    fun sendExit(onError: ((String) -> Unit)? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            val nodes = try {
                resolveNodes()
            } catch (e: Exception) {
                reportError(buildFailureMessage(e), onError)
                return@launch
            }
            if (nodes.isEmpty()) {
                reportError(noWearMessage, onError)
                return@launch
            }
            nodes.forEach { node ->
                try {
                    messageClient.sendMessage(node.id, Protocol.PATH_EXIT, ByteArray(0)).await()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send exit to ${node.id}", e)
                }
            }
        }
    }

    private fun buildFailureMessage(error: Exception): String {
        val reason = error.message?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.wear_error_unknown_reason)
        return context.getString(R.string.wear_error_reach_watch, reason)
    }

    private suspend fun reportError(message: String, onError: ((String) -> Unit)?) {
        withContext(Dispatchers.Main) {
            if (onError != null) {
                onError(message)
            } else {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
