package com.example.jitaicompanion.datalayer

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.jitaicompanion.R
import com.example.jitaicompanion.convention.Protocol
import com.example.jitaicompanion.convention.models.WatchDataBatch
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WearMessageSender(private val context: Context) {

    companion object {
        private const val TAG = "WearMessageSender"
    }

    private val messageClient: MessageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)

    private suspend fun resolveNodes(): List<Node> = nodeClient.connectedNodes.await()

    private suspend fun sendToNodes(path: String, payload: ByteArray, label: String) {
        val nodes = resolveNodes()
        if (nodes.isEmpty()) {
            Log.w(TAG, "No connected node for $label")
            return
        }
        nodes.forEach { node ->
            try {
                messageClient.sendMessage(node.id, path, payload).await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send $label", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.error_send_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun sendResponse(message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            sendToNodes(Protocol.PATH_INTERVENTION_RESPONSE, message.toByteArray(), "response")
        }
    }

    fun sendWatchData(batch: WatchDataBatch) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = Json.encodeToString(batch)
                sendToNodes(Protocol.PATH_WATCH_DATA, json.toByteArray(), "watch data")
                Log.d(TAG, "Sent watch data batch: ${batch.snapshots.size} snapshots")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send watch data", e)
            }
        }
    }

    fun sendPong(message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            sendToNodes(Protocol.PATH_PONG, message.toByteArray(), "pong")
        }
    }
}
