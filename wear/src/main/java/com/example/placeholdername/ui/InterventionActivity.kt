package com.example.jitaicompanion.ui

import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.LocalTextStyle
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.example.jitaicompanion.R
import com.example.jitaicompanion.convention.Protocol
import com.example.jitaicompanion.convention.models.GameType
import com.example.jitaicompanion.convention.models.Intervention
import com.example.jitaicompanion.convention.models.NotificationType
import com.example.jitaicompanion.datalayer.WearMessageSender
import com.example.jitaicompanion.ui.games.LockPickingGameActivity
import com.example.jitaicompanion.ui.games.SimonSaysGameActivity
import com.example.jitaicompanion.ui.games.StandStillGameActivity
import com.example.jitaicompanion.ui.games.TriviaGameActivity
import com.example.jitaicompanion.ui.layout.ProvideWearDimens
import com.example.jitaicompanion.ui.layout.sdp
import com.example.jitaicompanion.ui.layout.ssp
import com.example.jitaicompanion.ui.layout.wearDimens
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

class InterventionActivity : ComponentActivity() {

    companion object {
        private var currentInstance: InterventionActivity? = null
        fun finishCurrent() { currentInstance?.finish() }
    }

    private lateinit var vibrator: Vibrator
    private var ringtone: Ringtone? = null

    override fun onDestroy() {
        super.onDestroy()
        if (currentInstance === this) currentInstance = null
        // `vibrator` is only initialized past the early-return guards in onCreate.
        if (::vibrator.isInitialized) vibrator.cancel()
        ringtone?.stop()
        ringtone = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentInstance = this

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val jsonStr = intent.getStringExtra(Protocol.KEY_INTERVENTION) ?: run { finish(); return }
        val intervention = try {
            Json { ignoreUnknownKeys = true }.decodeFromString<Intervention>(jsonStr)
        } catch (e: Exception) {
            finish(); return
        }

        if (intervention.type == "Stop" || intervention.durationSeconds == 0) { finish(); return }

        if (intervention.message.isBlank()) {
            intervention.message = defaultMessageFor(intervention.type)
        }

        vibrator = getSystemService(Vibrator::class.java)
        applyVibration(intervention.notification)

        setContent {
            ProvideWearDimens {
                val sender = remember { WearMessageSender(this) }

                // Called when the user positively acknowledges the intervention (OK / Yes / timer done).
                // If a microgame is attached, launch it now — the text/vibration has already been shown.
                // Otherwise just send the response and finish.
                val onAccepted: (String) -> Unit = { response ->
                    if (intervention.gameType != null) {
                        launchGame(jsonStr, intervention.gameType!!)
                    } else {
                        sender.sendResponse(response)
                        finish()
                    }
                }

                // Called when the user explicitly declines (Yes/No → "No").
                // Never launches a game — always cancels the interaction cleanly.
                val onDeclined: (String) -> Unit = { response ->
                    sender.sendResponse(response)
                    finish()
                }

                MaterialTheme {
                    // Global timeout: for Yes/No the user didn't respond → treat as a decline (no game).
                    // For all other types the notification period elapsed → proceed to game if attached.
                    LaunchedEffect(Unit) {
                        delay(intervention.durationSeconds * 1000L)
                        if (intervention.type == "Yes/No") onDeclined("timeout") else onAccepted("timeout")
                    }
                    when (intervention.type) {
                        "Timer"  -> TimerScreen(intervention, onAccepted)
                        "Yes/No" -> YesNoScreen(intervention, onAccepted, onDeclined)
                        else     -> TextScreen(intervention, onAccepted)
                    }
                }
            }
        }
    }

    /**
     * Starts the appropriate microgame activity and finishes this one.
     * The full intervention JSON is forwarded so the game can read message/triviaQuestion/etc.
     */
    private fun launchGame(jsonStr: String, gameType: GameType) {
        val gameClass = when (gameType) {
            GameType.LOCK_PICKING -> LockPickingGameActivity::class.java
            GameType.SIMON_SAYS   -> SimonSaysGameActivity::class.java
            GameType.TRIVIA       -> TriviaGameActivity::class.java
            GameType.STAND_STILL  -> StandStillGameActivity::class.java
        }
        startActivity(Intent(this, gameClass).apply {
            putExtra(Protocol.KEY_INTERVENTION, jsonStr)
        })
        finish()
    }

    /** Fallback prompt shown when an intervention arrives with no message text. */
    private fun defaultMessageFor(type: String): String = when (type) {
        "Yes/No" -> getString(R.string.intervention_default_message_yes_no)
        "Timer"  -> getString(R.string.intervention_default_message_timer)
        else     -> getString(R.string.intervention_default_message_text)
    }

