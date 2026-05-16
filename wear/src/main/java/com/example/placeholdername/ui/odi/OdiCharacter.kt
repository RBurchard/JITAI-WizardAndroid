package com.example.jitaicompanion.ui.odi

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.repeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.random.Random

enum class OdiAnimationState {
    IDLE,
    TALKING,
    THINKING,
    CELEBRATING
}

// Adding Color
private val odiBlue = Color(0xFF1565C0)
private val odiAccent = Color(0xFF42A5F5)
private val pupilColor = Color.White

@Composable
fun OdiCharacter(
    state: OdiAnimationState,
    modifier: Modifier = Modifier,
    size: Dp = 140.dp
) {

    // Eye animation, 1f open, 0f closed

    var blinkProgress by remember { mutableStateOf(1f) }

    var irisX by remember { mutableStateOf(0f) }
    var irisY by remember { mutableStateOf(0f) }

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

    val irisOffsetX by animateFloatAsState(
        targetValue = irisX,
        animationSpec = tween(300)
    )
    val irisOffsetY by animateFloatAsState(
        targetValue = irisY,
        animationSpec = tween(300)
    )

    // occilate between eye every 3 - 5 sec)
    LaunchedEffect(Unit) {
        while (true) {
            // Eye test
            val nextIrisX = Random.nextFloat() * 2f - 1f
            val nextIrisY = Random.nextFloat() * 2f - 1f
            if (nextIrisX != irisX || nextIrisY != irisY) {
                irisX = nextIrisX
                irisY = nextIrisY
            }

            delay(Random.nextLong(3000, 5500))

            // Blinking
            blinkProgress = 0f
            delay(120)
            blinkProgress = 1f
        }
    }

    // Drawing

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val eyeRadius = w * 0.18f * celebrateScale

        // Eye positions
        val eyeY = h * 0.34f
        val leftEyeX = w * 0.30f
        val rightEyeX = w * 0.70f
        val openEyeRadius = eyeRadius * blinkProgress.coerceAtLeast(0.05f)

        // O and D eye (maybe change later?)
        //drawOEye(leftEyeX + irisOffsetX, eyeY + thinkOffset * h + irisOffsetY, openEyeRadius)
        //drawDEye(rightEyeX + irisOffsetX, eyeY + thinkOffset * h, openEyeRadius + irisOffsetY)
        drawOEye(
                leftEyeX,
                eyeY + thinkOffset * h,
                openEyeRadius,
                irisOffsetX,
                irisOffsetY
                )
        drawDEye(
                rightEyeX,
                eyeY + thinkOffset * h,
                openEyeRadius,
                irisOffsetX,
                irisOffsetY
                )

        // Small C-shaped nose in the middle
        drawCNose(w * 0.50f, h * 0.75f, w * 0.12f)
    }
}
// Here is where the function to draw happens
private fun DrawScope.drawOEye(cx: Float, cy: Float, radius: Float, irisX: Float, irisY: Float) {
    drawCircle(color = odiBlue, radius = radius * 0.75f, center = Offset(cx, cy), style = Stroke(radius * 0.22f))
    val movement = radius * 0.30f
    val irisCenter = Offset(
        cx + irisX * movement,
        cy + irisY * movement
    )
    drawCircle(color = pupilColor, radius = radius * 0.35f, center = Offset(cx, cy))
    drawCircle(color = odiAccent, radius = radius * 0.15f, center = irisCenter)
}

private fun DrawScope.drawDEye(
    cx: Float,
    cy: Float,
    radius: Float,
    irisX: Float,
    irisY: Float
) {
    val eyeRadius = radius * 0.75f
    val stroke = radius * 0.22f

    drawArc(
        color = odiBlue,
        startAngle = -90f,
        sweepAngle = 180f,
        useCenter = false,
        style = Stroke(stroke, cap = StrokeCap.Butt),
        topLeft = Offset(cx - eyeRadius, cy - eyeRadius),
        size = Size(eyeRadius * 2f, eyeRadius * 2f)
    )

    drawLine(
        color = odiBlue,
        start = Offset(cx, cy - eyeRadius),
        end = Offset(cx, cy + eyeRadius),
        strokeWidth = stroke
    )

    val movement = radius * 0.30f
    val irisCenter = Offset(
        cx + irisX * movement,
        cy + irisY * movement
    )

    // Actual Eye
    drawCircle(
        color = pupilColor,
        radius = radius * 0.35f,
        center = Offset(cx, cy)
    )

    drawCircle(
        color = odiAccent,
        radius = radius * 0.15f,
        center = irisCenter
    )
}

private fun DrawScope.drawCNose(cx: Float, cy: Float, radius: Float) {
    drawArc(
        color = odiBlue,
        startAngle = 42f,
        sweepAngle = 205f,
        useCenter = false,
        style = Stroke(radius * 0.3f, cap = StrokeCap.Round),
        topLeft = Offset(cx - radius, cy - radius * 2f),
        size = Size(radius * 2f, radius * 2f)
    )
}


