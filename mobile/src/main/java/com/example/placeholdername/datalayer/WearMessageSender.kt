package com.BWPStudio.JITAIWizard.datalayer

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.jitaicompanion.convention.Protocol
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

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

    fun sendIntervention(message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val node = resolveNode()
            node?.let {
                try {
                    messageClient.sendMessage(it.id, Protocol.PATH_INTERVENTION, message.toByteArray()).await()
                    Log.d("WearMessageSender", "Intervention sent")
                } catch (e: Exception) {
                    Log.e("WearMessageSender", "Failed to send intervention", e)
                    cachedNode = null
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to reach watch: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } ?: withContext(Dispatchers.Main) {
                Toast.makeText(context, "No watch connected", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
