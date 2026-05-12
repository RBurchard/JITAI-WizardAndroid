package com.BWPStudio.JITAIWizard.server

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.jitaicompanion.convention.Protocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class UdpBeacon(private val context: Context) {

    companion object {
        private const val TAG = "UdpBeacon"
        const val BEACON_PORT = 5280
        private const val BROADCAST_INTERVAL_MS = 5000L
    }

    private var job: Job? = null
    private val payload by lazy {
        """{"app":"JITAIWizard","port":${Protocol.HTTP_PORT},"name":"${Build.MODEL}"}"""
            .toByteArray()
    }

    fun start() {
        if (job?.isActive == true) return
        job = CoroutineScope(Dispatchers.IO).launch {
            try {
                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    val broadcastAddress = InetAddress.getByName("255.255.255.255")
                    Log.d(TAG, "UDP beacon started on port $BEACON_PORT")
                    while (isActive) {
                        try {
                            val packet = DatagramPacket(payload, payload.size, broadcastAddress, BEACON_PORT)
                            socket.send(packet)
                        } catch (e: Exception) {
                            Log.w(TAG, "Beacon send failed: ${e.message}")
                        }
                        delay(BROADCAST_INTERVAL_MS)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Beacon failed to start: ${e.message}", e)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        Log.d(TAG, "UDP beacon stopped")
    }
}
