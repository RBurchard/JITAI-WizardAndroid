package com.example.jitaicompanion.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.util.Log
import com.example.jitaicompanion.convention.models.WatchDataBatch
import com.example.jitaicompanion.convention.models.WatchDataSnapshot
import com.example.jitaicompanion.datalayer.WearMessageSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

class WatchDataService : Service(), SensorEventListener {

    companion object {
        private const val CHANNEL_ID = "watch_data_channel"
        private const val NOTIFICATION_ID = 1001
        private const val BATCH_INTERVAL_MS = 5000L
    }

    private lateinit var sensorManager: SensorManager
    private lateinit var messageSender: WearMessageSender
    private val snapshotBuffer = CopyOnWriteArrayList<WatchDataSnapshot>()
    private var sessionId = ""
    private var latestHeartRate = 0f
    private var latestStepCount = 0
    private var batchJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SensorManager::class.java)
        messageSender = WearMessageSender(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        sessionId = intent?.getStringExtra("sessionId") ?: ""
        startForeground(NOTIFICATION_ID, buildNotification())
        registerSensors()
        startBatchLoop()
        return START_STICKY
    }

    private fun registerSensors() {
        sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_HEART_RATE -> latestHeartRate = event.values[0]
            Sensor.TYPE_STEP_COUNTER -> latestStepCount = event.values[0].toInt()
            Sensor.TYPE_ACCELEROMETER -> {
                snapshotBuffer.add(
                    WatchDataSnapshot(
                        timestamp = System.currentTimeMillis(),
                        heartRate = latestHeartRate,
                        accelX = event.values[0],
                        accelY = event.values[1],
                        accelZ = event.values[2],
                        stepCount = latestStepCount,
                        sessionId = sessionId
                    )
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun startBatchLoop() {
        batchJob = serviceScope.launch {
            while (true) {
                delay(BATCH_INTERVAL_MS)
                val snapshots = snapshotBuffer.toList()
                snapshotBuffer.clear()
                if (snapshots.isNotEmpty()) {
                    val batch = WatchDataBatch(sessionId = sessionId, snapshots = snapshots)
                    messageSender.sendWatchData(batch)
                    Log.d("WatchDataService", "Sent batch of ${snapshots.size} snapshots")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        batchJob?.cancel()
        sensorManager.unregisterListener(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Watch Data Collection",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("JITAI Companion")
            .setContentText("Collecting sensor data")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .build()
}
