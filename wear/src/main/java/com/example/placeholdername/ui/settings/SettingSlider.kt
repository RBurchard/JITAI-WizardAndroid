package com.example.jitaicompanion.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.wear.compose.material3.Text
import com.example.jitaicompanion.ui.layout.sdp
import com.example.jitaicompanion.ui.layout.wearDimens
import kotlin.math.roundToInt

/**
 * A discrete slider sized for a watch.
 *
 * Wear's own slider components assume a full-screen picker; this is an inline row that fits in a
 * scrolling settings list. It accepts two gestures, because neither works well alone on a 1.2"
 * screen:
 *
 * - **Drag** along the track, the gesture people expect from a slider.
 * - **Tap** anywhere on the row, which is the only usable option when the knob is smaller than
 *   a fingertip.
 *
 * It deliberately does *not* take the crown: rotary events go to whichever node holds focus, so
 * a focusable slider would swallow the scroll gesture the settings list needs, and two sliders
 * on one screen would fight each other for it.
 *
 * Every step change buzzes, so the value can be set without watching the number.
 *
 * @param onChange called with the new value, already clamped to [min]..[max].
 */
@Composable
fun SettingSlider(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    valueLabel: String,
    modifier: Modifier = Modifier,
    onChange: (Int) -> Unit,
) {
    val dimens = wearDimens
    val context = LocalContext.current
    val haptics = remember(context) {
        context.getSystemService(android.os.Vibrator::class.java)
    }
    val steps = (max - min).coerceAtLeast(1)

    fun commit(newValue: Int) {
        val clamped = newValue.coerceIn(min, max)
        if (clamped != value) {
            onChange(clamped)
            runCatching {
                haptics?.vibrate(
                    android.os.VibrationEffect.createOneShot(18, android.os.VibrationEffect.DEFAULT_AMPLITUDE)
                )
            }
        }
    }

    fun valueAtX(x: Float, width: Float): Int =
        min + ((x / width).coerceIn(0f, 1f) * steps).roundToInt()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = dimens.captionTextSize, color = Color(0xFFB0BEC5))
            Text(
                valueLabel,
                fontSize = dimens.captionTextSize,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Spacer(Modifier.height(3.sdp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                // A 34 dp row for a 10 dp track: the track is the visual, the whole row is the
                // target. The gesture modifiers sit *above* the padding on purpose — below it
                // they would only cover the 10 dp track, which is under half the accessible
                // minimum and exactly the kind of near-miss this rework is meant to remove.
                .height(34.sdp)
                .pointerInput(min, max, value) {
                    detectTapGestures { offset ->
                        commit(valueAtX(offset.x, size.width.toFloat()))
                    }
                }
                .pointerInput(min, max, value) {
                    detectHorizontalDragGestures { change, _ ->
                        commit(valueAtX(change.position.x, size.width.toFloat()))
                    }
                }
                .padding(vertical = 12.sdp)
        ) {
            val h = size.height
            val radius = CornerRadius(h / 2f, h / 2f)
            val fraction = (value - min).toFloat() / steps
            drawRoundRect(color = TRACK, cornerRadius = radius)
            drawRoundRect(
                color = FILL,
                size = Size(size.width * fraction.coerceAtLeast(0.001f), h),
                cornerRadius = radius,
            )
            // Step pips, so the number of positions is visible before touching anything.
            repeat(steps + 1) { i ->
                val x = size.width * (i.toFloat() / steps)
                drawCircle(
                    color = Color.White.copy(alpha = 0.35f),
                    radius = h * 0.14f,
                    center = Offset(x.coerceIn(h * 0.2f, size.width - h * 0.2f), h / 2f),
                )
            }
            val knobX = (size.width * fraction).coerceIn(h * 0.6f, size.width - h * 0.6f)
            drawCircle(Color.White, radius = h * 0.62f, center = Offset(knobX, h / 2f))
            drawCircle(FILL, radius = h * 0.30f, center = Offset(knobX, h / 2f))
        }
    }
}

private val TRACK = Color(0xFF2B333D)
private val FILL = Color(0xFF42A5F5)
