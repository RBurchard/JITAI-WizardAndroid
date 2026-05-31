package com.example.jitaicompanion.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.MeasureClient
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DeltaDataType
import com.example.jitaicompanion.convention.models.WatchDataBatch
import com.example.jitaicompanion.convention.models.WatchDataSnapshot
import com.example.jitaicompanion.datalayer.WearMessageSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

class WatchDataService : Service(), SensorEventListener {

    companion object {
        private const val TAG = "WatchDataService"
        private const val CHANNEL_ID = "watch_data_channel"
        private const val NOTIFICATION_ID = 1001
        private const val BATCH_INTERVAL_MS = 100L
        // Cap motion snapshots to ~50 Hz so faster sensor HALs don't over-produce.
        private const val MOTION_SAMPLE_MIN_INTERVAL_MS = 18L
        // Hard ceiling on buffered snapshots so a stalled batch loop can never
        // approach the 100 KB Wearable message cap (~250 bytes/snapshot).
        private const val MAX_BUFFERED_SNAPSHOTS = 600

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _heartRateState = MutableStateFlow(0f)
        val heartRateState: StateFlow<Float> = _heartRateState
    }

    private lateinit var sensorManager: SensorManager
    private lateinit var messageSender: WearMessageSender
    private val snapshotBuffer = CopyOnWriteArrayList<WatchDataSnapshot>()
    private var sessionId = ""
    private var latestHeartRate = 0f
    private var latestStepCount = 0
    private var latestAccelX = 0f
    private var latestAccelY = 0f
    private var latestAccelZ = 0f
    private var latestGyroX = 0f
    private var latestGyroY = 0f
    private var latestGyroZ = 0f
    private var latestRotationX = 0f
    private var latestRotationY = 0f
    private var latestRotationZ = 0f
    private var latestRotationW = 0f
    private var latestBarometer = 0f
    private var latestLight = 0f
    private var lastMotionSnapshotMs = 0L
    private var batchJob: Job? = null
    private var sampleJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    private val measureExecutor = Executors.newSingleThreadExecutor()
    private var measureClient: MeasureClient? = null

    private val heartRateMeasureCallback = object : MeasureCallback {
        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
            Log.d(TAG, "HR availability changed: $availability")
            // If availability is not AVAILABLE, we might not get data points
        }

        override fun onDataReceived(data: DataPointContainer) {
            Log.v(TAG, "onDataReceived: Data received from Health Services")
            data.getData(DataType.HEART_RATE_BPM).lastOrNull()?.value?.let { bpm ->
                val hr = bpm.toFloat()
                Log.d(TAG, "New HR value: $hr bpm")
                latestHeartRate = hr
                _heartRateState.value = hr
                snapshotBuffer.add(buildSnapshot())
            } ?: Log.w(TAG, "onDataReceived: No HEART_RATE_BPM in container")
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate: Service created")
        sensorManager = getSystemService(SensorManager::class.java)
        messageSender = WearMessageSender(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand: Intent received, sessionId=${intent?.getStringExtra("sessionId")}")
        sessionId = intent?.getStringExtra("sessionId") ?: ""
        
        val isAlreadyRunning = _isRunning.value
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            stopSelf()
            return START_NOT_STICKY
        }
        
        if (!isAlreadyRunning) {
            _isRunning.value = true
            registerSensors()
            startSamplingLoop()
            startBatchLoop()
        } else {
            Log.d(TAG, "Service already running, skipping sensor registration")
        }

        return START_STICKY
    }

    private fun startSamplingLoop() {
        sampleJob = serviceScope.launch {
            while (true) {
                delay(200L) // UI-friendly update rate
                _heartRateState.value = latestHeartRate
            }
        }
    }

    private fun registerSensors() {
        Log.i(TAG, "registerSensors: Starting registration for all sensors")
        
        // Strategy: Dual-register Heart Rate to ensure we catch the HAL events
        // 1. Modern Health Services (MeasureClient)
        registerHeartRateMeasure()
        
        // 2. Legacy SensorManager (Standard Android Sensor API)
        // We do this ALWAYS now, not just as a fallback, because some devices 
        // publish to the legacy HAL even if MeasureClient claims support.
        registerLegacyHeartRate()

        registerSensor(Sensor.TYPE_ACCELEROMETER, SensorManager.SENSOR_DELAY_GAME, "accelerometer")
        registerSensor(Sensor.TYPE_STEP_COUNTER, SensorManager.SENSOR_DELAY_NORMAL, "step_counter")
        registerSensor(Sensor.TYPE_GYROSCOPE, SensorManager.SENSOR_DELAY_GAME, "gyroscope")
        registerSensor(Sensor.TYPE_ROTATION_VECTOR, SensorManager.SENSOR_DELAY_GAME, "rotation_vector")
        registerSensor(Sensor.TYPE_PRESSURE, SensorManager.SENSOR_DELAY_NORMAL, "barometer")
        registerSensor(Sensor.TYPE_LIGHT, SensorManager.SENSOR_DELAY_NORMAL, "light")
    }

