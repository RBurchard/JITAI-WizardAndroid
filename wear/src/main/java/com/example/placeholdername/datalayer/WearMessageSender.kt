package com.example.jitaicompanion.datalayer

import android.content.Context
import android.os.SystemClock
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

        /**
         * How long a resolved node list is trusted. Looking nodes up is an IPC into Play
         * Services, and the data service used to make it before every one of its ten batches
         * a second. A phone that has gone is caught by the send failing, which drops the cache.
         */
        private const val NODE_CACHE_MS = 30_000L

        @Volatile private var cachedNodes: List<Node> = emptyList()
        @Volatile private var cachedAt = 0L
    }

    private val messageClient: MessageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)

    private suspend fun resolveNodes(): List<Node> {
        val now = SystemClock.elapsedRealtime()
        if (cachedNodes.isNotEmpty() && now - cachedAt < NODE_CACHE_MS) return cachedNodes
        return nodeClient.connectedNodes.await().also {
            cachedNodes = it
            cachedAt = now
        }
    }

    /**
     * @param notifyUser Toast on failure. Only for sends a person is waiting on: a failed
     *   sensor batch while the phone is out of range is a log line, not ten toasts a second.
     */
    private suspend fun sendToNodes(path: String, payload: ByteArray, label: String, notifyUser: Boolean = false) {
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
                cachedNodes = emptyList()
                if (notifyUser) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.error_send_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun sendResponse(message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            sendToNodes(Protocol.PATH_INTERVENTION_RESPONSE, message.toByteArray(), "response", notifyUser = true)
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

    /** Echoes the microgame settings the watch actually stored back to the phone. */
    fun sendGameSettingsAck(settingsJson: String) {
        CoroutineScope(Dispatchers.IO).launch {
            sendToNodes(Protocol.PATH_GAME_SETTINGS_ACK, settingsJson.toByteArray(), "settings ack")
        }
    }
}
