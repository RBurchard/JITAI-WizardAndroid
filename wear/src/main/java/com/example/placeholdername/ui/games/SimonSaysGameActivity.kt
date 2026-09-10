package com.example.jitaicompanion.ui.games

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.example.jitaicompanion.R
import com.example.jitaicompanion.ui.layout.sdp
import com.example.jitaicompanion.ui.layout.ssp
import com.example.jitaicompanion.ui.layout.wearDimens
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Simon Says, rebuilt around the whole watch face.
 *
 * Two things drove the rework:
 *
 * **Reach.** The old board was four 52 dp squares stacked in the middle of the screen — about
 * 12 % of the display, in the one region a thumb covers worst. Now each signal is a full quarter
 * of the circle, so the smallest tap target is roughly a third of the screen and scales with the
 * watch instead of being a fixed dp size.
 *
 * **Colour independence.** Every pad also carries a silhouette (see [GameShape]) that fills in
 * white when it lights, and its own vibration rhythm. The sequence can be read as
 * "heart, heart, square" or felt as "short, short, long", so neither colour vision nor sight of
 * the screen at the moment of the flash is required to play.
 */
class SimonSaysGameActivity : MicrogameActivity() {

    override val timeoutSeconds = 60
    override val tutorialTitleRes = R.string.game_simonsays_title
    override val tutorialTextRes = R.string.game_simonsays_tutorial

    /**
     * The four pads, in index order. Index order is also draw order and hit-test order, so
     * pad *i* is always the same colour, shape, quadrant and haptic rhythm.
     */
    private enum class Pad(
        val color: Color,
        val shape: GameShape,
        /** Canvas start angle of the quadrant (0° = 3 o'clock, clockwise). */
        val startAngle: Float,
        /** Distinct buzz rhythm, so the sequence is learnable without looking. */
        val haptic: LongArray,
    ) {
        TOP_LEFT(Color(0xFF2962FF), GameShape.STAR, 180f, longArrayOf(0, 70)),
        TOP_RIGHT(Color(0xFFE53935), GameShape.SQUARE, 270f, longArrayOf(0, 60, 80, 60)),
        BOTTOM_LEFT(Color(0xFFFDD835), GameShape.CIRCLE, 90f, longArrayOf(0, 210)),
        BOTTOM_RIGHT(Color(0xFF2E9E4F), GameShape.HEART, 0f, longArrayOf(0, 60, 80, 190)),
    }

    private enum class Phase { SHOW, INPUT, WRONG, DONE }

    /** Near-black used for every shape outline — readable on all four pad colours. */
    private val outlineColor = Color(0xFF10141A)

    /** Grout between the pads. Grey rather than black so it reads as a border on a black screen. */
    private val groutColor = Color(0xFF6B7686)

