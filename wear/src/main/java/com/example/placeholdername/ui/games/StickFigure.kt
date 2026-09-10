package com.example.jitaicompanion.ui.games

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * What the lock-picking burglar is doing right now.
 *
 * The figure is the game's whole feedback channel: rather than reading a number, the player sees
 * a thief who is either creeping steadily or wobbling on the edge of setting the alarm off.
 */
enum class StickPose {
    /** Stage 1 — mid-stride between two beams, arms out, one foot off the floor. */
    BALANCING,

    /** Stage 2 — advancing along the corridor; [drawStickFigure]'s `phase` drives the walk cycle. */
    WALKING,

    /** Stage 2 — clipped a beam. Slumped, arms limp, stars overhead. */
    STUNNED,

    /** Stage 2 — holding position between beams, standing still and upright. */
    WAITING,
}

/**
 * Draws a stick figure standing on [feet], [height] px tall, in [pose].
 *
 * @param phase 0..1 loop position, used for the walk cycle and the stun stars.
 * @param lean  -1..1 how far off balance the figure is. Rotates the whole body around the feet,
 *   so "about to fall over" is legible without reading any text — this is what carries the
 *   accelerometer reading in stage 1.
 */
fun DrawScope.drawStickFigure(
    feet: Offset,
    height: Float,
    color: Color,
    pose: StickPose,
    phase: Float = 0f,
    lean: Float = 0f,
    stroke: Float = height * 0.055f,
) {
    val hipY = feet.y - height * 0.42f
    val shoulderY = feet.y - height * 0.75f
    val headR = height * 0.13f
    val headCenter = Offset(feet.x, feet.y - height * 0.87f)
    val legLen = feet.y - hipY
    val armLen = height * 0.30f
    val lineStroke = Stroke(width = stroke, cap = StrokeCap.Round)

    fun limb(from: Offset, to: Offset) =
        drawLine(color, from, to, strokeWidth = stroke, cap = StrokeCap.Round)

    // The whole body pivots about the feet, so a lean reads as losing balance rather than
    // as the figure sliding sideways.
    withTransform({ rotate(lean * 16f, pivot = feet) }) {
        val hip = Offset(feet.x, hipY)
        val shoulder = Offset(feet.x, shoulderY)

        when (pose) {
            StickPose.BALANCING -> {
                // Arms straight out for balance, tilting opposite the lean like a tightrope pole.
                val armDrop = lean * armLen * 0.35f
                limb(shoulder, Offset(shoulder.x - armLen, shoulder.y + armDrop))
                limb(shoulder, Offset(shoulder.x + armLen, shoulder.y - armDrop))
                // Planted leg, then a raised trailing leg frozen mid-step.
                limb(hip, Offset(feet.x - legLen * 0.18f, feet.y))
                val knee = Offset(feet.x + legLen * 0.42f, hipY + legLen * 0.42f)
                limb(hip, knee)
                limb(knee, Offset(feet.x + legLen * 0.62f, hipY + legLen * 0.30f))
            }

            StickPose.WALKING -> {
                val swing = sin(phase * 2f * PI.toFloat())
                val lift = cos(phase * 2f * PI.toFloat())
                limb(shoulder, Offset(shoulder.x - armLen * 0.75f * swing, shoulderY + armLen * 0.8f))
                limb(shoulder, Offset(shoulder.x + armLen * 0.75f * swing, shoulderY + armLen * 0.8f))
                // Front leg lifts as it swings forward; back leg stays planted.
                limb(hip, Offset(feet.x + legLen * 0.45f * swing, feet.y - legLen * 0.18f * maxOf(lift, 0f)))
                limb(hip, Offset(feet.x - legLen * 0.45f * swing, feet.y - legLen * 0.18f * maxOf(-lift, 0f)))
            }

            StickPose.WAITING -> {
                limb(shoulder, Offset(shoulder.x - armLen * 0.45f, shoulderY + armLen * 0.85f))
                limb(shoulder, Offset(shoulder.x + armLen * 0.45f, shoulderY + armLen * 0.85f))
                limb(hip, Offset(feet.x - legLen * 0.18f, feet.y))
                limb(hip, Offset(feet.x + legLen * 0.18f, feet.y))
            }

            StickPose.STUNNED -> {
                // Slumped: arms hang, knees buckle outward.
                limb(shoulder, Offset(shoulder.x - armLen * 0.30f, shoulderY + armLen * 0.95f))
                limb(shoulder, Offset(shoulder.x + armLen * 0.30f, shoulderY + armLen * 0.95f))
                val kneeL = Offset(feet.x - legLen * 0.40f, hipY + legLen * 0.55f)
                val kneeR = Offset(feet.x + legLen * 0.40f, hipY + legLen * 0.55f)
                limb(hip, kneeL); limb(kneeL, Offset(feet.x - legLen * 0.30f, feet.y))
                limb(hip, kneeR); limb(kneeR, Offset(feet.x + legLen * 0.30f, feet.y))
            }
        }

        // Spine last so it sits over the limb joints.
        drawLine(color, shoulder, hip, strokeWidth = stroke, cap = StrokeCap.Round)

        // A stunned head lolls to one side; every other pose looks straight ahead.
        val headOffset = if (pose == StickPose.STUNNED) Offset(headR * 0.5f, headR * 0.25f) else Offset.Zero
        drawCircle(color, radius = headR, center = headCenter + headOffset, style = lineStroke)
    }

    if (pose == StickPose.STUNNED) {
        drawStunStars(headCenter, headR, phase, color)
    }
}