    private fun applyVibration(type: NotificationType) {
        when (type) {
            NotificationType.VIBRATION1, NotificationType.VIBRATION_SOUND ->
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200, 100, 400), -1))
            NotificationType.VIBRATION2, NotificationType.VIBRATION_SOUND2 ->
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 100, 400, 100, 400, 100, 400, 100, 400, 100, 400), -1))
            NotificationType.ANNOY_VIB, NotificationType.ANNOY_VIBRATION_SOUND ->
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 100, 400), 0))
            else -> {}
        }
        when (type) {
            NotificationType.SOUND, NotificationType.VIBRATION_SOUND, NotificationType.VIBRATION_SOUND2 -> {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ringtone = RingtoneManager.getRingtone(this, uri)?.also { it.play() }
            }
            NotificationType.ANNOY_SOUND, NotificationType.ANNOY_VIBRATION_SOUND -> {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ringtone = RingtoneManager.getRingtone(this, uri)?.also {
                    it.isLooping = true
                    it.play()
                }
            }
            else -> {}
        }
    }
}

// ── Screen composables ────────────────────────────────────────────────────────

@Composable
private fun TextScreen(
    intervention: Intervention,
    onAccepted: (String) -> Unit
) {
    MessageScrollColumn {
        AutoResizeText(
            text = intervention.message,
            modifier = Modifier.padding(horizontal = messageInset())
        )
        Spacer(Modifier.height(16.sdp))
        // "Ready!" leads into a microgame; "OK" just dismisses. Payload sent to the phone
        // ("Done") is unrelated to this label and must not be localized.
        val label = if (intervention.gameType != null) {
            stringResource(R.string.intervention_button_ready)
        } else {
            stringResource(R.string.intervention_button_ok)
        }
        Button(onClick = { onAccepted("Done") }) { Text(label) }
    }
}

@Composable
private fun YesNoScreen(
    intervention: Intervention,
    onAccepted: (String) -> Unit,
    onDeclined: (String) -> Unit
) {
    val dimens = wearDimens
    MessageScrollColumn {
        AutoResizeText(
            text = intervention.message,
            modifier = Modifier.padding(horizontal = messageInset())
        )
        Spacer(Modifier.height(12.sdp))
        Row(
            modifier = Modifier.padding(horizontal = messageInset()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.itemSpacing)
        ) {
            // "Yes" proceeds to the game (if any) or sends the response. The "Yes" payload
            // below is the response recorded as study data — it must stay literal English.
            Button(onClick = { onAccepted("Yes") }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.intervention_button_yes))
            }
            // "No" always cancels — the game (if any) is not launched. Same rule: the "No"
            // payload is study data and must stay literal English.
            Button(onClick = { onDeclined("No") }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.intervention_button_no))
            }
        }
    }
}

@Composable
private fun TimerScreen(
    intervention: Intervention,
    onAccepted: (String) -> Unit
) {
    // One linear animation over the whole duration. The previous version polled the clock
    // every 16 ms and fed the result through a second spring animation, so two animation
    // clocks ran for the length of every timer; the frame clock alone does the same job.
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = intervention.durationSeconds * 1000, easing = LinearEasing)
        )
    }

    val remaining = ((1f - progress.value) * intervention.durationSeconds).toInt()
    CircularProgressWithCenter(
        progress = progress.value,
        text = intervention.message,
        timerText = stringResource(R.string.intervention_timer_seconds_remaining, remaining),
        onOk = { onAccepted("Timer OK") }
    )
}

// ── Shared UI helpers ─────────────────────────────────────────────────────────

/**
 * Side inset for message text and the buttons under it.
 *
 * A round screen clips the corners of anything laid out to its full width, and a multi-line
 * block reaches into the narrower chords above and below the centre. About 9 % a side keeps a
 * wrapped paragraph inside the glass on every watch size.
 */
@Composable
private fun messageInset(): Dp = (wearDimens.screenWidthDp * 0.09f).dp

/**
 * The scrolling body shared by the three intervention screens.
 *
 * The researcher writes the message, so it can be any length and in any language (German runs
 * roughly a fifth longer than English), and the watch can be anything from a 192 dp to a 227 dp
 * round display. Nothing here may assume the text fits, so the column scrolls by touch and by
 * crown, and shows a fade and chevron on whichever edge has more content hidden behind it.
 * Content shorter than the screen stays centred, so a short message looks as it always did.
 */
