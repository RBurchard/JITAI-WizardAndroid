package com.example.jitaicompanion.ui.games

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.delay
import kotlin.math.sqrt

class StandStillGameActivity : MicrogameActivity(), SensorEventListener {

    override val timeoutSeconds = 30
    override val tutorialTitle = "Stand Still"
    override val tutorialText =
        "Hold your arm completely still until the ring fills up. If you move, the timer resets and you start over!"

    private lateinit var sensorManager: SensorManager
    private var currentMagnitude = 0f
    private val motionThreshold = 0.5f
    private val targetSeconds = 10f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(SensorManager::class.java)
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
        currentMagnitude = (sqrt((x * x + y * y + z * z).toDouble()) - 9.8f).toFloat()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    @Composable
    override fun GameContent() {
        var elapsed by remember { mutableStateOf(0f) }
        val animatedProgress by animateFloatAsState(targetValue = (elapsed / targetSeconds).coerceIn(0f, 1f))
        var isMoving by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            while (true) {
                delay(100L)
                isMoving = currentMagnitude > motionThreshold
                if (isMoving) {
                    elapsed = 0f
                } else {
                    elapsed += 0.1f
                    if (elapsed >= targetSeconds) {
                        onGameComplete("StandStill OK")
                        return@LaunchedEffect
                    }
                }
            }
        }

        MaterialTheme {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    strokeWidth = 6.dp,
                    colors = androidx.wear.compose.material3.ProgressIndicatorDefaults.colors(
                        indicatorColor = if (isMoving) Color.Red else Color.Green
                    )
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isMoving) "Keep still!" else "Hold steady...",
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${(targetSeconds - elapsed).toInt()}s",
                        fontSize = 22.sp,
                        color = if (isMoving) Color.Red else Color.Green
                    )
                }
            }
        }
    }
}