    private fun registerHeartRateMeasure() {
        Log.i(TAG, "Attempting to register heart rate measure via Health Services...")
        val client = HealthServices.getClient(this).measureClient
        measureClient = client

        serviceScope.launch {
            try {
                val capabilities = client.getCapabilitiesAsync().get()
                val isSupported = DataType.HEART_RATE_BPM in capabilities.supportedDataTypesMeasure
                Log.d(TAG, "Heart rate supported via Health Services: $isSupported")
                
                if (isSupported) {
                    Log.d(TAG, "Registering MeasureCallback...")
                    client.registerMeasureCallback(DataType.HEART_RATE_BPM, measureExecutor, heartRateMeasureCallback)
                    Log.i(TAG, "MeasureCallback registration call completed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Health Services capability check/registration failed", e)
            }
        }
    }

    private fun registerLegacyHeartRate() {
        Log.i(TAG, "Attempting to register legacy Heart Rate sensor via SensorManager...")
        registerSensor(Sensor.TYPE_HEART_RATE, SensorManager.SENSOR_DELAY_NORMAL, "heart_rate")
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
        if (event.sensor.type == Sensor.TYPE_HEART_RATE) {
            Log.i(TAG, "onSensorChanged [Legacy HR]: value=${event.values[0]} confidence=${if (event.values.size > 2) event.values[2] else "N/A"}")
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            Log.v(TAG, "onSensorChanged: ${event.sensor.name} value=${event.values[0]}")
        }
        when (event.sensor.type) {
            Sensor.TYPE_HEART_RATE -> {
                // Fallback path — only reached when Health Services unavailable
                val hr = event.values[0]
                Log.d(TAG, "SensorManager HR: $hr bpm")
                if (hr > 0f) {
                    latestHeartRate = hr
                    _heartRateState.value = hr
                    snapshotBuffer.add(buildSnapshot())
                }
            }
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
                latestAccelX = event.values[0]
                latestAccelY = event.values[1]
                latestAccelZ = event.values[2]
                // Accelerometer is the highest-rate motion sensor; drive the motion
                // stream from it (decimated to ~50 Hz) so the ControlStation can
                // reconstruct fast gestures. The batch loop flushes every 100 ms,
                // so this adds only ~5–6 snapshots per batch.
                val now = System.currentTimeMillis()
                if (now - lastMotionSnapshotMs >= MOTION_SAMPLE_MIN_INTERVAL_MS) {
                    lastMotionSnapshotMs = now
                    snapshotBuffer.add(buildSnapshot())
                    // Safety valve in case the batch loop stalls.
                    while (snapshotBuffer.size > MAX_BUFFERED_SNAPSHOTS) {
                        snapshotBuffer.removeAt(0)
                    }
                }
            }
        }
    }

    private fun buildSnapshot() = WatchDataSnapshot(
        timestamp = System.currentTimeMillis(),
        heartRate = latestHeartRate,
        accelX = latestAccelX,
        accelY = latestAccelY,
        accelZ = latestAccelZ,
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

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun startBatchLoop() {
        batchJob = serviceScope.launch {
            while (true) {
                delay(BATCH_INTERVAL_MS)
                // Always include one current-state snapshot so the phone receives data
                // even when the user is still and no HR event fired this window.
                snapshotBuffer.add(buildSnapshot())
                val snapshots = snapshotBuffer.toList()
                snapshotBuffer.clear()
                val batch = WatchDataBatch(sessionId = sessionId, snapshots = snapshots)
                messageSender.sendWatchData(batch)
                Log.d(TAG, "Sent batch of ${snapshots.size} snapshots, HR=${latestHeartRate}")
            }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy called")
        _isRunning.value = false
        _heartRateState.value = 0f
        super.onDestroy()
        batchJob?.cancel()
        sampleJob?.cancel()
        measureClient?.let {
            try {
                it.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, heartRateMeasureCallback)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister HR measure callback: ${e.message}")
            }
        }
        measureExecutor.shutdown()
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