/** Three little stars orbiting a stunned head — the classic "seeing stars" cue. */
private fun DrawScope.drawStunStars(headCenter: Offset, headR: Float, phase: Float, color: Color) {
    val orbitR = headR * 1.7f
    repeat(3) { i ->
        val angle = (phase * 2f * PI.toFloat()) + i * (2f * PI.toFloat() / 3f)
        val center = Offset(
            headCenter.x + orbitR * cos(angle),
            headCenter.y - headR * 0.9f + orbitR * 0.42f * sin(angle)
        )
        drawPath(
            path = buildShapePath(GameShape.STAR, center, headR * 0.42f),
            color = color
        )
    }
}

/**
 * A horizontal laser beam across [bounds] at [y].
 *
 * [intensity] 0..1 fades the beam in and out so the player can see one arming before it is
 * lethal, instead of being hit by something that appeared the same frame.
 */
fun DrawScope.drawLaserBeam(
    bounds: Rect,
    y: Float,
    intensity: Float,
    thickness: Float,
    color: Color = Color(0xFFFF3B30),
) {
    val a = intensity.coerceIn(0f, 1f)
    if (a <= 0.01f) return
    // Wide soft halo under a hot core: a flat line reads as a UI divider, a glowing one reads
    // as something you must not touch.
    drawLine(
        color = color.copy(alpha = 0.22f * a),
        start = Offset(bounds.left, y),
        end = Offset(bounds.right, y),
        strokeWidth = thickness * 3.2f,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = color.copy(alpha = 0.85f * a),
        start = Offset(bounds.left, y),
        end = Offset(bounds.right, y),
        strokeWidth = thickness,
        cap = StrokeCap.Round,
    )
    // Emitter nubs, so the beam looks anchored to the walls of the corridor.
    val nub = thickness * 1.6f
    drawCircle(color.copy(alpha = a), nub, Offset(bounds.left, y))
    drawCircle(color.copy(alpha = a), nub, Offset(bounds.right, y))
}

/**
 * A vertical guillotine beam at [x], hanging from the top of [bounds] down to [reach] (0..1 of
 * the corridor height).
 *
 * [arming] 0..1 is the pre-fire telegraph: the emitter flashes and a thin tracer feels its way
 * down the corridor before the beam actually drops. A beam that fires the frame it appears can
 * only be memorised, not read — the warning is what makes the stage a reaction test.
 */
fun DrawScope.drawDropBeam(
    bounds: Rect,
    x: Float,
    reach: Float,
    thickness: Float,
    arming: Float = 1f,
    color: Color = Color(0xFFFF3B30),
) {
    val r = reach.coerceIn(0f, 1f)
    val arm = arming.coerceIn(0f, 1f)

    // Emitter housing: always present so the hazard's position is known before it charges,
    // brightening as it winds up.
    drawRect(
        color = lerp(Color(0xFF455A64), color, maxOf(arm, r)),
        topLeft = Offset(x - thickness * 1.7f, bounds.top - thickness * 1.2f),
        size = Size(thickness * 3.4f, thickness * 2.4f),
    )

    if (r <= 0.005f) {
        if (arm > 0.02f) {
            // Charging: a dashed tracer down the full drop, plus a swelling glow at the muzzle.
            // Dashes rather than a solid line, so a charging beam is never mistaken for a live
            // one at a glance.
            val dash = bounds.height / 14f
            var y = bounds.top
            while (y < bounds.bottom) {
                drawLine(
                    color = color.copy(alpha = 0.18f + 0.34f * arm),
                    start = Offset(x, y),
                    end = Offset(x, minOf(y + dash * 0.45f, bounds.bottom)),
                    strokeWidth = thickness * 0.5f,
                    cap = StrokeCap.Round,
                )
                y += dash
            }
            drawCircle(color.copy(alpha = arm), thickness * (0.7f + 0.8f * arm), Offset(x, bounds.top + thickness))
        }
        return
    }

    val bottom = bounds.top + bounds.height * r
    drawLine(
        color = color.copy(alpha = 0.20f),
        start = Offset(x, bounds.top),
        end = Offset(x, bottom),
        strokeWidth = thickness * 3.2f,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = color,
        start = Offset(x, bounds.top),
        end = Offset(x, bottom),
        strokeWidth = thickness,
        cap = StrokeCap.Round,
    )
    drawCircle(Color.White.copy(alpha = 0.85f), thickness * 0.9f, Offset(x, bottom))
}

/** Dashed floor line the figure walks on, so the corridor has a readable ground plane. */
fun DrawScope.drawFloor(bounds: Rect, y: Float, color: Color, stroke: Float) {
    val dash = bounds.width / 22f
    var x = bounds.left
    val path = Path()
    while (x < bounds.right) {
        path.moveTo(x, y)
        path.lineTo(minOf(x + dash * 0.6f, bounds.right), y)
        x += dash
    }
    drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
}
