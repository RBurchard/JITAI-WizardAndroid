package com.example.jitaicompanion.ui.games

import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.example.jitaicompanion.R
import com.example.jitaicompanion.datalayer.WearMessageSender
import com.example.jitaicompanion.ui.layout.ProvideWearDimens
import com.example.jitaicompanion.ui.layout.sdp
import com.example.jitaicompanion.ui.layout.wearDimens
import com.example.jitaicompanion.ui.odi.OdiAnimationState
import com.example.jitaicompanion.ui.odi.OdiCharacter
import com.example.jitaicompanion.ui.PositiveFeedbackActivity

abstract class MicrogameActivity : ComponentActivity() {

    companion object {
        @Volatile private var currentInstance: MicrogameActivity? = null
        fun finishCurrent() { currentInstance?.finish() }
    }

    protected lateinit var sender: WearMessageSender
    protected lateinit var vibrator: Vibrator
    private var isCompleted = false

    abstract val timeoutSeconds: Int

    /** Short title shown above the how-to-play text on the tutorial screen. */
    open val tutorialTitleRes: Int = R.string.microgame_tutorial_title_default

    /** Game-specific explanation so a first-time user knows exactly what to do. */
    open val tutorialTextRes: Int = R.string.microgame_tutorial_text_default

    @Composable
    abstract fun GameContent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentInstance = this
        sender = WearMessageSender(this)
        vibrator = getSystemService(Vibrator::class.java)

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent { ProvideWearDimens { GameWithTutorial() } }
    }

    @Composable
    private fun GameWithTutorial() {
        // The tutorial stays up until the user taps "Start" so a first-time player has
        // time to actually read how the game works (the old 2s auto-dismiss was far too
        // short to read the explanation).
        var showTutorial by remember { mutableStateOf(true) }

        if (showTutorial) {
            OdiTutorialScreen { showTutorial = false }
        } else {
            GameContent()
        }
    }

    @Composable
    private fun OdiTutorialScreen(onStart: () -> Unit) {
        MaterialTheme {
            val dimens = wearDimens
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = dimens.contentPadding
            ) {
                item { OdiCharacter(state = OdiAnimationState.TALKING) }
                item { Spacer(Modifier.height(4.sdp)) }
                item {
                    Text(
                        text = stringResource(tutorialTitleRes),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                }
                item { Spacer(Modifier.height(4.sdp)) }
                item {
                    Text(
                        text = stringResource(tutorialTextRes),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
                item { Spacer(Modifier.height(10.sdp)) }
                item {
                    Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.microgame_tutorial_start_button))
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (currentInstance === this) currentInstance = null
    }

    protected fun resetCompletion() { isCompleted = false }

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
