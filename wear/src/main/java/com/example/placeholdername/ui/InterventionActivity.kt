package com.example.jitaicompanion.ui

import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.example.jitaicompanion.convention.Protocol
import com.example.jitaicompanion.convention.models.Intervention
import com.example.jitaicompanion.convention.models.NotificationType
import com.example.jitaicompanion.datalayer.WearMessageSender
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
        vibrator.cancel()
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

        vibrator = getSystemService(Vibrator::class.java)
        applyVibration(intervention.notification)

        setContent {
            val sender = WearMessageSender(LocalContext.current)
            MaterialTheme {
                LaunchedEffect(Unit) {
                    delay(intervention.durationSeconds * 1000L)
                    sender.sendResponse("timeout")
                    finish()
                }
                when (intervention.type) {
                    "Timer" -> TimerScreen(intervention, sender) { finish() }
                    "Yes/No" -> YesNoScreen(intervention, sender) { finish() }
                    else -> TextScreen(intervention, sender) { finish() }
                }
            }
        }
    }

    private fun applyVibration(type: NotificationType) {
        // Vibration
        when (type) {
            NotificationType.VIBRATION1, NotificationType.VIBRATION_SOUND ->
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200, 100, 400), -1))
            NotificationType.VIBRATION2, NotificationType.VIBRATION_SOUND2 ->
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 100, 400, 100, 400, 100, 400, 100, 400, 100, 400), -1))
            NotificationType.ANNOY_VIB, NotificationType.ANNOY_VIBRATION_SOUND ->
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 100, 400), 0))
            else -> {}
        }
        // Sound
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

@Composable
private fun TextScreen(intervention: Intervention, sender: WearMessageSender, onFinish: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AutoResizeText(text = intervention.message, maxLines = 3, modifier = Modifier.fillMaxWidth().padding(5.dp))
            Spacer(Modifier.height(16.dp))
            Button(onClick = { sender.sendResponse("Done"); onFinish() }) { Text("OK") }
        }
    }
}

@Composable
private fun YesNoScreen(intervention: Intervention, sender: WearMessageSender, onFinish: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AutoResizeText(text = intervention.message, maxLines = 3, modifier = Modifier.fillMaxWidth().padding(5.dp))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { sender.sendResponse("Yes"); onFinish() }) { Text("Yes") }
                Button(onClick = { sender.sendResponse("No"); onFinish() }) { Text("No") }
            }
        }
    }
}

@Composable
private fun TimerScreen(intervention: Intervention, sender: WearMessageSender, onFinish: () -> Unit) {
    var progress by remember { mutableStateOf(0f) }
    val animatedProgress by animateFloatAsState(targetValue = progress)

    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        while (true) {
            val elapsed = System.currentTimeMillis() - start
            progress = (elapsed / (intervention.durationSeconds * 1000f)).coerceIn(0f, 1f)
            if (progress >= 1f) break
            delay(16L)
        }
    }

    val remaining = ((1f - animatedProgress) * intervention.durationSeconds).toInt()
    CircularProgressWithCenter(
        progress = animatedProgress,
        text = intervention.message,
        timerText = "$remaining s",
        onOk = { sender.sendResponse("Timer OK"); onFinish() }
    )
}

@Composable
fun CircularProgressWithCenter(progress: Float, text: String, timerText: String, onOk: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        EdgeCircularProgress(progress = progress)
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AutoResizeText(text = text, maxLines = 3, modifier = Modifier.fillMaxWidth().padding(5.dp))
            Spacer(Modifier.height(4.dp))
            Text(timerText)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onOk) { Text("OK") }
        }
    }
}

@Composable
fun EdgeCircularProgress(progress: Float, strokeWidth: Dp = 6.dp) {
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
    maxFontSize: TextUnit = 18.sp,
    minFontSize: TextUnit = 4.sp,
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
