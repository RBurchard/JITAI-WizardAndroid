package com.BWPStudio.JITAIWizard.ui.odi

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
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

    var irisX by remember { mutableStateOf(0f) }

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
    val irisOffsetX by animateFloatAsState(
        targetValue = irisX,
        animationSpec = tween(300)
    )

    // Random blink loop
    LaunchedEffect(Unit) {
        val positions = listOf(-0.55f, 0f, 0.55f) // down-left, straight-down, down-right
        while (true) {
            // pick a different discrete horizontal gaze position
            var next = positions.random()
            if (next == irisX) {
                // if it's the same, try to pick a different one (prefer change)
                next = positions.filter { it != irisX }.random()
            }
            irisX = next

            // shorter, more frequent changes while thinking
            val wait = if (state == OdiAnimationState.THINKING) Random.nextLong(1200, 2200) else Random.nextLong(3000, 5500)
            delay(wait)

            // blink
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

        drawOEye(
            leftEyeX,
            eyeY + thinkOffset * h,
            eyeRadius,
            openEyeRadius,
            irisX = irisOffsetX
        )
        drawDEye(
            rightEyeX,
            eyeY + thinkOffset * h,
            eyeRadius,
            openEyeRadius,
            irisX = irisOffsetX
        )
        drawONose(
            w * 0.50f,
            h * 0.75f,
            w * 0.13f
        )
        // Hands: expectant/observant, hovering under the UI box
        drawThreeFingerHand(
            cx = w * 0.28f,
            cy = h * 0.82f,
            handWidth = w * 0.20f,
            handHeight = h * 0.12f,
            isLeft = true,
            openness = 0.5f,
            tilt = -0.12f,
        )
        drawThreeFingerHand(
            cx = w * 0.72f,
            cy = h * 0.82f,
            handWidth = w * 0.20f,
            handHeight = h * 0.12f,
            isLeft = false,
            openness = 0.5f,
            tilt = 0.12f,
        )
    }
}

// Face is OCD

private fun DrawScope.drawOEye(
    cx: Float,
    cy: Float,
    radius: Float,
    blink: Float,
    irisX: Float
) {

    val eyeRadius = radius * 0.75f

    drawArc(
        color = odiBlue,
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        style = Stroke(radius * 0.22f, cap = StrokeCap.Butt),
        topLeft = Offset(cx - eyeRadius, cy - eyeRadius),
        size = Size(eyeRadius * 2, eyeRadius * 2)
    )
    val movement = radius * 0.30f
    val irisCenter = Offset(
        cx + irisX * movement,
        cy + radius * 0.12f
    )
    // Actual Eyes
    drawCircle(color = pupilColor, radius = blink * 0.35f, center = Offset(cx, cy))
    drawCircle(color = odiAccent, radius = blink * 0.15f, center = irisCenter)
}

// Complete change. Eyebrow archs, maybe hands too? Looks more invested in you
private fun DrawScope.drawDEye(
    cx: Float,
    cy: Float,
    radius: Float,
    blink: Float,
    irisX: Float
) {
    // D-shape: flat left side, curved right

    val eyeRadius = radius * 0.75f

    drawArc(
        color = odiBlue,
        startAngle = -90f,
        sweepAngle = 180f,
        useCenter = false,
        style = Stroke(radius * 0.22f, cap = StrokeCap.Butt),
        topLeft = Offset(cx - eyeRadius, cy - eyeRadius),
        size = Size(eyeRadius * 2, eyeRadius * 2)
    )
    drawLine(
        color = odiBlue,
        start = Offset(cx * 0.98f, cy - eyeRadius * 1.15f),
        end = Offset(cx * 0.98f, cy + eyeRadius * 1.15f),
        strokeWidth = radius * 0.22f,
    )
    val movement = radius * 0.30f
    val irisCenter = Offset(
        cx + irisX * movement,
        cy + radius * 0.12f
    )
    // Actual Eyes
    drawCircle(color = pupilColor, radius = blink * 0.35f, center = Offset(cx, cy))
    drawCircle(color = odiAccent, radius = blink * 0.15f, center = irisCenter)
}

// Right now no animation for emotions, first have to look more into it, later
private fun DrawScope.drawONose(cx: Float, cy: Float, radius: Float) {
    drawArc(
        color = odiBlue,
        startAngle = 25f,
        sweepAngle = 220f,
        useCenter = false,
        style = Stroke(radius * 0.3f, cap = StrokeCap.Round),
        topLeft = Offset(cx - radius * 0.75f, cy - radius * 2f),
        size = Size(radius * 2f, radius * 2f)
    )
}

// HANDS WE GOT HANDS WE THROW HANDS.
// Maybe like a little Keyboard or small button
private fun DrawScope.drawThreeFingerHand(
    cx: Float,
    cy: Float,
    handWidth: Float,
    handHeight: Float,
    isLeft: Boolean = true,
    openness: Float = 0.6f,
    tilt: Float = 0.18f,
    color: Color = odiBlue
) {
    val palmW = handWidth * 0.8f
    val palmH = handHeight * 0.55f
    val fingerW = handWidth * 0.22f
    val fingerH = handHeight * 0.9f
    val stroke = (handWidth.coerceAtLeast(handHeight) * 0.06f).coerceAtLeast(2f)
    val m = if (isLeft) -1f else 1f

    val fingerBaseX = floatArrayOf(-0.3f, 0f, 0.3f)
    for (i in 0 until 3) {
        val base = cx + (fingerBaseX[i] * palmW * 0.9f * m)
        val tipX = base + (m * openness * fingerW * (i - 1) * 0.45f)
        // Mirror vertically: fingers point downward from the palm
        val tipY = cy + palmH * 0.7f + fingerH * (0.95f + 0.05f * i) + tilt * handHeight * 0.12f

        val p = Path().apply {
            // start at lower palm edge instead of top
            moveTo(base, cy + palmH * 0.25f)
            quadraticTo(
                base + (m * 0.05f * palmW),
                cy + palmH * 0.6f + fingerH * 0.35f,
                tipX,
                tipY
            )
        }
        drawPath(p, color = color, style = Stroke(width = stroke * 0.7f, cap = StrokeCap.Round))
        drawCircle(color = color, radius = stroke * 0.75f, center = Offset(tipX, tipY))
    }

    val sepStroke = stroke * 0.5f
    for (i in 0 until 2) {
        val sx = cx + (fingerBaseX[i] * palmW * 0.9f * m) + (m * 0.08f * palmW)
        val sy = cy + palmH * 0.45f
        val ex = cx + (fingerBaseX[i + 1] * palmW * 0.9f * m) - (m * 0.08f * palmW)
        val ey = cy + palmH * 0.5f
        val sp = Path().apply {
            moveTo(sx, sy)
            quadraticTo((sx + ex) / 2f + m * 0.02f * palmW, (sy + ey) / 2f - 0.03f * palmH, ex, ey)
        }
        drawPath(sp, color = color.copy(alpha = 0.9f), style = Stroke(width = sepStroke, cap = StrokeCap.Round))
    }
}

// HANDS WE GOT HANDS

