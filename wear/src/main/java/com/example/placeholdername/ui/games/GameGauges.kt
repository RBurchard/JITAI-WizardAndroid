package com.example.jitaicompanion.ui.games

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.Text
import com.example.jitaicompanion.R
import com.example.jitaicompanion.ui.layout.sdp
import com.example.jitaicompanion.ui.layout.ssp

/**
 * "How close am I to moving too much?", as a bar.
 *
 * Both wrist-steadiness games ([StandStillGameActivity] and lock picking's first stage) score the
 * player on the same smoothed accelerometer reading, and both used to give no warning at all
 * before the reading crossed the line — you learned you had moved only once the timer had
 * already reset. The bar shows the live reading against the limit, so the failure is visible a
 * second before it happens and can be corrected.
 *
 * @param level 0..1 current motion, already smoothed by the caller.
 * @param limit 0..1 point on the bar where the game counts it as moving.
 */
@Composable
fun SteadinessBar(
    level: Float,
    limit: Float,
    tripped: Boolean,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.game_steadiness_label),
) {
    val animated by animateFloatAsState(level.coerceIn(0f, 1f), label = "steadiness")
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 9.ssp, color = Color(0xFF90A4AE))
        Spacer(Modifier.height(2.sdp))
        Canvas(
            Modifier
                .width(84.sdp)
                .height(7.sdp)
        ) {
            val r = size.height / 2f
            val radius = CornerRadius(r, r)
            drawRoundRect(color = TRACK_DARK, cornerRadius = radius)
            // Safe band drawn as a lighter track, so the danger zone is legible even when the
            // fill is nowhere near it.
            drawRoundRect(
                color = TRACK_SAFE,
                size = Size(size.width * limit, size.height),
                cornerRadius = radius,
            )
            drawRoundRect(
                color = if (tripped) ALARM_RED else STEADY_GREEN,
                size = Size(size.width * animated.coerceAtLeast(0.02f), size.height),
                cornerRadius = radius,
            )
            drawLine(
                color = Color.White,
                start = Offset(size.width * limit, 0f),
                end = Offset(size.width * limit, size.height),
                strokeWidth = size.height * 0.28f,
            )
        }
    }
}

private val TRACK_DARK = Color(0xFF263238)
private val TRACK_SAFE = Color(0xFF37474F)
private val STEADY_GREEN = Color(0xFF66BB6A)
private val ALARM_RED = Color(0xFFFF5252)
