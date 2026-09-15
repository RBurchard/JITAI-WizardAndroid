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
import androidx.health.services.client.PassiveListenerCallback
import androidx.health.services.client.PassiveMonitoringClient
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DeltaDataType
import androidx.health.services.client.data.PassiveListenerConfig
import com.example.jitaicompanion.R
import com.example.jitaicompanion.convention.models.WatchDataBatch
import com.example.jitaicompanion.convention.models.WatchDataSnapshot
import com.example.jitaicompanion.datalayer.WearMessageSender
import com.example.jitaicompanion.power.PowerProfile
import com.example.jitaicompanion.power.WatchPowerPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * Collects the watch's sensor stream and ships it to the phone.
 *
 * Runs at one of two rates, chosen by [WatchPowerPolicy]:
 *
 * - **Active** is the study configuration and is unchanged: accelerometer, gyroscope and
 *   rotation vector at `SENSOR_DELAY_GAME`, continuous heart rate through both the Health
 *   Services and the legacy path, motion decimated to ~50 Hz and a batch flushed every 100 ms.
 * - **Idle** registers nothing that wakes the application processor on a schedule. The step
 *   counter is kept, batched in the sensor hub for [IDLE_SENSOR_LATENCY_MS] at a time; the
 *   accelerometer goes entirely (the step counter already says whether the wrist is moving).
 *   Heart rate comes from Health Services' passive monitoring, which piggybacks on whatever
 *   periodic reading the system is already taking, falling back to a short measurement every
 *   few minutes on watches that cannot do that. One snapshot is flushed every
 *   [IDLE_BATCH_INTERVAL_MS] so the phone still sees a live watch.
 *
 * The three biggest costs are the ones idle addresses: the PPG sensor, the gyroscope and
 * rotation vector (which fuses several sensors), and holding the Bluetooth radio up with ten
 * messages a second. Idle takes those from roughly 600 messages a minute to one, and the heart
 * rate sensor to whatever the OS was going to spend anyway.
 */
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

        /** Idle flush cadence. One snapshot each, so the phone still sees a live watch. The
         *  phone's "watch connected" indicator comes from the Data Layer node list, not from
         *  this, so it can be slow without the watch reading as gone. */
        private const val IDLE_BATCH_INTERVAL_MS = 60_000L

        /** How long the sensor hub may hold idle-profile samples before waking us with them. */
        private const val IDLE_SENSOR_LATENCY_MS = 60_000L

        /** Fallback heart rate duty cycle for watches without passive monitoring: measure this
         *  long ... */
        private const val IDLE_HR_WINDOW_MS = 12_000L

        /** ... once every this long. */
        private const val IDLE_HR_PERIOD_MS = 3 * 60 * 1000L

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
    private var policyJob: Job? = null
    @Volatile private var idleHeartRateJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    private val measureExecutor = Executors.newSingleThreadExecutor()
    private var measureClient: MeasureClient? = null
    /** Guarded by `synchronized(this)`. What is actually registered. */
    private var measureRegistered = false
    /** What the current profile wants registered. See [registerHeartRateMeasure]. */
    @Volatile private var measureWanted = false

    /** Null until the first capability check; cached so an idle cycle does not re-ask hourly. */
    private var healthServicesHasHeartRate: Boolean? = null

    private var passiveClient: PassiveMonitoringClient? = null
    /** Null until the first capability check. False also after a registration that failed. */
    private var passiveHasHeartRate: Boolean? = null
    @Volatile private var passiveRegistered = false

    @Volatile private var profile = PowerProfile.ACTIVE

    private val heartRateMeasureCallback = object : MeasureCallback {
        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
            Log.d(TAG, "HR availability changed: $availability")
            // If availability is not AVAILABLE, we might not get data points
        }

        override fun onDataReceived(data: DataPointContainer) {
            Log.v(TAG, "onDataReceived: Data received from Health Services")
            data.getData(DataType.HEART_RATE_BPM).lastOrNull()?.value?.let { bpm ->
                onHeartRate(bpm.toFloat(), "Health Services")
            } ?: Log.w(TAG, "onDataReceived: No HEART_RATE_BPM in container")
        }
    }

    /**
     * Idle heart rate. The system delivers whatever it measured, whenever it measured it, in
     * batches; only the newest reading matters here.
     */
    private val passiveHeartRateCallback = object : PassiveListenerCallback {
        override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
            dataPoints.getData(DataType.HEART_RATE_BPM).lastOrNull()?.value?.let { bpm ->
                if (bpm > 0) onHeartRate(bpm.toFloat(), "passive")
            }
        }

        override fun onRegistrationFailed(throwable: Throwable) {
            Log.w(TAG, "Passive heart rate registration failed, using duty cycle", throwable)
            passiveRegistered = false
            passiveHasHeartRate = false
            if (profile == PowerProfile.IDLE) {
                idleHeartRateJob?.cancel()
                idleHeartRateJob = serviceScope.launch { idleHeartRateCycle() }
            }
        }

        override fun onPermissionLost() {
            Log.w(TAG, "Passive heart rate permission lost")
            passiveRegistered = false
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
            applyProfile(WatchPowerPolicy.profile.value, force = true)
            startPolicyLoop()
            startBatchLoop()
        } else {
            Log.d(TAG, "Service already running, skipping sensor registration")
        }

        return START_STICKY
    }

    // ── Power profile ────────────────────────────────────────────────────────

    /**
     * Drives [WatchPowerPolicy.evaluate] at the moments it can matter.
     *
     * Idling is triggered by nothing happening, so something has to look at the clock. Rather
     * than polling every few seconds (which in idle is the only thing left waking the CPU),
     * sleep until the policy says the profile could next change, or until it kicks
     * [WatchPowerPolicy.ticks] because a deadline moved.
     */
    private fun startPolicyLoop() {
        policyJob = serviceScope.launch {
            launch {
                while (true) {
                    withTimeoutOrNull(WatchPowerPolicy.msUntilNextTransition()) {
                        WatchPowerPolicy.ticks.receive()
                    }
                    WatchPowerPolicy.evaluate()
                }
            }
            WatchPowerPolicy.profile.collect { applyProfile(it) }
        }
    }

    /** Swaps the sensor set, the heart rate strategy and the notification text. */
    private fun applyProfile(next: PowerProfile, force: Boolean = false) {
        if (!force && next == profile) return
        Log.i(TAG, "Applying power profile: $next")
        profile = next

        idleHeartRateJob?.cancel()
        idleHeartRateJob = null
        sensorManager.unregisterListener(this)
        stopHeartRateMeasure()
        stopPassiveHeartRate()

        when (next) {
            PowerProfile.ACTIVE -> {
                registerSensor(Sensor.TYPE_ACCELEROMETER, SensorManager.SENSOR_DELAY_GAME, "accelerometer")
                registerSensor(Sensor.TYPE_STEP_COUNTER, SensorManager.SENSOR_DELAY_NORMAL, "step_counter")
                registerSensor(Sensor.TYPE_GYROSCOPE, SensorManager.SENSOR_DELAY_GAME, "gyroscope")
                registerSensor(Sensor.TYPE_ROTATION_VECTOR, SensorManager.SENSOR_DELAY_GAME, "rotation_vector")
                registerSensor(Sensor.TYPE_PRESSURE, SensorManager.SENSOR_DELAY_NORMAL, "barometer")
                registerSensor(Sensor.TYPE_LIGHT, SensorManager.SENSOR_DELAY_NORMAL, "light")
                startHeartRate()
            }
            PowerProfile.IDLE -> {
                // Only the step counter, and even that batched: it is a hardware counter that
                // runs whether we listen or not, and letting the hub hold its events for a
                // minute means the application processor is not woken for each step. No
                // accelerometer: a few samples a second are still a few wakeups a second, and
                // the step counter already tells the phone whether the wrist is moving.
                registerSensor(
                    Sensor.TYPE_STEP_COUNTER, SensorManager.SENSOR_DELAY_NORMAL, "step_counter",
                    maxReportLatencyMs = IDLE_SENSOR_LATENCY_MS
                )
                idleHeartRateJob = serviceScope.launch { idleHeartRate() }
            }
        }
        updateNotification()
    }

    // ── Heart rate ───────────────────────────────────────────────────────────

    private fun onHeartRate(hr: Float, source: String) {
        Log.d(TAG, "$source HR: $hr bpm")
        latestHeartRate = hr
        _heartRateState.value = hr
        snapshotBuffer.add(buildSnapshot())
    }

    /**
     * Idle heart rate: passive monitoring if the watch has it, a duty cycle if not.
     *
     * Passive monitoring is Health Services' background API. The system decides when to light
     * the PPG, shares the reading with its own health tracking, and delivers it in batches; we
     * pay nothing on top. `MeasureClient`, which the active profile uses, is the foreground API
     * and keeps the sensor on for as long as it is registered, which is exactly what idle is
     * trying not to do.
     */
    private suspend fun idleHeartRate() {
        if (startPassiveHeartRate()) return
        idleHeartRateCycle()
    }

    /**
     * Heart rate on a duty cycle instead of continuously.
     *
     * Reads first and waits after, so dropping into idle still produces one reading promptly:
     * a watch that showed a heart rate a second ago should not appear dead for three minutes
     * just because nobody touched it.
     */
    private suspend fun idleHeartRateCycle() {
        while (true) {
            startHeartRate()
            delay(IDLE_HR_WINDOW_MS)
            stopHeartRateMeasure()
            sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)?.let {
                sensorManager.unregisterListener(this, it)
            }
            delay(IDLE_HR_PERIOD_MS - IDLE_HR_WINDOW_MS)
        }
    }

    /** True if passive heart rate is supported and the registration was submitted. */
    private fun startPassiveHeartRate(): Boolean {
        val client = passiveClient ?: HealthServices.getClient(this).passiveMonitoringClient.also {
            passiveClient = it
        }
        val supported = passiveHasHeartRate ?: try {
            val capabilities = client.getCapabilitiesAsync().get()
            val value = DataType.HEART_RATE_BPM in capabilities.supportedDataTypesPassiveMonitoring
            passiveHasHeartRate = value
            Log.d(TAG, "Heart rate supported via passive monitoring: $value")
            value
        } catch (e: Exception) {
            Log.e(TAG, "Passive monitoring capability check failed", e)
            passiveHasHeartRate = false
            false
        }
        if (!supported) return false
        // The profile may have flipped back to active during the capability check.
        if (profile != PowerProfile.IDLE) return true
        val config = PassiveListenerConfig.builder()
            .setDataTypes(setOf(DataType.HEART_RATE_BPM))
            .build()
        return try {
            client.setPassiveListenerCallback(config, measureExecutor, passiveHeartRateCallback)
            passiveRegistered = true
            Log.d(TAG, "Passive heart rate callback registered")
            // Same race as registerHeartRateMeasure: active may have arrived while we registered.
            if (profile != PowerProfile.IDLE) stopPassiveHeartRate()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Passive heart rate registration failed", e)
            passiveHasHeartRate = false
            false
        }
    }

    private fun stopPassiveHeartRate() {
        if (!passiveRegistered) return
        passiveRegistered = false
        try {
            passiveClient?.clearPassiveListenerCallbackAsync()
            Log.d(TAG, "Passive heart rate callback cleared")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear passive heart rate callback: ${e.message}")
        }
    }

    /**
     * Both heart rate paths at once.
     *
     * Health Services is the modern API, but some devices publish to the legacy HAL even when
     * MeasureClient claims support, so the legacy sensor is registered as well rather than only
     * as a fallback. The idle duty cycle keeps this pairing inside its short window, where the
     * double registration costs a few seconds rather than a day.
     */
    private fun startHeartRate() {
        registerHeartRateMeasure()
        registerSensor(Sensor.TYPE_HEART_RATE, SensorManager.SENSOR_DELAY_NORMAL, "heart_rate")
    }

    /**
     * Registration is asynchronous, so [measureWanted] records the intent separately from
     * [measureRegistered], which records the fact.
     *
     * Without that split, an idle window ending while the first capability check is still in
     * flight would unregister nothing and then register the callback a moment later, leaving a
     * continuous heart rate measurement running with nobody to turn it off. That is the exact
     * leak the idle profile exists to prevent, and it would be invisible: the watch would look
     * idle and drain like a session.
     */
    private fun registerHeartRateMeasure() {
        measureWanted = true
        val client = measureClient ?: HealthServices.getClient(this).measureClient.also {
            measureClient = it
        }
        serviceScope.launch {
            val supported = healthServicesHasHeartRate ?: try {
                val capabilities = client.getCapabilitiesAsync().get()
                val value = DataType.HEART_RATE_BPM in capabilities.supportedDataTypesMeasure
                healthServicesHasHeartRate = value
                Log.d(TAG, "Heart rate supported via Health Services: $value")
                value
            } catch (e: Exception) {
                Log.e(TAG, "Health Services capability check failed", e)
                return@launch
            }
            if (!supported) return@launch

            val register = synchronized(this@WatchDataService) {
                val go = measureWanted && !measureRegistered
                if (go) measureRegistered = true
                go
            }
            if (!register) return@launch
            try {
                client.registerMeasureCallback(DataType.HEART_RATE_BPM, measureExecutor, heartRateMeasureCallback)
                Log.d(TAG, "MeasureCallback registered")
            } catch (e: Exception) {
                Log.e(TAG, "MeasureCallback registration failed", e)
                synchronized(this@WatchDataService) { measureRegistered = false }
                return@launch
            }
            // The window may have closed while the capability check was in flight.
            if (!measureWanted) stopHeartRateMeasure()
        }
    }

    private fun stopHeartRateMeasure() {
        measureWanted = false
        val unregister = synchronized(this) {
            val go = measureRegistered
            measureRegistered = false
            go
        }
        if (!unregister) return
        try {
            measureClient?.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, heartRateMeasureCallback)
            Log.d(TAG, "MeasureCallback unregistered")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister HR measure callback: ${e.message}")
        }
    }

    // ── Sensors ──────────────────────────────────────────────────────────────

    /**
     * [maxReportLatencyMs] lets the sensor hub hold events in its FIFO and deliver them in one
     * go instead of waking the application processor per sample. The active profile leaves it
     * at zero: the ~50 Hz motion stream is fused from three sensors by "latest value" at
     * delivery time, and batching them independently would pair an accelerometer sample with a
     * gyroscope reading from up to a batch earlier.
     */
    private fun registerSensor(type: Int, delay: Int, label: String, maxReportLatencyMs: Long = 0L) {
        val sensor = sensorManager.getDefaultSensor(type)
        if (sensor == null) {
            Log.w(TAG, "Sensor missing: $label")
            return
        }
        val registered = if (maxReportLatencyMs > 0) {
            sensorManager.registerListener(this, sensor, delay, (maxReportLatencyMs * 1000).toInt())
        } else {
            sensorManager.registerListener(this, sensor, delay)
        }
        if (registered) {
            Log.d(TAG, "Sensor registered: $label (${sensor.name}), delay=$delay, latency=${maxReportLatencyMs}ms")
        } else {
            Log.w(TAG, "Failed to register sensor: $label (${sensor.name})")
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_HEART_RATE -> {
                val hr = event.values[0]
                if (hr > 0f) onHeartRate(hr, "SensorManager")
            }
            Sensor.TYPE_STEP_COUNTER -> latestStepCount = event.values[0].toInt()
            Sensor.TYPE_GYROSCOPE -> {
                latestGyroX = event.values[0]
                latestGyroY = event.values[1]
                latestGyroZ = event.values[2]
            }
            Sensor.TYPE_ROTATION_VECTOR -> {
                latestRotationX = if (event.values.isNotEmpty()) event.values[0] else 0f
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
                //
                // Idle has no accelerometer, but guard anyway: an event already queued when
                // the profile flipped would otherwise buffer a row nobody asked for.
                if (profile != PowerProfile.ACTIVE) return
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

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

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

    private fun startBatchLoop() {
        batchJob = serviceScope.launch {
            while (true) {
                // Read per iteration rather than per loop: a profile change has to take effect
                // on the next flush, not on the next restart of the service.
                delay(if (profile == PowerProfile.ACTIVE) BATCH_INTERVAL_MS else IDLE_BATCH_INTERVAL_MS)
                // Always include one current-state snapshot so the phone receives data
                // even when the user is still and no HR event fired this window.
                snapshotBuffer.add(buildSnapshot())
                val snapshots = snapshotBuffer.toList()
                snapshotBuffer.clear()
                val batch = WatchDataBatch(sessionId = sessionId, snapshots = snapshots)
                messageSender.sendWatchData(batch)
                Log.d(TAG, "Sent batch of ${snapshots.size} snapshots, HR=$latestHeartRate, profile=$profile")
            }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy called")
        _isRunning.value = false
        _heartRateState.value = 0f
        super.onDestroy()
        batchJob?.cancel()
        policyJob?.cancel()
        idleHeartRateJob?.cancel()
        stopHeartRateMeasure()
        stopPassiveHeartRate()
        measureExecutor.shutdown()
        sensorManager.unregisterListener(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_watch_data_name),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun updateNotification() {
        runCatching {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(
                getString(
                    if (profile == PowerProfile.IDLE) R.string.notif_watch_data_text_idle
                    else R.string.notif_watch_data_text
                )
            )
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .build()
}
