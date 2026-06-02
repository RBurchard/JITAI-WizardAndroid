package com.BWPStudio.JITAIWizard.ui.odi

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val odiBlue = Color(0xFF1565C0)
private val odiAccent = Color(0xFF42A5F5)
private val odiHappy = Color(0xFF43C66A)     // warm green for joyful moods
private val odiConcern = Color(0xFFEF8E53)   // amber for worry
private val pupilColor = Color.White
private val mouthInner = Color(0xFF0A2540)   // dark inside of an open mouth
private val sparkleColor = Color(0xFFFFD54F) // gold twinkle

private const val TWO_PI = (2.0 * PI).toFloat()

/** A target description of ODI's face for a given mood. Fields are tweened between moods. */
private data class OdiExpression(
    val eyeOpen: Float,    // 0 = shut, 1 = fully open (vertical squash)
    val happyEyes: Float,  // 0 = round O / D eyes, 1 = upturned ^_^ arcs
    val eyeScale: Float,   // overall eye size multiplier
    val pupilScale: Float, // pupil size (small = surprised/alert)
    val mouthOpen: Float,  // 0 = closed curve, 1 = wide open
    val mouthMood: Float,  // -1 frown .. +1 big grin
    val mouthWidth: Float, // horizontal mouth size multiplier
    val tint: Color,       // accent colour bled into the face for the mood
    val sparkle: Float     // 0..1 twinkle intensity around the head
)

private fun expressionFor(state: OdiAnimationState): OdiExpression = when (state) {
    OdiAnimationState.IDLE ->
        OdiExpression(1f, 0f, 1f, 1f, 0f, 0.25f, 1f, odiAccent, 0f)
    OdiAnimationState.TALKING ->
        OdiExpression(1f, 0f, 1f, 1f, 0.45f, 0.3f, 1f, odiAccent, 0f)
    OdiAnimationState.THINKING ->
        OdiExpression(0.82f, 0f, 1f, 1f, 0f, -0.05f, 0.7f, odiAccent, 0f)
    OdiAnimationState.CELEBRATING ->
        OdiExpression(1f, 1f, 1.05f, 1f, 0.7f, 1f, 1.15f, odiHappy, 1f)
    OdiAnimationState.HAPPY ->
        OdiExpression(1f, 0.85f, 1f, 1f, 0.12f, 0.85f, 1.05f, odiHappy, 0.5f)
    OdiAnimationState.SURPRISED ->
        OdiExpression(1f, 0f, 1.35f, 0.55f, 0.6f, 0f, 0.55f, odiAccent, 0f)
    OdiAnimationState.SLEEPING ->
        OdiExpression(0.05f, 0f, 1f, 1f, 0.05f, 0.15f, 0.5f, odiBlue, 0f)
    OdiAnimationState.CONCERNED ->
        OdiExpression(0.95f, 0f, 1.05f, 1f, 0.08f, -0.7f, 0.85f, odiConcern, 0f)
}

