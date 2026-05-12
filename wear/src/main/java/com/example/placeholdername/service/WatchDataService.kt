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
        private const val TAG = "WatchDataService"
        private const val CHANNEL_ID = "watch_data_channel"
        private const val NOTIFICATION_ID = 1001
        private const val BATCH_INTERVAL_MS = 5000L

        @Volatile
        var isRunning = false
            private set
    }

    private lateinit var sensorManager: SensorManager
    private lateinit var messageSender: WearMessageSender
    private val snapshotBuffer = CopyOnWriteArrayList<WatchDataSnapshot>()
    private var sessionId = ""
    private var latestHeartRate = 0f
    private var latestStepCount = 0
    private var latestGyroX = 0f
    private var latestGyroY = 0f
    private var latestGyroZ = 0f
    private var latestRotationX = 0f
    private var latestRotationY = 0f
    private var latestRotationZ = 0f
    private var latestRotationW = 0f
    private var latestBarometer = 0f
    private var latestLight = 0f
    private var batchJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SensorManager::class.java)
        messageSender = WearMessageSender(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRunning = true
        sessionId = intent?.getStringExtra("sessionId") ?: ""
        startForeground(NOTIFICATION_ID, buildNotification())
        registerSensors()
        startBatchLoop()
        return START_STICKY
    }

    private fun registerSensors() {
        registerSensor(Sensor.TYPE_HEART_RATE, SensorManager.SENSOR_DELAY_NORMAL, "heart_rate")
        registerSensor(Sensor.TYPE_ACCELEROMETER, SensorManager.SENSOR_DELAY_GAME, "accelerometer")
        registerSensor(Sensor.TYPE_STEP_COUNTER, SensorManager.SENSOR_DELAY_NORMAL, "step_counter")
        registerSensor(Sensor.TYPE_GYROSCOPE, SensorManager.SENSOR_DELAY_GAME, "gyroscope")
        registerSensor(Sensor.TYPE_ROTATION_VECTOR, SensorManager.SENSOR_DELAY_GAME, "rotation_vector")
        registerSensor(Sensor.TYPE_PRESSURE, SensorManager.SENSOR_DELAY_NORMAL, "barometer")
        registerSensor(Sensor.TYPE_LIGHT, SensorManager.SENSOR_DELAY_NORMAL, "light")
    }

    private fun registerSensor(type: Int, delay: Int, label: String) {
        val sensor = sensorManager.getDefaultSensor(type)
        if (sensor == null) {
            Log.w(TAG, "Sensor missing: $label")
            return
        }
        val registered = sensorManager.registerListener(this, sensor, delay)
        if (registered) {
            Log.d(TAG, "Sensor registered: $label (${sensor.name}), delay=$delay")
        } else {
            Log.w(TAG, "Failed to register sensor: $label (${sensor.name})")
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_HEART_RATE -> latestHeartRate = event.values[0]
            Sensor.TYPE_STEP_COUNTER -> latestStepCount = event.values[0].toInt()
            Sensor.TYPE_GYROSCOPE -> {
                latestGyroX = event.values[0]
                latestGyroY = event.values[1]
                latestGyroZ = event.values[2]
            }
            Sensor.TYPE_ROTATION_VECTOR -> {
                latestRotationX = if (event.values.size > 0) event.values[0] else 0f
                latestRotationY = if (event.values.size > 1) event.values[1] else 0f
                latestRotationZ = if (event.values.size > 2) event.values[2] else 0f
                latestRotationW = if (event.values.size > 3) event.values[3] else 0f
            }
            Sensor.TYPE_PRESSURE -> latestBarometer = event.values[0]
            Sensor.TYPE_LIGHT -> latestLight = event.values[0]
            Sensor.TYPE_ACCELEROMETER -> {
                snapshotBuffer.add(
                    WatchDataSnapshot(
                        timestamp = System.currentTimeMillis(),
                        heartRate = latestHeartRate,
                        accelX = event.values[0],
                        accelY = event.values[1],
                        accelZ = event.values[2],
                        gyroX = latestGyroX,
                        gyroY = latestGyroY,
                        gyroZ = latestGyroZ,
                        rotationX = latestRotationX,
                        rotationY = latestRotationY,
                        rotationZ = latestRotationZ,
                        rotationW = latestRotationW,
                        barometer = latestBarometer,
                        light = latestLight,
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
        isRunning = false
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
