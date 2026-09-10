package com.example.jitaicompanion.ui.games

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.example.jitaicompanion.R
import com.example.jitaicompanion.convention.models.LockPenalty
import com.example.jitaicompanion.ui.layout.sdp
import com.example.jitaicompanion.ui.layout.ssp
import com.example.jitaicompanion.ui.layout.wearDimens
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A three-stage heist, rebuilt so each stage is something you *watch happen to someone* rather
 * than a progress ring with a caption.
 *
 * 1. **Creep the beam corridor** — the old hold-still check, now with a thief tiptoeing between
 *    two laser grids. Wobble and the beam above his head drops toward him; hold steady and he
 *    advances. Movement drains progress instead of resetting it, so a cough is a setback and not
 *    a restart.
 * 2. **Turn the lock, dodge the guillotines** — the old circular swipe, now with beams that drop
 *    and retract along the corridor. Walking into one *stuns* the thief for a second; it never
 *    sends the player back to the start.
 * 3. **Crack the safe** — replaces the single timed button press. The crown (or a drag, on a
 *    watch with no crown) turns a real dial; the watch ticks harder and faster the closer the
 *    dial gets to the notch. Park on it for a second, three times, and the safe opens.
 */
class LockPickingGameActivity : MicrogameActivity(), SensorEventListener {

    override val timeoutSeconds = 120
    override val tutorialTitleRes = R.string.game_lockpicking_title
    override val tutorialTextRes = R.string.game_lockpicking_tutorial

    private lateinit var sensorManager: SensorManager

    /** Linear acceleration magnitude with gravity removed; ~0 when the wrist is still. */
    private var currentMagnitude = 0f