@Composable
fun OdiCharacter(
    state: OdiAnimationState,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp
) {
    val target = remember(state) { expressionFor(state) }
    val moodSpec = tween<Float>(durationMillis = 380, easing = FastOutSlowInEasing)

    // Facial parameters smoothly tween whenever the mood changes.
    val eyeOpen = animateFloatAsState(target.eyeOpen, moodSpec, label = "eyeOpen")
    val happyEyes = animateFloatAsState(target.happyEyes, moodSpec, label = "happyEyes")
    val eyeScale = animateFloatAsState(target.eyeScale, moodSpec, label = "eyeScale")
    val pupilScale = animateFloatAsState(target.pupilScale, moodSpec, label = "pupilScale")
    val mouthOpen = animateFloatAsState(target.mouthOpen, moodSpec, label = "mouthOpen")
    val mouthMood = animateFloatAsState(target.mouthMood, moodSpec, label = "mouthMood")
    val mouthWidth = animateFloatAsState(target.mouthWidth, moodSpec, label = "mouthWidth")
    val sparkle = animateFloatAsState(target.sparkle, moodSpec, label = "sparkle")
    val pop = animateFloatAsState(
        targetValue = when (state) {
            OdiAnimationState.CELEBRATING -> 1.08f
            OdiAnimationState.HAPPY -> 1.03f
            OdiAnimationState.SURPRISED -> 1.06f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "pop"
    )

    // Continuous "alive" motion — read inside the draw lambda so only the draw
    // phase is invalidated each frame, never recomposition.
    val life = rememberInfiniteTransition(label = "odiLife")
    val breathePhase = life.animateFloat(0f, TWO_PI, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "breathe")
    val swayPhase = life.animateFloat(0f, TWO_PI, infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart), label = "sway")
    val bouncePhase = life.animateFloat(0f, TWO_PI, infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Restart), label = "bounce")
    val talkPhase = life.animateFloat(0f, TWO_PI, infiniteRepeatable(tween(260, easing = LinearEasing), RepeatMode.Restart), label = "talk")
    val twinklePhase = life.animateFloat(0f, TWO_PI, infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart), label = "twinkle")
    val driftPhase = life.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "drift")

    // Eyelid blinks (skipped while asleep — eyes are already shut).
    val blink = remember { Animatable(1f) }
    LaunchedEffect(state) {
        if (state == OdiAnimationState.SLEEPING) {
            blink.snapTo(1f); return@LaunchedEffect
        }
        while (isActive) {
            delay(Random.nextLong(2600, 5200))
            blink.animateTo(0.05f, tween(90)); blink.animateTo(1f, tween(130))
            if (Random.nextFloat() < 0.3f) { // occasional double blink
                delay(110)
                blink.animateTo(0.05f, tween(90)); blink.animateTo(1f, tween(130))
            }
        }
    }

    // Eyes wander to feel curious; they lock to a direction for some moods.
    val lookX = remember { Animatable(0f) }
    val lookY = remember { Animatable(0f) }
    LaunchedEffect(state) {
        while (isActive) {
            val (tx, ty) = when (state) {
                OdiAnimationState.THINKING -> -0.15f + Random.nextFloat() * 0.3f to -0.75f
                OdiAnimationState.CONCERNED -> (Random.nextFloat() - 0.5f) * 0.3f to 0.45f
                OdiAnimationState.SLEEPING, OdiAnimationState.SURPRISED -> 0f to 0f
                else -> (Random.nextFloat() - 0.5f) * 1.2f to (Random.nextFloat() - 0.5f) * 0.7f
            }
            launch { lookX.animateTo(tx, tween(600, easing = FastOutSlowInEasing)) }
            launch { lookY.animateTo(ty, tween(600, easing = FastOutSlowInEasing)) }
            delay(Random.nextLong(1100, 2900))
        }
    }

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f

        val bob = sin(breathePhase.value) * h * 0.012f
        val hop = if (state == OdiAnimationState.CELEBRATING) -abs(sin(bouncePhase.value)) * h * 0.05f else 0f
        val sway = sin(swayPhase.value) * 2.2f

        val baseEyeR = w * 0.17f * eyeScale.value
        val eyeY = h * 0.37f
        val leftEyeX = w * 0.30f
        val rightEyeX = w * 0.70f
        val openY = (eyeOpen.value * blink.value).coerceIn(0f, 1f)

        val tint = target.tint
        val eyeColor = lerp(odiBlue, tint, 0.35f)
        val look = Offset(lookX.value, lookY.value)

        // Mouth openness: TALKING modulates the resting open value into a flap.
        var mOpen = mouthOpen.value
        if (state == OdiAnimationState.TALKING) {
            mOpen *= 0.35f + 0.65f * (0.5f + 0.5f * sin(talkPhase.value))
        }

        withTransform({
            translate(0f, bob + hop)
            rotate(sway, pivot = Offset(cx, cy))
            scale(pop.value, pop.value, pivot = Offset(cx, cy))
        }) {
            drawOEye(Offset(leftEyeX, eyeY), baseEyeR, openY, look, happyEyes.value, pupilScale.value, eyeColor, tint)
            drawDEye(Offset(rightEyeX, eyeY), baseEyeR, openY, look, happyEyes.value, pupilScale.value, eyeColor, tint)
            drawMouth(Offset(cx, h * 0.74f), w * 0.22f, mOpen, mouthMood.value, mouthWidth.value, eyeColor)
        }

        // Mood-specific extras, drawn in screen space so they don't bob with the face.
        if (sparkle.value > 0.01f) {
            drawSparkles(Offset(cx, cy), w * 0.46f, twinklePhase.value, sparkle.value)
        }
        if (state == OdiAnimationState.THINKING) {
            drawThinkingDots(Offset(rightEyeX + w * 0.06f, eyeY - h * 0.16f), w, twinklePhase.value)
        }
        if (state == OdiAnimationState.SLEEPING) {
            drawSleepingZ(Offset(rightEyeX + w * 0.06f, eyeY - h * 0.05f), w, h, driftPhase.value)
        }
    }
}

