package com.example.jitaicompanion.ui.games

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The four Simon Says signals.
 *
 * Each pad carries a **colour and a distinct silhouette**. Colour alone excludes anyone with a
 * colour-vision deficiency — red/green in particular are the two pads a deuteranope is least
 * able to separate, and they sit diagonally opposite each other here. With a shape on every pad
 * the sequence can be memorised as "heart, heart, square" and played back with no colour
 * perception at all.
 *
 * Silhouettes were chosen to stay distinguishable at a glance and in peripheral vision: a
 * pointed star, a flat-sided square, a round circle and a lobed heart differ in outline even
 * when small or blurred.
 */
enum class GameShape { STAR, SQUARE, CIRCLE, HEART }

/**
 * Builds [shape] as a [Path] centred on [center] and sized to fit a box of `2 * radius`.
 *
 * Returned as a Path (rather than drawn directly) so a caller can both fill and stroke it in
 * one place, and reuse it for the "outline when idle / filled when lit" treatment the pads use.
 */
fun buildShapePath(shape: GameShape, center: Offset, radius: Float): Path = Path().apply {
    when (shape) {
        GameShape.CIRCLE -> addOval(
            Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius)
        )

        GameShape.SQUARE -> {
            // Slightly inset: a square inscribed in the same radius as a circle reads much
            // larger than one, so pull it in to balance the four pads optically.
            val half = radius * 0.82f
            addRect(Rect(center.x - half, center.y - half, center.x + half, center.y + half))
        }

        GameShape.STAR -> {
            val outer = radius
            val inner = radius * 0.46f
            // Start at -90° so a point faces straight up — an upright star is what reads as
            // "star"; a rotated one reads as a blob.
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) outer else inner
                val angle = (-PI / 2 + i * PI / 5).toFloat()
                val x = center.x + r * cos(angle)
                val y = center.y + r * sin(angle)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }

        GameShape.HEART -> {
            // Two top lobes into a bottom point, drawn with cubics in units of `radius` so the
            // heart scales with the pad instead of being tuned per screen size.
            val w = radius * 1.05f
            val h = radius * 1.0f
            val cx = center.x
            val cy = center.y + h * 0.12f
            moveTo(cx, cy + h * 0.85f)
            cubicTo(
                cx - w * 1.25f, cy + h * 0.05f,
                cx - w * 0.55f, cy - h * 1.15f,
                cx, cy - h * 0.35f
            )
            cubicTo(
                cx + w * 0.55f, cy - h * 1.15f,
                cx + w * 1.25f, cy + h * 0.05f,
                cx, cy + h * 0.85f
            )
            close()
        }
    }
}