    private companion object {
        /** Acceleration (m/s²) treated as "completely out of control" — the top of the meter. */
        const val MOTION_CEILING = 1.4f

        /** Crown pixels for one full turn of the safe dial. Tuned for a Pixel Watch crown. */
        const val ROTARY_PIXELS_PER_TURN = 900f

        /** Crown pixels to fill a one-screen stage-2 lock; scaled by the run length. */
        const val ROTARY_PIXELS_PER_LOCK = 2600f

        /** Finger-drag degrees to fill a one-screen stage-2 lock; scaled by the run length. */
        const val DRAG_DEGREES_PER_LOCK = 1080f

        /** How close to the notch counts as "on it", in degrees. */
        const val NOTCH_TOLERANCE_DEG = 7f

        /** Milliseconds the dial must stay on the notch for a tumbler to drop. */
        const val NOTCH_HOLD_MS = 1000f

        const val TUMBLERS = 3
        const val STUN_MS = 1000L

        /**
         * Grace after a stun wears off, during which beams cannot connect again.
         *
         * Without it, a thief stunned under a beam that is still down is re-stunned the frame
         * he wakes up, forever - the one way this stage could become unwinnable.
         */
        const val STUN_GRACE_MS = 700L

        /**
         * Stage-2 figure height as a fraction of the corridor. Shared by the drawing and the
         * collision test so a beam connects exactly when it visually reaches his head.
         */
        const val CORRIDOR_FIGURE_FRACTION = 0.72f

        /** Half-width of the thief's hitbox on a one-screen run, in units of stage-2 progress. */
        const val CORRIDOR_HIT_HALF_WIDTH = 0.055f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(SensorManager::class.java)
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
        currentMagnitude = (sqrt((x * x + y * y + z * z).toDouble()) - 9.8f).toFloat()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    @Composable
    override fun GameContent() {
        var stage by remember { mutableIntStateOf(1) }
        MaterialTheme {
            when (stage) {
                1 -> StageCreep(onComplete = { vibratePulse(); stage = 2 })
                2 -> StageCorridor(onComplete = { vibratePulse(); stage = 3 })
                else -> StageSafe(onComplete = { onGameComplete("LockPick OK") })
            }
        }
    }

    // ── Stage 1 — creep the beam corridor ────────────────────────────────────

    @Composable
    private fun StageCreep(onComplete: () -> Unit) {
        // Snapshotted once per stage rather than collected: a settings push that lands
        // mid-round must not change the rules under the player's hands halfway through.
        val settings = remember { GameSettings.load(this) }
        val penalty = settings.lockPenalty
        val motionLimit = remember(settings) { GameSettings.lockMotionLimit(settings.lockDifficulty) }
        val creepSeconds = remember(settings) { GameSettings.lockCreepSeconds(settings.lockDifficulty) }

        var progress by remember { mutableFloatStateOf(0f) }
        var motion by remember { mutableFloatStateOf(0f) }
        var frozenUntil by remember { mutableLongStateOf(0L) }
        val tripped = motion > motionLimit

        LaunchedEffect(Unit) {
            val stepMs = 40L
            val step = stepMs / 1000f
            while (true) {
                delay(stepMs)
                // Smoothed, so a single noisy sample cannot trip the alarm on its own — the
                // accelerometer reads ±0.2 m/s² of jitter even on a table.
                val raw = (abs(currentMagnitude) / MOTION_CEILING).coerceIn(0f, 1f)
                motion += (raw - motion) * 0.25f

                val now = System.currentTimeMillis()
                if (motion > motionLimit) {
                    // The consequence is a setting, not a constant: how harshly a slip is
                    // punished is exactly the kind of thing a study wants to vary between arms,
                    // and a participant with a tremor may need a forgiving one.
                    when (penalty) {
                        LockPenalty.STUN -> frozenUntil = now + STUN_MS
                        LockPenalty.PUSHBACK ->
                            progress = (progress - step / creepSeconds * 1.4f).coerceAtLeast(0f)
                        LockPenalty.RESET -> progress = 0f
                    }
                } else if (now >= frozenUntil) {
                    progress += step / creepSeconds
                    if (progress >= 1f) { onComplete(); return@LaunchedEffect }
                }
            }
        }

        LaunchedEffect(tripped) {
            if (tripped) vibratePattern(longArrayOf(0, 60, 60, 60))
        }

        val walkPhase = rememberLoopPhase(900)

        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                drawEdgeProgress(progress, if (tripped) Color(0xFFFF5252) else Color(0xFF26C6DA))
                drawCreepScene(progress, motion, motionLimit, tripped, walkPhase)
            }
            StageChrome(
                stage = 1,
                headline = stringResource(
                    if (tripped) R.string.game_lockpicking_too_much_motion
                    else R.string.game_lockpicking_hold_still
                ),
                headlineColor = if (tripped) Color(0xFFFF5252) else Color.White,
            ) {
                SteadinessBar(level = motion, limit = motionLimit, tripped = tripped)
            }
        }
    }

    /** The corridor, the descending beam and the balancing thief. */
    private fun DrawScope.drawCreepScene(
        progress: Float,
        motion: Float,
        motionLimit: Float,
        tripped: Boolean,
        walkPhase: Float,
    ) {
        val corridor = Rect(
            left = size.width * 0.16f,
            top = size.height * 0.32f,
            right = size.width * 0.84f,
            bottom = size.height * 0.73f,
        )
        val floorY = corridor.bottom
        val figureHeight = corridor.height * 0.74f
        val stroke = size.minDimension * 0.011f

        drawFloor(corridor, floorY, Color(0xFF4A5568), stroke)

        val headTop = floorY - figureHeight
        val maxClearance = figureHeight * 0.34f
        val danger = (motion / motionLimit).coerceIn(0f, 1f)

        // Ceiling beams, drawn above the live beam's *highest* position. Keeping them out of
        // its travel is what makes the live beam always the lowest — and so the nearest — line
        // on screen; with them interleaved there is no way to tell at a glance which beam is
        // the one closing in on you.
        repeat(2) { i ->
            drawLaserBeam(
                bounds = corridor,
                y = headTop - maxClearance - (i + 1) * size.height * 0.030f,
                intensity = 0.18f,
                thickness = stroke * 0.7f,
            )
        }

        // The beam that matters. Its clearance above the thief's head closes as the wrist
        // moves, so "how close am I to moving too much" is a physical gap, not a number.
        drawLaserBeam(
            bounds = corridor,
            y = headTop - maxClearance * (1f - danger),
            intensity = if (tripped) 1f else 0.55f + 0.45f * danger,
            thickness = stroke * 1.6f,
        )

        // The thief creeps left-to-right; his position *is* the progress bar.
        val x = corridor.left + corridor.width * (0.08f + 0.84f * progress.coerceIn(0f, 1f))
        drawStickFigure(
            feet = Offset(x, floorY),
            height = figureHeight,
            color = if (tripped) Color(0xFFFF8A80) else Color(0xFFE8EDF5),
            pose = StickPose.BALANCING,
            phase = walkPhase,
            lean = danger * sin(walkPhase * 2f * PI.toFloat()),
            stroke = stroke * 1.6f,
        )

        // The vault door he is creeping toward, so the goal is visible from the first frame.
        drawVaultDoor(Offset(corridor.right + size.width * 0.035f, floorY), figureHeight * 0.58f)
    }

    // ── Stage 2 — turn the lock while dodging drop beams ─────────────────────

    /** One guillotine beam: where it hangs on the run, and how its cycle is timed. */
    private data class DropBeam(val xFraction: Float, val periodMs: Int, val phaseOffset: Float)

    /** A beam's state this frame: how far it hangs, and how close it is to firing. */
    private data class BeamState(val reach: Float, val arming: Float)

    @Composable
    private fun StageCorridor(onComplete: () -> Unit) {
        val settings = remember { GameSettings.load(this) }
        val penalty = settings.lockPenalty
        val difficulty = settings.lockDifficulty
        val screens = remember(difficulty) { GameSettings.lockRunScreens(difficulty) }

        val beams = remember(difficulty) {
            val count = GameSettings.lockBeamCount(difficulty)
            List(count) { i ->
                // Spread across the run, leaving the first and last stretch clear so the player
                // can start moving and can finish without a beam sitting on the vault door.
                val t = if (count == 1) 0.5f else i / (count - 1).toFloat()
                DropBeam(
                    xFraction = 0.20f + 0.62f * t,
                    // Periods are deliberately co-prime-ish so the beams never settle into a
                    // single rhythm the player can walk through once and repeat.
                    periodMs = 2000 + (i % 3) * 370,
                    phaseOffset = (i * 0.37f) % 1f,
                )
            }
        }

        var progress by remember { mutableFloatStateOf(0f) }
        var stunnedUntil by remember { mutableLongStateOf(0L) }
        var safeUntil by remember { mutableLongStateOf(0L) }
        var lastDragAngle by remember { mutableStateOf<Float?>(null) }
        var clock by remember { mutableLongStateOf(0L) }
        var walkPhase by remember { mutableFloatStateOf(0f) }
        val hasRotary = remember { hasRotaryInput() }

        // Plain holder, not state: it is only ever read from the draw pass, which already runs
        // every frame because `clock` ticks. Making it state would cost a recomposition per
        // turn event for nothing.
        val lastAdvanceAt = remember { longArrayOf(0L) }

        /** +1 walking toward the vault, -1 backing away. Mirrors the walk cycle. */
        val lastDirection = remember { floatArrayOf(1f) }

        val stunned = clock < stunnedUntil

        // A longer run needs proportionally more turning, so the crown-to-distance feel stays
        // the same whatever the difficulty.
        val rotaryPerLock = ROTARY_PIXELS_PER_LOCK * screens
        val dragPerLock = DRAG_DEGREES_PER_LOCK * screens

        // Signed, so turning back walks the thief back down the corridor. Backing off is the
        // whole point of telegraphing a beam before it drops: a warning you cannot act on is
        // just a countdown. It costs lock progress, which is the trade the player chooses.
        val advance: (Float) -> Unit = { amount ->
            val now = System.currentTimeMillis()
            if (now >= stunnedUntil && amount != 0f) {
                lastAdvanceAt[0] = now
                lastDirection[0] = if (amount > 0f) 1f else -1f
                progress = (progress + amount).coerceIn(0f, 1f)
                if (progress >= 1f) onComplete()
            }
        }

        // The thief is about as wide as a fifth of his height; converting that to run units
        // keeps the hitbox honest however long the corridor is.
        val hitHalfWidth = CORRIDOR_HIT_HALF_WIDTH / screens

        LaunchedEffect(Unit) {
            while (true) {
                delay(33L)
                clock = System.currentTimeMillis()
                val moving = clock - lastAdvanceAt[0] < 250L
                if (clock >= stunnedUntil && moving) {
                    walkPhase = (walkPhase + 0.055f * lastDirection[0] + 1f) % 1f
                }
                // Collision is evaluated on the game clock, not on input, so standing under a
                // beam that is on its way down still gets you.
                if (clock >= stunnedUntil && clock >= safeUntil && progress > 0f && progress < 1f) {
                    val hit = beams.any { beam ->
                        abs(progress - beam.xFraction) < hitHalfWidth &&
                            beamState(beam, clock).reach > 1f - CORRIDOR_FIGURE_FRACTION
                    }
                    if (hit) {
                        stunnedUntil = clock + STUN_MS
                        safeUntil = stunnedUntil + STUN_GRACE_MS
                        if (penalty != LockPenalty.STUN) {
                            progress = when (penalty) {
                                // Enough to clear the beam he was standing under, so the
                                // pushback actually moves him out of danger rather than
                                // dropping him back into the same trap.
                                LockPenalty.PUSHBACK ->
                                    (progress - hitHalfWidth * 2.4f).coerceAtLeast(0f)
                                else -> 0f
                            }
                        }
                        vibratePattern(longArrayOf(0, 220, 90, 220))
                    }
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .rotaryDelta { pixels -> advance(pixels / rotaryPerLock) }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            lastDragAngle = angleAt(offset, size.width.toFloat(), size.height.toFloat())
                        },
                        onDrag = { change, _ ->
                            val angle = angleAt(change.position, size.width.toFloat(), size.height.toFloat())
                            lastDragAngle?.let { prev ->
                                advance(shortestDelta(prev, angle) / dragPerLock)
                            }
                            lastDragAngle = angle
                        },
                        onDragEnd = { lastDragAngle = null },
                        onDragCancel = { lastDragAngle = null },
                    )
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawEdgeProgress(progress, if (stunned) Color(0xFFFFB300) else Color(0xFFFFEE58))
                drawCorridorScene(
                    beams = beams,
                    progress = progress,
                    screens = screens,
                    clock = clock,
                    stunned = stunned,
                    walkPhase = walkPhase,
                    moving = clock - lastAdvanceAt[0] < 250L,
                )
            }
            StageChrome(
                stage = 2,
                headline = stringResource(
                    when {
                        stunned -> R.string.game_lockpicking_stunned
                        hasRotary -> R.string.game_lockpicking_turn_crown
                        else -> R.string.game_lockpicking_swipe_circle
                    }
                ),
                headlineColor = if (stunned) Color(0xFFFFB300) else Color.White,
            )
        }
    }

    /**
     * Corridor with dropping beams and the thief advancing along it.
     *
     * At difficulty 1 the run is exactly one screen wide and nothing scrolls. Above that the
     * camera follows the thief but clamps at both ends — "semi scroll" — so the entrance stays
     * visible while he is near the start and the vault door is on screen for the whole finish,
     * instead of the run being an endless featureless tunnel.
     */
    private fun DrawScope.drawCorridorScene(
        beams: List<DropBeam>,
        progress: Float,
        screens: Float,
        clock: Long,
        stunned: Boolean,
        walkPhase: Float,
        moving: Boolean,
    ) {
        val corridor = Rect(
            left = size.width * 0.15f,
            top = size.height * 0.32f,
            right = size.width * 0.85f,
            bottom = size.height * 0.72f,
        )
        val floorY = corridor.bottom
        val figureHeight = corridor.height * CORRIDOR_FIGURE_FRACTION
        val stroke = size.minDimension * 0.011f

        val viewWidth = corridor.width
        val worldWidth = viewWidth * screens
        val thiefWorldX = progress.coerceIn(0f, 1f) * worldWidth
        // Keep the thief a little left of centre so more of what is coming is visible than of
        // what is behind him.
        val cameraX = (thiefWorldX - viewWidth * 0.42f)
            .coerceIn(0f, (worldWidth - viewWidth).coerceAtLeast(0f))
        fun toScreen(worldX: Float) = corridor.left + (worldX - cameraX)

        // Ceiling rail the emitters hang from.
        drawLine(
            color = Color(0xFF37474F),
            start = Offset(corridor.left, corridor.top),
            end = Offset(corridor.right, corridor.top),
            strokeWidth = stroke * 1.2f,
            cap = StrokeCap.Round,
        )
        drawFloor(corridor, floorY, Color(0xFF4A5568), stroke)

        beams.forEach { beam ->
            val x = toScreen(beam.xFraction * worldWidth)
            if (x < corridor.left - stroke * 4f || x > corridor.right + stroke * 4f) return@forEach
            val state = beamState(beam, clock)
            drawDropBeam(
                bounds = corridor,
                x = x,
                reach = state.reach,
                arming = state.arming,
                thickness = stroke * 1.3f,
            )
        }

        drawStickFigure(
            feet = Offset(toScreen(thiefWorldX), floorY),
            height = figureHeight,
            color = if (stunned) Color(0xFFFFCC80) else Color(0xFFE8EDF5),
            pose = when {
                stunned -> StickPose.STUNNED
                moving -> StickPose.WALKING
                // Standing rather than moon-walking on the spot. The pose is the clearest
                // signal that the lock only turns while the player is turning it.
                else -> StickPose.WAITING
            },
            phase = walkPhase,
            stroke = stroke * 1.6f,
        )

        // The vault door sits at the far end of the *run*, so on a scrolling corridor it slides
        // into view as the thief closes on it.
        val doorX = toScreen(worldWidth + size.width * 0.035f)
        if (doorX < corridor.right + size.width * 0.25f) {
            drawVaultDoor(Offset(doorX, floorY), figureHeight * 0.62f)
        }
    }

    /**
     * Beam [beam]'s state at [clock].
     *
     * The cycle opens with an **arming** window in which the beam has not dropped yet but the
     * emitter is visibly charging. Without it a beam that fires the instant it appears is pure
     * memorisation — you have to already know it is coming — whereas a telegraph turns the
     * stage into something you can read and react to, including backing off a step.
     *
     * The rest is a sawtooth held at the extremes. The pauses are what make the timing
     * learnable; a pure sine gives no stable window to walk through.
     */
    private fun beamState(beam: DropBeam, clock: Long): BeamState {
        val t = (((clock % beam.periodMs) / beam.periodMs.toFloat()) + beam.phaseOffset) % 1f
        return when {
            t < 0.22f -> BeamState(0f, t / 0.22f)                     // charging up
            t < 0.34f -> BeamState((t - 0.22f) / 0.12f, 1f)           // dropping
            t < 0.56f -> BeamState(1f, 1f)                            // fully down
            t < 0.70f -> BeamState(1f - (t - 0.56f) / 0.14f, 0f)      // retracting
            else -> BeamState(0f, 0f)                                 // clear
        }
    }

    // ── Stage 3 — crack the safe ─────────────────────────────────────────────

    @Composable
    private fun StageSafe(onComplete: () -> Unit) {
        var dialAngle by remember { mutableFloatStateOf(0f) }
        // Never within reach of the dial's starting position, or the first tumbler can fall
        // before the player has touched anything.
        var notch by remember { mutableFloatStateOf((60..300).random().toFloat()) }
        var solved by remember { mutableIntStateOf(0) }
        var holdMs by remember { mutableFloatStateOf(0f) }
        var lastDragAngle by remember { mutableStateOf<Float?>(null) }
        val hasRotary = remember { hasRotaryInput() }

        // 1 at the notch, 0 once the dial is a quarter-turn away. Drives the ticks, the meter
        // and the dial's glow together, so the haptic and the visual never disagree.
        val proximity = (1f - abs(shortestDelta(dialAngle, notch)) / 90f).coerceIn(0f, 1f)
        val onNotch = abs(shortestDelta(dialAngle, notch)) <= NOTCH_TOLERANCE_DEG

        val turn: (Float) -> Unit = { degrees ->
            dialAngle = (dialAngle + degrees).mod(360f)
        }

        LaunchedEffect(Unit) {
            var lastTickAt = 0L
            while (true) {
                delay(50L)
                // Recomputed per tick from the backing state. The `proximity`/`onNotch` vals
                // above belong to the composition that started this coroutine and are frozen
                // at their first-frame values in here.
                val offBy = abs(shortestDelta(dialAngle, notch))
                val nearness = (1f - offBy / 90f).coerceIn(0f, 1f)
                if (offBy <= NOTCH_TOLERANCE_DEG) {
                    holdMs += 50f
                    // A steady "locked on" pulse while parked. Without it the notch feels the
                    // same as being far away - both silent - and the only confirmation left
                    // would be the on-screen ring, which defeats the point of a haptic safe.
                    val now = System.currentTimeMillis()
                    if (now - lastTickAt >= 140L) {
                        lastTickAt = now
                        vibrateAmplitude(durationMs = 26, amplitude = 210)
                    }
                    if (holdMs >= NOTCH_HOLD_MS) {
                        holdMs = 0f
                        solved++
                        vibratePattern(longArrayOf(0, 90, 70, 220))
                        if (solved >= TUMBLERS) { onComplete(); return@LaunchedEffect }
                        // A fresh notch at least a third of a turn away, so a solved tumbler
                        // can never be re-solved by simply standing still.
                        notch = (notch + (100..260).random()).mod(360f)
                    }
                } else {
                    // Bleed the hold off instead of zeroing it — a hand tremor that carries the
                    // dial one degree past tolerance should not erase most of a second's work.
                    holdMs = (holdMs - 90f).coerceAtLeast(0f)

                    // Geiger-counter feedback: ticks get faster *and* harder near the notch.
                    val now = System.currentTimeMillis()
                    val interval = (420f - 340f * nearness).toLong()
                    if (nearness > 0.05f && now - lastTickAt >= interval) {
                        lastTickAt = now
                        vibrateAmplitude(
                            durationMs = (18 + 22 * nearness).toLong(),
                            amplitude = (40 + 205 * nearness).toInt(),
                        )
                    }
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .rotaryDelta { pixels -> turn(pixels / ROTARY_PIXELS_PER_TURN * 360f) }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            lastDragAngle = angleAt(offset, size.width.toFloat(), size.height.toFloat())
                        },
                        onDrag = { change, _ ->
                            val angle = angleAt(change.position, size.width.toFloat(), size.height.toFloat())
                            lastDragAngle?.let { prev -> turn(shortestDelta(prev, angle)) }
                            lastDragAngle = angle
                        },
                        onDragEnd = { lastDragAngle = null },
                        onDragCancel = { lastDragAngle = null },
                    )
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawSafeDial(
                    dialAngle = dialAngle,
                    proximity = proximity,
                    onNotch = onNotch,
                    holdFraction = holdMs / NOTCH_HOLD_MS,
                    solved = solved,
                )
            }
            StageChrome(
                stage = 3,
                headline = stringResource(
                    when {
                        onNotch -> R.string.game_lockpicking_hold_it
                        hasRotary -> R.string.game_lockpicking_turn_crown_feel
                        else -> R.string.game_lockpicking_drag_dial_feel
                    }
                ),
                headlineColor = if (onNotch) Color(0xFFFFD54F) else Color.White,
            ) {
                ProximityMeter(proximity = proximity, onNotch = onNotch)
            }
        }
    }

    /**
     * Visual twin of the haptic ticks.
     *
     * The stage is designed around vibration, but vibration alone would lock out anyone with
     * reduced tactile sensitivity, and it is unreadable through a thick strap. The meter shows
     * *how close* the dial is without ever showing *where* the notch is, so it costs the puzzle
     * nothing.
     */
    @Composable
    private fun ProximityMeter(proximity: Float, onNotch: Boolean) {
        val bars = 6
        val lit = (proximity * bars).toInt().coerceIn(0, bars)
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.sdp),
            verticalAlignment = Alignment.Bottom,
        ) {
            repeat(bars) { i ->
                val on = i < lit
                Box(
                    Modifier
                        .width(6.sdp)
                        .height((5 + i * 2).sdp)
                        .background(
                            color = when {
                                onNotch -> Color(0xFFFFD54F)
                                on -> Color(0xFF66BB6A)
                                else -> Color(0xFF37474F)
                            },
                            shape = RoundedCornerShape(2.sdp),
                        )
                )
            }
        }
    }

    /** The dial face, its fixed pointer, the hold ring and the tumbler pips. */
    private fun DrawScope.drawSafeDial(
        dialAngle: Float,
        proximity: Float,
        onNotch: Boolean,
        holdFraction: Float,
        solved: Int,
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val outer = size.minDimension * 0.36f
        val stroke = size.minDimension * 0.012f

        // Body of the dial.
        drawCircle(Color(0xFF11161D), outer, center)
        drawCircle(
            color = if (onNotch) Color(0xFFFFD54F) else Color(0xFF546E7A).copy(alpha = 0.55f + 0.45f * proximity),
            radius = outer,
            center = center,
            style = Stroke(stroke * 1.6f),
        )

        // Ticks rotate with the dial, so turning is unmistakable even at a glance.
        rotate(degrees = dialAngle, pivot = center) {
            repeat(36) { i ->
                val major = i % 3 == 0
                val angle = (i * 10f - 90f) * PI.toFloat() / 180f
                val rOuter = outer - stroke * 0.6f
                val rInner = rOuter - if (major) outer * 0.20f else outer * 0.11f
                drawLine(
                    color = if (major) Color(0xFFCFD8DC) else Color(0xFF78909C),
                    start = Offset(center.x + rInner * cos(angle), center.y + rInner * sin(angle)),
                    end = Offset(center.x + rOuter * cos(angle), center.y + rOuter * sin(angle)),
                    strokeWidth = if (major) stroke else stroke * 0.6f,
                    cap = StrokeCap.Round,
                )
            }
            // The dial's own index mark — the "0" you are turning around the face.
            val markAngle = -90f * PI.toFloat() / 180f
            drawCircle(
                color = Color(0xFFEF5350),
                radius = stroke * 1.5f,
                center = Offset(
                    center.x + (outer - outer * 0.30f) * cos(markAngle),
                    center.y + (outer - outer * 0.30f) * sin(markAngle),
                ),
            )
        }

        // Fixed pointer at 12 o'clock — the reference the notch has to line up with.
        val pointerTip = Offset(center.x, center.y - outer + stroke * 2.4f)
        drawLine(
            color = if (onNotch) Color(0xFFFFD54F) else Color.White,
            start = Offset(center.x, center.y - outer - stroke * 1.6f),
            end = pointerTip,
            strokeWidth = stroke * 1.8f,
            cap = StrokeCap.Round,
        )

        // Hub: hold ring plus tumbler pips.
        val hub = outer * 0.46f
        drawCircle(Color(0xFF0B0F14), hub, center)
        if (holdFraction > 0.01f) {
            drawArc(
                color = Color(0xFFFFD54F),
                startAngle = -90f,
                sweepAngle = holdFraction.coerceIn(0f, 1f) * 360f,
                useCenter = false,
                style = Stroke(stroke * 1.8f, cap = StrokeCap.Round),
                topLeft = Offset(center.x - hub * 0.82f, center.y - hub * 0.82f),
                size = Size(hub * 1.64f, hub * 1.64f),
            )
        }
        repeat(TUMBLERS) { i ->
            val spacing = hub * 0.52f
            val cx = center.x + (i - (TUMBLERS - 1) / 2f) * spacing
            drawCircle(
                color = if (i < solved) Color(0xFFFFD54F) else Color(0xFF37474F),
                radius = hub * 0.16f,
                center = Offset(cx, center.y),
            )
        }
    }

    // ── Shared chrome and drawing helpers ────────────────────────────────────

    /**
     * Stage number at the top, headline under it, and optional gauge at the bottom.
     *
     * Kept as composed text rather than canvas text so it stays translatable and honours the
     * watch's font-scale accessibility setting.
     */
    @Composable
    private fun StageChrome(
        stage: Int,
        headline: String,
        headlineColor: Color,
        gauge: @Composable (() -> Unit)? = null,
    ) {
        val dimens = wearDimens
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = dimens.verticalPadding + 4.sdp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.game_lockpicking_stage_indicator, stage, 3),
                    fontSize = 9.ssp,
                    color = Color(0xFF78909C),
                )
                Text(
                    text = headline,
                    fontSize = 12.ssp,
                    fontWeight = FontWeight.Bold,
                    color = headlineColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
            if (gauge != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = dimens.verticalPadding + 4.sdp),
                ) { gauge() }
            }
        }
    }

    /** Progress ring hugging the bezel — the stage's completion, always in the same place. */
    private fun DrawScope.drawEdgeProgress(progress: Float, color: Color) {
        val stroke = size.minDimension * 0.028f
        val r = size.minDimension / 2f - stroke / 2f - size.minDimension * 0.01f
        val center = Offset(size.width / 2f, size.height / 2f)
        val topLeft = Offset(center.x - r, center.y - r)
        val arcSize = Size(r * 2, r * 2)
        drawArc(
            color = Color.White.copy(alpha = 0.10f),
            startAngle = 0f, sweepAngle = 360f, useCenter = false,
            style = Stroke(stroke), topLeft = topLeft, size = arcSize,
        )
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = progress.coerceIn(0f, 1f) * 360f,
            useCenter = false,
            style = Stroke(stroke, cap = StrokeCap.Round),
            topLeft = topLeft, size = arcSize,
        )
    }

    /** The prize at the end of the corridor: a small vault door with a dial. */
    private fun DrawScope.drawVaultDoor(base: Offset, height: Float) {
        val w = height * 0.72f
        val rect = Rect(base.x - w / 2f, base.y - height, base.x + w / 2f, base.y)
        drawRect(Color(0xFF263238), Offset(rect.left, rect.top), Size(rect.width, rect.height))
        drawRect(
            color = Color(0xFF607D8B),
            topLeft = Offset(rect.left, rect.top),
            size = Size(rect.width, rect.height),
            style = Stroke(height * 0.055f),
        )
        drawCircle(Color(0xFFB0BEC5), height * 0.15f, rect.center, style = Stroke(height * 0.05f))
    }

    /** A 0..1 phase that loops every [periodMs], for walk cycles and idle motion. */
    @Composable
    private fun rememberLoopPhase(periodMs: Int): Float {
        var phase by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(periodMs) {
            while (true) {
                delay(33L)
                phase = (phase + 33f / periodMs) % 1f
            }
        }
        return phase
    }
}

/** Angle in degrees of [point] about the centre of a [width]×[height] box. 0° = 3 o'clock. */
private fun angleAt(point: Offset, width: Float, height: Float): Float =
    atan2(point.y - height / 2f, point.x - width / 2f) * (180f / PI.toFloat())

/** Signed shortest rotation from [from] to [to], in (-180, 180]. */
private fun shortestDelta(from: Float, to: Float): Float {
    var delta = (to - from) % 360f
    if (delta > 180f) delta -= 360f
    if (delta < -180f) delta += 360f
    return delta
}