/** Left "O" eye — a ring with a pupil, or an upturned arc when happy. */
private fun DrawScope.drawOEye(
    center: Offset, baseR: Float, openY: Float, look: Offset,
    happy: Float, pupilScale: Float, eyeColor: Color, accent: Color
) {
    val normal = (1f - happy).coerceIn(0f, 1f)
    val rx = baseR
    val ry = baseR * openY.coerceAtLeast(0.04f)

    if (normal > 0.01f) {
        if (openY < 0.16f) {
            drawClosedEye(center, rx, eyeColor.copy(alpha = normal))
        } else {
            drawOval(
                color = eyeColor.copy(alpha = normal),
                topLeft = Offset(center.x - rx, center.y - ry),
                size = Size(rx * 2, ry * 2),
                style = Stroke(rx * 0.22f)
            )
            drawPupil(center, ry, look, pupilScale, normal, accent)
        }
    }
    if (happy > 0.01f) drawHappyEye(center, rx, eyeColor.copy(alpha = happy))
}

/** Right "D" eye — flat on the left, curved on the right. */
private fun DrawScope.drawDEye(
    center: Offset, baseR: Float, openY: Float, look: Offset,
    happy: Float, pupilScale: Float, eyeColor: Color, accent: Color
) {
    val normal = (1f - happy).coerceIn(0f, 1f)
    val rx = baseR
    val ry = baseR * openY.coerceAtLeast(0.04f)

    if (normal > 0.01f) {
        if (openY < 0.16f) {
            drawClosedEye(center, rx, eyeColor.copy(alpha = normal))
        } else {
            val color = eyeColor.copy(alpha = normal)
            drawArc(
                color = color, startAngle = -90f, sweepAngle = 180f, useCenter = false,
                style = Stroke(rx * 0.22f, cap = StrokeCap.Butt),
                topLeft = Offset(center.x - rx, center.y - ry), size = Size(rx * 2, ry * 2)
            )
            drawLine(color, Offset(center.x, center.y - ry), Offset(center.x, center.y + ry), strokeWidth = rx * 0.22f)
            drawPupil(Offset(center.x + rx * 0.12f, center.y), ry, look, pupilScale, normal, accent)
        }
    }
    if (happy > 0.01f) drawHappyEye(center, rx, eyeColor.copy(alpha = happy))
}

private fun DrawScope.drawPupil(center: Offset, ry: Float, look: Offset, pupilScale: Float, alpha: Float, accent: Color) {
    val pupilR = ry * 0.4f * pupilScale
    val pos = Offset(center.x + look.x * ry * 0.5f, center.y + look.y * ry * 0.5f)
    drawCircle(pupilColor.copy(alpha = alpha), radius = pupilR, center = pos)
    drawCircle(accent.copy(alpha = alpha), radius = pupilR * 0.45f, center = Offset(pos.x - pupilR * 0.3f, pos.y - pupilR * 0.3f))
}

/** Closed, content eye: a gentle downward curve like a relaxed lid. */
private fun DrawScope.drawClosedEye(center: Offset, rx: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x - rx, center.y - rx * 0.08f)
        quadraticTo(center.x, center.y + rx * 0.5f, center.x + rx, center.y - rx * 0.08f)
    }
    drawPath(path, color, style = Stroke(rx * 0.2f, cap = StrokeCap.Round))
}

