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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
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
    val dimens = wearDimens
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.verticalScroll(rememberScrollState())
        ) {
            AutoResizeText(
                text = intervention.message,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth().padding(dimens.horizontalPadding)
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
}

@Composable
private fun YesNoScreen(
    intervention: Intervention,
    onAccepted: (String) -> Unit,
    onDeclined: (String) -> Unit
) {
    val dimens = wearDimens
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.verticalScroll(rememberScrollState())
        ) {
            AutoResizeText(
                text = intervention.message,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth().padding(dimens.horizontalPadding)
            )
            Spacer(Modifier.height(12.sdp))
            Row(
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

@Composable
fun CircularProgressWithCenter(progress: Float, text: String, timerText: String, onOk: () -> Unit) {
    val dimens = wearDimens
    Box(modifier = Modifier.fillMaxSize()) {
        EdgeCircularProgress(progress = progress)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(dimens.contentPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AutoResizeText(
                text = text,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth().padding(dimens.horizontalPadding)
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

@Composable
fun AutoResizeText(
    text: String,
    modifier: Modifier = Modifier,
    maxFontSize: TextUnit = wearDimens.titleTextSize,
    minFontSize: TextUnit = maxOf(wearDimens.captionTextSize.value, 10f).sp,
    maxLines: Int = 3
) {
    var fontSize by remember { mutableStateOf(maxFontSize) }
    Text(
        text = text,
        maxLines = maxLines,
        softWrap = true,
        overflow = TextOverflow.Clip,
        fontSize = fontSize,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
        onTextLayout = { result ->
            if (result.hasVisualOverflow && fontSize > minFontSize) fontSize *= 0.9f
        }
    )
}
