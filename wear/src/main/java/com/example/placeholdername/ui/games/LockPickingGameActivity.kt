package com.example.jitaicompanion.ui.games

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

class LockPickingGameActivity : MicrogameActivity(), SensorEventListener {

    override val timeoutSeconds = 60

    private lateinit var sensorManager: SensorManager
    private var currentMagnitude = 0f

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
        var stage by remember { mutableStateOf(1) }

        MaterialTheme {
            when (stage) {
                1 -> Stage1HoldStill(onComplete = { vibratePulse(); stage = 2 })
                2 -> Stage2DialSwipe(onComplete = { vibratePulse(); stage = 3 })
                3 -> Stage3TapTiming(onComplete = { vibratePulse(); onGameComplete("LockPick OK") })
            }
        }
    }

    @Composable
    private fun Stage1HoldStill(onComplete: () -> Unit) {
        var elapsed by remember { mutableStateOf(0f) }
        val target = 2f

        LaunchedEffect(Unit) {
            while (true) {
                delay(100L)
                if (currentMagnitude < 0.3f) {
                    elapsed += 0.1f
                    if (elapsed >= target) { onComplete(); return@LaunchedEffect }
                } else {
                    elapsed = 0f
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                val progress = elapsed / target
                val r = size.minDimension / 2f - 8.dp.toPx()
                drawArc(
                    color = Color.Cyan,
                    startAngle = -90f,
                    sweepAngle = progress * 360f,
                    useCenter = false,
                    style = Stroke(6.dp.toPx(), cap = StrokeCap.Round),
                    topLeft = Offset(center.x - r, center.y - r),
                    size = Size(r * 2, r * 2)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Stage 1/3", fontSize = 11.sp, color = Color.Gray)
                Spacer(Modifier.height(4.dp))
                Text("Hold still!", fontSize = 16.sp)
            }
        }
    }

    @Composable
    private fun Stage2DialSwipe(onComplete: () -> Unit) {
        var accumulatedAngle by remember { mutableStateOf(0f) }
        var lastAngle by remember { mutableStateOf<Float?>(null) }
        val targetRotation = 270f
        var completed by remember { mutableStateOf(false) }

        Box(modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val cx = size.width / 2f; val cy = size.height / 2f
                        lastAngle = atan2(offset.y - cy, offset.x - cx) * (180f / PI.toFloat())
                    },
                    onDrag = { change, _ ->
                        val cx = size.width / 2f; val cy = size.height / 2f
                        val angle = atan2(change.position.y - cy, change.position.x - cx) * (180f / PI.toFloat())
                        lastAngle?.let { prev ->
                            var delta = angle - prev
                            if (delta > 180f) delta -= 360f
                            if (delta < -180f) delta += 360f
                            if (!completed) {
                                accumulatedAngle += delta
                                if (accumulatedAngle >= targetRotation) {
                                    completed = true
                                    onComplete()
                                }
                            }
                        }
                        lastAngle = angle
                    },
                    onDragEnd = { lastAngle = null }
                )
            },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                val progress = (accumulatedAngle / targetRotation).coerceIn(0f, 1f)
                val r = size.minDimension / 2f - 8.dp.toPx()
                drawCircle(color = Color.DarkGray, radius = r, style = Stroke(6.dp.toPx()))
                drawArc(
                    color = Color.Yellow,
                    startAngle = -90f,
                    sweepAngle = progress * 360f,
                    useCenter = false,
                    style = Stroke(6.dp.toPx(), cap = StrokeCap.Round),
                    topLeft = Offset(center.x - r, center.y - r),
                    size = Size(r * 2, r * 2)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Stage 2/3", fontSize = 11.sp, color = Color.Gray)
                Spacer(Modifier.height(4.dp))
                Text("Swipe in a circle!", fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        }
    }

    @Composable
    private fun Stage3TapTiming(onComplete: () -> Unit) {
        var dotAngle by remember { mutableStateOf(-90f) }
        val targetStart = 30f
        val targetSweep = 60f
        var tapped by remember { mutableStateOf(false) }
        var hitResult by remember { mutableStateOf("") }
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            val revolution = 3000L
            val start = System.currentTimeMillis()
            while (!tapped) {
                delay(16L)
                val elapsed = (System.currentTimeMillis() - start) % revolution
                dotAngle = -90f + (elapsed / revolution.toFloat()) * 360f
            }
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                val r = size.minDimension / 2f - 8.dp.toPx()
                drawArc(
                    color = Color.Green.copy(alpha = 0.4f),
                    startAngle = targetStart - 90f,
                    sweepAngle = targetSweep,
                    useCenter = false,
                    style = Stroke(8.dp.toPx()),
                    topLeft = Offset(center.x - r, center.y - r),
                    size = Size(r * 2, r * 2)
                )
                val radians = dotAngle * PI.toFloat() / 180f
                val dotX = center.x + r * kotlin.math.cos(radians)
                val dotY = center.y + r * kotlin.math.sin(radians)
                drawCircle(color = Color.White, radius = 10.dp.toPx(), center = Offset(dotX, dotY))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Stage 3/3", fontSize = 11.sp, color = Color.Gray)
                Spacer(Modifier.height(4.dp))
                if (hitResult.isNotEmpty()) {
                    Text(hitResult, fontSize = 14.sp, color = if (hitResult == "Perfect!") Color.Green else Color.Red)
                } else {
                    Text("Tap in the green zone!", fontSize = 12.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        if (tapped) return@Button
                        var normalizedAngle = (dotAngle + 90f) % 360f
                        if (normalizedAngle < 0f) normalizedAngle += 360f
                        if (normalizedAngle >= targetStart && normalizedAngle <= targetStart + targetSweep) {
                            tapped = true
                            hitResult = "Perfect!"
                            onComplete()
                        } else {
                            hitResult = "Too early/late!"
                            scope.launch {
                                delay(1500L)
                                hitResult = ""
                            }
                        }
                    }) { Text("TAP!") }
                }
            }
        }
    }
}
