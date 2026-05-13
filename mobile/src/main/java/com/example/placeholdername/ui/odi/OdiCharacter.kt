package com.BWPStudio.JITAIWizard.ui.odi

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.random.Random

private val odiBlue = Color(0xFF1565C0)
private val odiAccent = Color(0xFF42A5F5)
private val pupilColor = Color.White

@Composable
fun OdiCharacter(
    state: OdiAnimationState,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp
) {
    var blinkProgress by remember { mutableStateOf(1f) }
    val talkProgress by animateFloatAsState(
        targetValue = if (state == OdiAnimationState.TALKING) 1f else 0f,
        animationSpec = tween(300)
    )
    val thinkOffset by animateFloatAsState(
        targetValue = if (state == OdiAnimationState.THINKING) 0.15f else 0f,
        animationSpec = repeatable(
            iterations = RepeatMode.Reverse.ordinal,
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        )
    )
    val celebrateScale by animateFloatAsState(
        targetValue = if (state == OdiAnimationState.CELEBRATING) 1.1f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )

    // Random blink loop
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(3000, 5500))
            blinkProgress = 0f
            delay(120)
            blinkProgress = 1f
        }
    }

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val eyeRadius = w * 0.18f * celebrateScale
        val eyeY = h * 0.38f
        val leftEyeX = w * 0.30f
        val rightEyeX = w * 0.70f
        val openEyeRadius = eyeRadius * blinkProgress.coerceAtLeast(0.05f)

        drawOEye(leftEyeX, eyeY + thinkOffset * h, openEyeRadius)
        drawDEye(rightEyeX, eyeY + thinkOffset * h, openEyeRadius)
        drawCMouth(w * 0.5f, h * 0.80f, w * 0.22f, talkProgress, state == OdiAnimationState.CELEBRATING)
    }
}

private fun DrawScope.drawOEye(cx: Float, cy: Float, radius: Float) {
    drawCircle(color = odiBlue, radius = radius, center = Offset(cx, cy), style = Stroke(radius * 0.22f))
    drawCircle(color = pupilColor, radius = radius * 0.35f, center = Offset(cx, cy))
    drawCircle(color = odiAccent, radius = radius * 0.15f, center = Offset(cx - radius * 0.12f, cy - radius * 0.12f))
}

private fun DrawScope.drawDEye(cx: Float, cy: Float, radius: Float) {
    // D-shape: flat left side, curved right
    drawArc(
        color = odiBlue,
        startAngle = -90f,
        sweepAngle = 180f,
        useCenter = false,
        style = Stroke(radius * 0.22f, cap = StrokeCap.Butt),
        topLeft = Offset(cx - radius, cy - radius),
        size = Size(radius * 2, radius * 2)
    )
    // Flat line on left
    drawLine(color = odiBlue, start = Offset(cx, cy - radius), end = Offset(cx, cy + radius), strokeWidth = radius * 0.22f)
    drawCircle(color = pupilColor, radius = radius * 0.35f, center = Offset(cx + radius * 0.15f, cy))
    drawCircle(color = odiAccent, radius = radius * 0.15f, center = Offset(cx + radius * 0.05f, cy - radius * 0.12f))
}

private fun DrawScope.drawCMouth(cx: Float, cy: Float, radius: Float, talkProgress: Float, celebrating: Boolean) {
    val baseSweep = if (celebrating) 240f else 210f
    val sweep = baseSweep + talkProgress * 20f
    val startAngle = 45f - talkProgress * 5f
    val yOffset = if (celebrating) -radius * 0.12f else 0f
    drawArc(
        color = odiBlue,
        startAngle = startAngle,
        sweepAngle = sweep,
        useCenter = false,
        style = Stroke(radius * 0.18f, cap = StrokeCap.Round),
        topLeft = Offset(cx - radius, cy - radius + yOffset),
        size = Size(radius * 2, radius * 2)
    )
}
