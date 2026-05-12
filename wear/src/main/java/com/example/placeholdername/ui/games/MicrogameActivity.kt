package com.example.jitaicompanion.ui.games

import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.example.jitaicompanion.datalayer.WearMessageSender
import com.example.jitaicompanion.ui.odi.OdiAnimationState
import com.example.jitaicompanion.ui.odi.OdiCharacter
import com.example.jitaicompanion.ui.PositiveFeedbackActivity
import kotlinx.coroutines.delay

abstract class MicrogameActivity : ComponentActivity() {

    protected lateinit var sender: WearMessageSender
    protected lateinit var vibrator: Vibrator
    private var isCompleted = false

    abstract val timeoutSeconds: Int
    open val tutorialText: String = "Quick tip: follow the instructions."

    @Composable
    abstract fun GameContent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sender = WearMessageSender(this)
        vibrator = getSystemService(Vibrator::class.java)

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent { GameWithTutorial() }
    }

    @Composable
    private fun GameWithTutorial() {
        var showTutorial by remember { mutableStateOf(true) }

        LaunchedEffect(Unit) {
            delay(2000L)
            showTutorial = false
        }

        if (showTutorial) {
            OdiTutorialScreen(message = tutorialText) { showTutorial = false }
        } else {
            GameContent()
        }
    }

    @Composable
    private fun OdiTutorialScreen(message: String, onStart: () -> Unit) {
        MaterialTheme {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                OdiCharacter(state = OdiAnimationState.TALKING)
                Spacer(Modifier.height(6.dp))
                Text(text = message, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Button(onClick = onStart) { Text("Start") }
            }
        }
    }

    protected fun onGameComplete(response: String) {
        if (isCompleted) return
        isCompleted = true
        sender.sendResponse(response)
        startActivity(Intent(this, PositiveFeedbackActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
        finish()
    }

    protected fun onGameFailed(response: String = "FAIL") {
        if (isCompleted) return
        isCompleted = true
        sender.sendResponse(response)
        finish()
    }

    protected fun vibratePulse() {
        vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    protected fun vibratePattern(pattern: LongArray, repeat: Int = -1) {
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, repeat))
    }
}
