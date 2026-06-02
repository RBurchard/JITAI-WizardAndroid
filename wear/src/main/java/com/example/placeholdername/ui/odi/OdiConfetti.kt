package com.example.jitaicompanion.ui.odi

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val TAU = (2.0 * PI).toFloat()

private val confettiPalette = listOf(
    Color(0xFFEF5350), Color(0xFFFFCA28), Color(0xFF66BB6A),
    Color(0xFF42A5F5), Color(0xFFAB47BC), Color(0xFFFF7043), Color(0xFFFFFFFF)
)

/** A single fluttering confetti rectangle. Positions/velocities are in normalised
 *  units (0..1 of the canvas) so the effect adapts to any size it's placed in. */
private class Confetti(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    var rot: Float, val vrot: Float,
    var flap: Float, val flapSpeed: Float,
    val size: Float, val color: Color
)

private fun spawnPiece(burst: Boolean): Confetti {
    val color = confettiPalette.random()
    val pieceSize = 0.02f + Random.nextFloat() * 0.026f
    return if (burst) {
        // launched outward from ODI's chest, then gravity takes over
        val angle = Random.nextFloat() * TAU
        val speed = 0.5f + Random.nextFloat() * 0.8f
        Confetti(
            x = 0.5f, y = 0.42f,
            vx = cos(angle) * speed, vy = sin(angle) * speed - 0.35f,
            rot = Random.nextFloat() * 360f, vrot = (Random.nextFloat() - 0.5f) * 720f,
            flap = Random.nextFloat() * TAU, flapSpeed = 6f + Random.nextFloat() * 6f,
            size = pieceSize, color = color
        )
    } else {
        // gentle rain from above
        Confetti(
            x = Random.nextFloat(), y = -0.1f - Random.nextFloat() * 0.4f,
            vx = (Random.nextFloat() - 0.5f) * 0.25f, vy = 0.2f + Random.nextFloat() * 0.35f,
            rot = Random.nextFloat() * 360f, vrot = (Random.nextFloat() - 0.5f) * 540f,
            flap = Random.nextFloat() * TAU, flapSpeed = 5f + Random.nextFloat() * 6f,
            size = pieceSize, color = color
        )
    }
}

/**
 * A confetti shower that fills whatever box it's placed in. Drop it behind/around
 * ODI and flip [active] on to celebrate. Particles burst from the centre, then a
 * steady rain sustains while [active] stays true.
 */
@Composable
fun OdiConfetti(
    active: Boolean,
    modifier: Modifier = Modifier,
    pieces: Int = 48
) {
    val confetti = remember { mutableListOf<Confetti>() }
    var frame by remember { mutableStateOf(0L) }

    LaunchedEffect(active) {
        if (!active) {
            confetti.clear(); frame++
            return@LaunchedEffect
        }
        confetti.clear()
        repeat(pieces) { confetti.add(spawnPiece(burst = it % 3 == 0)) }
        var last = 0L
        while (isActive) {
            withFrameNanos { t ->
                val dt = if (last == 0L) 0f else ((t - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = t
                val itr = confetti.iterator()
                while (itr.hasNext()) {
                    val p = itr.next()
                    p.vy += 0.9f * dt          // gravity
                    p.x += p.vx * dt
                    p.y += p.vy * dt
                    p.rot += p.vrot * dt
                    p.flap += p.flapSpeed * dt
                    if (p.y > 1.2f) itr.remove()
                }
                while (confetti.size < pieces) confetti.add(spawnPiece(burst = false))
                frame = t
            }
        }
    }

    Canvas(modifier) {
        @Suppress("UNUSED_EXPRESSION") frame // read so the draw phase re-runs each frame
        val w = size.width
        val h = size.height
        confetti.forEach { p ->
            val cx = p.x * w
            val cy = p.y * h
            val pw = p.size * w
            val ph = pw * 0.55f * (0.35f + 0.65f * abs(sin(p.flap))) // flutter
            rotate(p.rot, pivot = Offset(cx, cy)) {
                drawRect(p.color, topLeft = Offset(cx - pw / 2f, cy - ph / 2f), size = Size(pw, ph))
            }
        }
    }
}