    @Composable
    override fun GameContent() {
        val difficulty = remember { GameSettings.getSimonDifficulty(this) }
        val totalRounds = remember { GameSettings.getSimonRounds(this) }
        val flashMs = remember(difficulty) { GameSettings.simonFlashMillis(difficulty) }
        val gapMs = remember(difficulty) { GameSettings.simonGapMillis(difficulty) }

        var round by remember { mutableIntStateOf(1) }
        var sequence by remember { mutableStateOf(listOf(Pad.entries.indices.random())) }
        var phase by remember { mutableStateOf(Phase.SHOW) }
        var playerInput by remember { mutableStateOf(listOf<Int>()) }
        var litIndex by remember { mutableIntStateOf(-1) }
        var shownCount by remember { mutableIntStateOf(0) }
        // Bumped on every wrong answer to re-run the show LaunchedEffect for the *same*
        // sequence: a mistake costs the round, never the rounds already cleared. Losing all
        // progress on one slip was the old design's other accessibility problem.
        var replayToken by remember { mutableIntStateOf(0) }
        // The progress panel's real on-screen size, measured rather than assumed. The dead zone
        // has to be exactly the panel: guess it too small and taps on the visible white panel
        // fire the pad behind it, which is what makes the four pads feel like different sizes.
        var panelSize by remember { mutableStateOf(IntSize.Zero) }

        LaunchedEffect(phase, round, replayToken) {
            if (phase != Phase.SHOW) return@LaunchedEffect
            playerInput = emptyList()
            shownCount = 0
            delay(700L)
            sequence.forEach { idx ->
                litIndex = idx
                vibratePattern(Pad.entries[idx].haptic)
                delay(flashMs)
                litIndex = -1
                shownCount++
                delay(gapMs)
            }
            phase = Phase.INPUT
        }

        val onPad: (Int) -> Unit = handler@{ idx ->
            if (phase != Phase.INPUT) return@handler
            val next = playerInput + idx
            vibratePulse()
            if (next != sequence.take(next.size)) {
                playerInput = next
                phase = Phase.WRONG
                return@handler
            }
            playerInput = next
            if (next.size == sequence.size) {
                if (round >= totalRounds) {
                    phase = Phase.DONE
                    onGameComplete("SimonSays OK")
                } else {
                    round++
                    sequence = sequence + Pad.entries.indices.random()
                    phase = Phase.SHOW
                }
            }
        }

        LaunchedEffect(phase) {
            if (phase != Phase.WRONG) return@LaunchedEffect
            vibratePattern(longArrayOf(0, 260))
            delay(1400L)
            replayToken++
            phase = Phase.SHOW
        }

        MaterialTheme {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PadBoard(
                    litIndex = litIndex,
                    enabled = phase == Phase.INPUT,
                    wrong = phase == Phase.WRONG,
                    deadZone = panelSize,
                    onPad = onPad,
                )
                CenterPanel(
                    phase = phase,
                    round = round,
                    totalRounds = totalRounds,
                    steps = sequence.size,
                    filled = if (phase == Phase.SHOW) shownCount else playerInput.size,
                    litIndex = litIndex,
                    onPanelMeasured = { panelSize = it },
                )
            }
        }
    }

    /** The four full-quadrant pads plus their shape glyphs. */
    @Composable
    private fun PadBoard(
        litIndex: Int,
        enabled: Boolean,
        wrong: Boolean,
        deadZone: IntSize,
        onPad: (Int) -> Unit,
    ) {
        // Fades the whole board red on a mistake, which reads instantly even in peripheral
        // vision — and, unlike a red *pad*, cannot be confused with a pad lighting up.
        val wrongFade by animateFloatAsState(
            targetValue = if (wrong) 1f else 0f,
            animationSpec = tween(220),
            label = "wrongFade",
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(enabled, deadZone) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset ->
                        padAt(offset, size.width.toFloat(), size.height.toFloat(), deadZone)
                            ?.let(onPad)
                    }
                }
        ) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val inset = radius * 0.015f
            val r = radius - inset
            val arcTopLeft = Offset(center.x - r, center.y - r)
            val arcSize = Size(r * 2, r * 2)

            Pad.entries.forEachIndexed { idx, pad ->
                val lit = idx == litIndex
                // Sectors meet edge to edge at a full 90°. The separation is drawn afterwards as
                // straight grout lines instead of an angular gap: a gap in degrees is hairline
                // at the centre and wide at the rim, and it leaves the wedges' own antialiased
                // edges on show.
                drawArc(
                    // Idle pads stay bright enough for their outlined glyph to be readable:
                    // the shape is a permanent reference the player matches the flash against,
                    // so it cannot be allowed to sink into the background between flashes.
                    color = if (lit) pad.color else pad.color.copy(alpha = 0.58f),
                    startAngle = pad.startAngle,
                    sweepAngle = 90f,
                    useCenter = true,
                    topLeft = arcTopLeft,
                    size = arcSize,
                )
                if (lit) {
                    // A bright rim on the lit pad, for anyone who cannot see the colour change.
                    drawArc(
                        color = Color.White,
                        startAngle = pad.startAngle,
                        sweepAngle = 90f,
                        useCenter = true,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = radius * 0.045f),
                    )
                }
                drawPadShape(pad, center, radius, lit)
            }

            // Grout: two straight lines through the centre plus a rim, covering every seam at a
            // constant width and giving the board a finished edge on a round display.
            val grout = radius * 0.030f
            drawLine(
                color = groutColor,
                start = Offset(center.x, center.y - r),
                end = Offset(center.x, center.y + r),
                strokeWidth = grout,
            )
            drawLine(
                color = groutColor,
                start = Offset(center.x - r, center.y),
                end = Offset(center.x + r, center.y),
                strokeWidth = grout,
            )
            drawCircle(groutColor, radius = r - grout / 2f, center = center, style = Stroke(grout))

            if (wrongFade > 0.01f) {
                drawCircle(
                    color = Color(0xFFFF1744).copy(alpha = 0.34f * wrongFade),
                    radius = radius,
                    center = center,
                )
            }
        }
    }

    /**
     * Draws one pad's glyph: always outlined in near-black, filled white only while lit.
     *
     * The fill is the colour-independent signal — "the heart filled in" is exactly as visible
     * to a deuteranope as to anyone else, whereas a red pad brightening is not.
     */
    private fun DrawScope.drawPadShape(pad: Pad, center: Offset, radius: Float, lit: Boolean) {
        // Mid-angle of the quadrant, pushed out to the sector's optical centre.
        val midAngle = (pad.startAngle + 45f) * PI.toFloat() / 180f
        val distance = radius * 0.55f
        val glyphCenter = Offset(
            center.x + distance * cos(midAngle),
            center.y + distance * sin(midAngle),
        )
        val glyphRadius = radius * 0.17f
        val path = buildShapePath(pad.shape, glyphCenter, glyphRadius)
        if (lit) drawPath(path, Color.White)
        drawPath(
            path = path,
            color = outlineColor,
            style = Stroke(width = radius * (if (lit) 0.028f else 0.034f)),
        )
    }

    /**
     * The progress panel across the middle of the board.
     *
     * One dot per step in the current sequence, filling left to right — during the show phase it
     * counts the flashes as they play, during input it counts the taps. That answers the two
     * questions the old status line could not: how long is this sequence, and how far in am I?
     */
    @Composable
    private fun CenterPanel(
        phase: Phase,
        round: Int,
        totalRounds: Int,
        steps: Int,
        filled: Int,
        litIndex: Int,
        onPanelMeasured: (IntSize) -> Unit,
    ) {
        val dimens = wearDimens
        val accent = when (phase) {
            Phase.WRONG -> Color(0xFFD32F2F)
            Phase.INPUT -> Color(0xFF1565C0)
            else -> Color(0xFFFF8F00)
        }
        // Dots shrink as the sequence grows so the panel never has to widen past the round
        // screen's safe width.
        val dotSize = when {
            steps <= 4 -> 13.dp
            steps <= 6 -> 11.dp
            else -> 9.dp
        }

        Box(
            modifier = Modifier
                .fillMaxWidth(0.64f)
                .onSizeChanged(onPanelMeasured)
                .background(Color(0xFFF7F7F7), RoundedCornerShape(10.sdp))
                .padding(horizontal = 6.sdp, vertical = 5.sdp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = when (phase) {
                        Phase.SHOW -> stringRes(R.string.game_simonsays_watch_carefully)
                        Phase.INPUT -> stringRes(R.string.game_simonsays_your_turn_round, round, totalRounds)
                        Phase.WRONG -> stringRes(R.string.game_simonsays_wrong_retry)
                        Phase.DONE -> stringRes(R.string.game_result_perfect)
                    },
                    color = if (phase == Phase.WRONG) Color(0xFFB71C1C) else Color(0xFF20242B),
                    fontSize = 11.ssp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                Spacer(Modifier.height(3.sdp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.sdp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(steps) { i ->
                        val on = i < filled
                        Box(
                            modifier = Modifier
                                .size(dimens.scaled(dotSize))
                                .background(
                                    color = if (on) accent else Color(0xFFD8D8D8),
                                    shape = CircleShape,
                                )
                        )
                    }
                }
            }
        }

        // Screen-reader / low-vision hint: name the shape being flashed, since a glyph on a
        // coloured wedge is the one thing a magnifier user may not catch in 300 ms.
        if (phase == Phase.SHOW && litIndex >= 0) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Text(
                    text = shapeLabel(Pad.entries[litIndex].shape),
                    color = Color.White,
                    fontSize = 11.ssp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(bottom = 6.sdp)
                        .background(Color(0xCC000000), RoundedCornerShape(8.sdp))
                        .padding(horizontal = 8.sdp, vertical = 2.sdp),
                )
            }
        }
    }

    private fun shapeLabel(shape: GameShape): String = getString(
        when (shape) {
            GameShape.STAR -> R.string.game_shape_star
            GameShape.SQUARE -> R.string.game_shape_square
            GameShape.CIRCLE -> R.string.game_shape_circle
            GameShape.HEART -> R.string.game_shape_heart
        }
    )

    /** `getString` under a Compose-friendly name; these are called from non-composable lambdas too. */
    private fun stringRes(id: Int, vararg args: Any): String =
        if (args.isEmpty()) getString(id) else getString(id, *args)

    /**
     * Maps a tap to a pad, or `null` for the centre panel.
     *
     * Taps under the progress panel are swallowed rather than scored: a stray touch on a
     * non-interactive element should never cost the player a round.
     */
    private fun padAt(offset: Offset, width: Float, height: Float, deadZone: IntSize): Int? {
        val dx = offset.x - width / 2f
        val dy = offset.y - height / 2f
        // Rectangular and taken from the panel's measured bounds, so the four pads are exactly
        // the four quadrants minus one shared, centred rectangle — equal in area to the pixel.
        if (deadZone != IntSize.Zero &&
            kotlin.math.abs(dx) < deadZone.width / 2f &&
            kotlin.math.abs(dy) < deadZone.height / 2f
        ) return null
        return when {
            dx < 0 && dy < 0 -> Pad.TOP_LEFT.ordinal
            dx >= 0 && dy < 0 -> Pad.TOP_RIGHT.ordinal
            dx < 0 -> Pad.BOTTOM_LEFT.ordinal
            else -> Pad.BOTTOM_RIGHT.ordinal
        }
    }
}