/** Happy squint: an upside-down U (^). */
private fun DrawScope.drawHappyEye(center: Offset, rx: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x - rx, center.y + rx * 0.2f)
        quadraticTo(center.x, center.y - rx * 0.7f, center.x + rx, center.y + rx * 0.2f)
    }
    drawPath(path, color, style = Stroke(rx * 0.24f, cap = StrokeCap.Round))
}

/** The "C" mouth — a curve that smiles, frowns, opens, or rounds with the mood. */
private fun DrawScope.drawMouth(center: Offset, radius: Float, open: Float, mood: Float, width: Float, color: Color) {
    val half = radius * width
    val stroke = radius * 0.18f
    if (open > 0.08f) {
        val mw = half * 1.6f
        val mh = (0.3f + open).coerceAtMost(1.3f) * radius
        val topLeft = Offset(center.x - mw / 2f, center.y - mh / 2f)
        val ovalSize = Size(mw, mh)
        drawOval(mouthInner, topLeft = topLeft, size = ovalSize)
        drawOval(color, topLeft = topLeft, size = ovalSize, style = Stroke(stroke))
    } else {
        val lift = mood * radius * 0.14f
        val bulge = (0.12f + abs(mood) * 0.5f) * radius
        val path = Path().apply {
            if (mood >= 0f) { // smile: corners up, centre dips down
                moveTo(center.x - half, center.y - lift)
                quadraticTo(center.x, center.y + bulge, center.x + half, center.y - lift)
            } else {          // frown: corners down, centre lifts up
                moveTo(center.x - half, center.y - lift)
                quadraticTo(center.x, center.y - bulge, center.x + half, center.y - lift)
            }
        }
        drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

/** Twinkling 4-point sparkles arranged around the head. */
private fun DrawScope.drawSparkles(center: Offset, spread: Float, phase: Float, intensity: Float) {
    val count = 6
    for (i in 0 until count) {
        val angle = (i / count.toFloat()) * TWO_PI + phase * 0.2f
        val twinkle = (0.5f + 0.5f * sin(phase + i * 1.7f)).coerceIn(0f, 1f)
        val s = spread * (0.18f + 0.12f * twinkle) * intensity
        val pos = Offset(center.x + cos(angle) * spread * 0.92f, center.y + sin(angle) * spread * 0.78f)
        val a = twinkle * intensity
        val c = sparkleColor.copy(alpha = a)
        val arm = s
        drawLine(c, Offset(pos.x - arm, pos.y), Offset(pos.x + arm, pos.y), strokeWidth = s * 0.18f, cap = StrokeCap.Round)
        drawLine(c, Offset(pos.x, pos.y - arm), Offset(pos.x, pos.y + arm), strokeWidth = s * 0.18f, cap = StrokeCap.Round)
        drawCircle(c, radius = s * 0.16f, center = pos)
    }
}

/** Three little dots bubbling up while ODI thinks. */
private fun DrawScope.drawThinkingDots(anchor: Offset, w: Float, phase: Float) {
    for (i in 0 until 3) {
        val t = (0.5f + 0.5f * sin(phase * 1.4f - i * 1.1f)).coerceIn(0f, 1f)
        val r = w * 0.018f * (0.6f + 0.8f * t)
        val pos = Offset(anchor.x + i * w * 0.05f, anchor.y - i * w * 0.03f)
        drawCircle(odiAccent.copy(alpha = 0.3f + 0.7f * t), radius = r, center = pos)
    }
}

/** Sleepy "z"s drifting up and away. */
private fun DrawScope.drawSleepingZ(anchor: Offset, w: Float, h: Float, drift: Float) {
    for (k in 0 until 2) {
        val prog = ((drift + k * 0.5f) % 1f)
        val pos = Offset(anchor.x + prog * w * 0.14f, anchor.y - prog * h * 0.26f)
        val s = w * 0.05f * (0.7f + prog * 0.7f)
        val color = odiAccent.copy(alpha = (1f - prog) * 0.9f)
        val sw = s * 0.16f
        drawLine(color, Offset(pos.x, pos.y), Offset(pos.x + s, pos.y), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color, Offset(pos.x + s, pos.y), Offset(pos.x, pos.y + s), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color, Offset(pos.x, pos.y + s), Offset(pos.x + s, pos.y + s), strokeWidth = sw, cap = StrokeCap.Round)
    }
}
