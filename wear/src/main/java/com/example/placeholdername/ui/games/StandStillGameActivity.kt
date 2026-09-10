package com.example.jitaicompanion.ui.games

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.example.jitaicompanion.R
import com.example.jitaicompanion.ui.layout.sdp
import com.example.jitaicompanion.ui.layout.ssp
import com.example.jitaicompanion.ui.layout.wearDimens
import com.example.jitaicompanion.ui.odi.OdiAnimationState
import com.example.jitaicompanion.ui.odi.OdiCharacter
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Hold the wrist still for ten seconds.
 *
 * The measurement is unchanged from the original — the same smoothed accelerometer magnitude
 * against the same threshold — because it is what the study records. What changed is that the
 * player can now *see it coming*: ODI reacts and sways with the live reading, and a
 * [SteadinessBar] shows how much headroom is left before the timer resets. Previously the only
 * feedback was the reset itself, after the fact.
 */
class StandStillGameActivity : MicrogameActivity(), SensorEventListener {

    override val timeoutSeconds = 30
    override val tutorialTitleRes = R.string.game_standstill_title
    override val tutorialTextRes = R.string.game_standstill_tutorial

    private lateinit var sensorManager: SensorManager
    private var currentMagnitude = 0f

    private companion object {
        /** Movement counted as "moving" — unchanged from the original scoring. */
        const val MOTION_THRESHOLD = 0.5f

        /** Reading treated as the top of the meter. */
        const val MOTION_CEILING = 1.2f

        /** Threshold as a fraction of the meter, so the bar's marker sits on the real limit. */
        const val LIMIT_FRACTION = MOTION_THRESHOLD / MOTION_CEILING

        const val TARGET_SECONDS = 10f
    }

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
        var elapsed by remember { mutableFloatStateOf(0f) }
        var motion by remember { mutableFloatStateOf(0f) }
        var sway by remember { mutableFloatStateOf(0f) }

        val isMoving = motion > LIMIT_FRACTION
        val animatedProgress by animateFloatAsState(
            targetValue = (elapsed / TARGET_SECONDS).coerceIn(0f, 1f),
            label = "progress",
        )

        LaunchedEffect(Unit) {
            val stepMs = 50L
            var phase = 0f
            while (true) {
                delay(stepMs)
                val raw = (abs(currentMagnitude) / MOTION_CEILING).coerceIn(0f, 1f)
                // Same 100 ms decision cadence as before, just sampled twice as often and
                // low-pass filtered so the bar does not strobe on sensor noise.
                motion += (raw - motion) * 0.25f
                phase = (phase + stepMs / 700f) % 1f
                sway = sin(phase * 2f * PI.toFloat()) * motion

                if (motion > LIMIT_FRACTION) {
                    elapsed = 0f
                } else {
                    elapsed += stepMs / 1000f
                    if (elapsed >= TARGET_SECONDS) {
                        onGameComplete("StandStill OK")
                        return@LaunchedEffect
                    }
                }
            }
        }

        MaterialTheme {
            val dimens = wearDimens
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                    val stroke = size.minDimension * 0.028f
                    val r = size.minDimension / 2f - stroke / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val topLeft = Offset(center.x - r, center.y - r)
                    val arcSize = Size(r * 2, r * 2)
                    drawArc(
                        color = Color.White.copy(alpha = 0.10f),
                        startAngle = 0f, sweepAngle = 360f, useCenter = false,
                        style = Stroke(stroke), topLeft = topLeft, size = arcSize,
                    )
                    drawArc(
                        color = if (isMoving) Color(0xFFFF5252) else Color(0xFF66BB6A),
                        startAngle = -90f,
                        sweepAngle = animatedProgress * 360f,
                        useCenter = false,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                        topLeft = topLeft, size = arcSize,
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // ODI tips with the wrist. The rotation is the reading itself, so a wobble
                    // is visible in the character before the bar's marker is reached.
                    OdiCharacter(
                        state = if (isMoving) OdiAnimationState.SURPRISED else OdiAnimationState.THINKING,
                        size = dimens.scaled(74.dp),
                        modifier = Modifier.graphicsLayer { rotationZ = sway * 14f },
                    )
                    Spacer(Modifier.height(2.sdp))
                    Text(
                        text = stringResource(
                            if (isMoving) R.string.game_standstill_keep_still
                            else R.string.game_standstill_hold_steady
                        ),
                        fontSize = 12.ssp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(
                            R.string.game_standstill_seconds_remaining,
                            (TARGET_SECONDS - elapsed).toInt().coerceAtLeast(0)
                        ),
                        fontSize = 26.ssp,
                        fontWeight = FontWeight.Bold,
                        color = if (isMoving) Color(0xFFFF5252) else Color(0xFF66BB6A),
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = dimens.verticalPadding + 6.sdp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    SteadinessBar(level = motion, limit = LIMIT_FRACTION, tripped = isMoving)
                }
            }
        }
    }
}