@Composable
private fun MessageScrollColumn(content: @Composable ColumnScope.() -> Unit) {
    val scrollState = rememberScrollState()
    val focusRequester = rememberActiveFocusRequester()
    // A round screen hides the top and bottom of a full-height column behind its curve, so
    // without this slack a long message's first line and its button would start out clipped.
    val edgeInset = (wearDimens.screenHeightDp * 0.14f).dp
    val edgeInsetPx = with(LocalDensity.current) { edgeInset.toPx() }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .rotaryScrollable(RotaryScrollableDefaults.behavior(scrollState), focusRequester)
                .verticalScroll(scrollState)
                .padding(vertical = edgeInset),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
        ScrollCue(scrollState, pointsDown = false, slackPx = edgeInsetPx, modifier = Modifier.align(Alignment.TopCenter))
        ScrollCue(scrollState, pointsDown = true, slackPx = edgeInsetPx, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * A fade plus chevron on one edge of a scrolling column, shown only while content is hidden
 * past that edge. Without it a message that runs long just looks cut off, which is exactly the
 * failure the scrolling exists to prevent.
 *
 * [slackPx] is the padding the column keeps clear at each end for the round screen. Scroll
 * distance that is only that padding hides nothing, so it must not raise the cue: otherwise a
 * message that fits exactly would still carry a "more below" chevron over its button.
 */
@Composable
private fun ScrollCue(state: ScrollState, pointsDown: Boolean, slackPx: Float, modifier: Modifier = Modifier) {
    val hasMore by remember(state, pointsDown, slackPx) {
        derivedStateOf {
            // maxValue is Int.MAX_VALUE until the first layout; treat that as "nothing hidden".
            if (pointsDown) state.maxValue != Int.MAX_VALUE && state.maxValue - state.value > slackPx
            else state.value > slackPx
        }
    }
    AnimatedVisibility(visible = hasMore, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        val scrim = Brush.verticalGradient(
            if (pointsDown) listOf(Color.Transparent, Color.Black) else listOf(Color.Black, Color.Transparent)
        )
        Box(
            modifier = Modifier.fillMaxWidth().height(36.sdp).background(scrim),
            contentAlignment = if (pointsDown) Alignment.BottomCenter else Alignment.TopCenter
        ) {
            Canvas(modifier = Modifier.padding(vertical = 10.sdp).size(width = 16.sdp, height = 8.sdp)) {
                val stroke = 2.dp.toPx()
                val tipY = if (pointsDown) size.height else 0f
                val baseY = size.height - tipY
                val tint = Color.White.copy(alpha = 0.85f)
                drawLine(tint, Offset(0f, baseY), Offset(size.width / 2f, tipY), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width, baseY), Offset(size.width / 2f, tipY), stroke, StrokeCap.Round)
            }
        }
    }
}

@Composable
fun CircularProgressWithCenter(progress: Float, text: String, timerText: String, onOk: () -> Unit) {
    val dimens = wearDimens
    Box(modifier = Modifier.fillMaxSize()) {
        EdgeCircularProgress(progress = progress)
        MessageScrollColumn {
            AutoResizeText(
                text = text,
                modifier = Modifier.padding(horizontal = messageInset())
            )
            Spacer(Modifier.height(4.sdp))
            Text(timerText, fontSize = dimens.bodyTextSize)
            Spacer(Modifier.height(8.sdp))
            Button(onClick = onOk) { Text(stringResource(R.string.intervention_button_ok)) }
        }
    }
}

@Composable
fun EdgeCircularProgress(progress: Float, strokeWidth: Dp = 6.sdp) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokePx = strokeWidth.toPx()
        val radius = size.minDimension / 2f - strokePx / 2f
        drawArc(
            color = progressToColor(progress),
            startAngle = -90f,
            sweepAngle = progress * 360f,
            useCenter = false,
            style = Stroke(width = strokePx, cap = StrokeCap.Round),
            topLeft = Offset((size.width / 2f) - radius, (size.height / 2f) - radius),
            size = Size(radius * 2, radius * 2)
        )
    }
}

fun progressToColor(progress: Float): Color {
    val p = progress.coerceIn(0f, 1f)
    val hue = if (p <= 0.5f) lerp(120f, 60f, p / 0.5f) else lerp(60f, 0f, (p - 0.5f) / 0.5f)
    return Color.hsv(hue, 1f, 1f)
}

private fun lerp(start: Float, end: Float, t: Float) = start + (end - start) * t

/**
 * Message text that is as large as it can be and never cut off.
 *
 * It starts at [maxFontSize] and steps down while it needs more than [preferredLines] lines,
 * but never below [minFontSize], the smallest size that is still comfortable to read on a wrist
 * (12 sp, Wear's body-small, and never below the watch-scaled body size). A message that is
 * still longer at that size simply wraps onto more lines; it is the caller's scroll container,
 * not a line limit, that decides how much is visible. An earlier version capped the text at
 * three lines and clipped the rest, so the end of a long sentence was silently lost.
 */
@Composable
fun AutoResizeText(
    text: String,
    modifier: Modifier = Modifier,
    maxFontSize: TextUnit = wearDimens.titleTextSize,
    minFontSize: TextUnit = maxOf(wearDimens.bodyTextSize.value, 12f).sp,
    preferredLines: Int = 4
) {
    var fontSize by remember(text, maxFontSize, minFontSize) { mutableStateOf(maxFontSize) }
    Text(
        text = text,
        fontSize = fontSize,
        lineHeight = fontSize * 1.25f,
        textAlign = TextAlign.Center,
        // Paragraph breaking balances the lines so a sentence does not end on one orphaned
        // word; automatic hyphenation keeps long German compounds from breaking mid-letter.
        style = LocalTextStyle.current.copy(lineBreak = LineBreak.Paragraph, hyphens = Hyphens.Auto),
        modifier = modifier.fillMaxWidth(),
        onTextLayout = { result ->
            if (result.lineCount > preferredLines && fontSize.value > minFontSize.value) {
                fontSize = maxOf(fontSize.value * 0.92f, minFontSize.value).sp
            }
        }
    )
}
