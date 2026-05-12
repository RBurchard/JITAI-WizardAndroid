package com.example.jitaicompanion.datalayer

import android.content.Context
import android.util.Log
import android.widget.Toast
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

    private val messageClient: MessageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)
    private var cachedNode: Node? = null

    private suspend fun resolveNode(): Node? {
        if (cachedNode != null) return cachedNode
        val nodes = nodeClient.connectedNodes.await()
        cachedNode = nodes.firstOrNull()
        return cachedNode
    }

    fun sendResponse(message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val node = resolveNode()
            node?.let {
                try {
                    messageClient.sendMessage(it.id, Protocol.PATH_INTERVENTION_RESPONSE, message.toByteArray()).await()
                } catch (e: Exception) {
                    Log.e("WearMessageSender", "Failed to send response", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to send response: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun sendWatchData(batch: WatchDataBatch) {
        CoroutineScope(Dispatchers.IO).launch {
            val node = resolveNode()
            node?.let {
                try {
                    val json = Json.encodeToString(batch)
                    messageClient.sendMessage(it.id, Protocol.PATH_WATCH_DATA, json.toByteArray()).await()
                    Log.d("WearMessageSender", "Sent watch data batch: ${batch.snapshots.size} snapshots")
                } catch (e: Exception) {
                    Log.e("WearMessageSender", "Failed to send watch data", e)
                    cachedNode = null
                }
            }
        }
    }
}
