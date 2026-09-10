package com.example.jitaicompanion.ui.games

import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.example.jitaicompanion.R
import com.example.jitaicompanion.datalayer.WearMessageSender
import com.example.jitaicompanion.ui.layout.ProvideWearDimens
import com.example.jitaicompanion.ui.layout.sdp
import com.example.jitaicompanion.ui.layout.ssp
import com.example.jitaicompanion.ui.layout.wearDimens
import com.example.jitaicompanion.ui.odi.OdiAnimationState
import com.example.jitaicompanion.ui.odi.OdiCharacter
import com.example.jitaicompanion.ui.PositiveFeedbackActivity

abstract class MicrogameActivity : ComponentActivity() {

    companion object {
        @Volatile private var currentInstance: MicrogameActivity? = null
        fun finishCurrent() { currentInstance?.finish() }

        /**
         * Launches the game as a free-play round: nothing is reported to the phone and no
         * study data is produced. Set by the watch's own Settings → Practice entry so a
         * participant can play while the researcher is still setting the session up.
         */
        const val EXTRA_PRACTICE = "practice_mode"
    }

    protected lateinit var sender: WearMessageSender
    protected lateinit var vibrator: Vibrator
    private var isCompleted = false

    /** True when this round is free play — see [EXTRA_PRACTICE]. */
    protected val isPractice: Boolean by lazy { intent.getBooleanExtra(EXTRA_PRACTICE, false) }

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

    /**
     * Two-step opening: **ask before teaching**.
     *
     * A returning player has read the same three sentences on every previous intervention, and
     * making them read again before every round is the fastest way to make a microgame feel
     * like a chore. So the first screen is a yes/no question from ODI, and only someone who
     * says "not really" is taken to the instructions.
     */
    @Composable
    private fun GameWithTutorial() {
        var step by remember { mutableStateOf(TutorialStep.ASK) }

        MaterialTheme {
            when (step) {
                TutorialStep.ASK -> TutorialPrompt(
                    onSkip = { step = TutorialStep.PLAYING },
                    onExplain = { step = TutorialStep.EXPLAIN },
                )
                TutorialStep.EXPLAIN -> TutorialText(onStart = { step = TutorialStep.PLAYING })
                TutorialStep.PLAYING -> GameContent()
            }
        }
    }

    private enum class TutorialStep { ASK, EXPLAIN, PLAYING }

    /** Step 1 — ODI asks whether the tutorial is needed at all. */
    @Composable
    private fun TutorialPrompt(onSkip: () -> Unit, onExplain: () -> Unit) {
        val dimens = wearDimens
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // Deliberately more bottom padding than the screen needs. The second button
                // otherwise comes to rest near the foot of a round display, where the glass
                // curves in and clips its ends; the extra room lets the user scroll it up into
                // the wide middle of the screen instead.
                .padding(
                    start = dimens.horizontalPadding,
                    end = dimens.horizontalPadding,
                    top = dimens.verticalPadding,
                    bottom = dimens.verticalPadding + 40.sdp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            OdiCharacter(state = OdiAnimationState.TALKING, size = dimens.scaled(72.dp))
            Spacer(Modifier.height(4.sdp))
            Text(
                text = stringResource(tutorialTitleRes),
                fontSize = dimens.bodyTextSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(2.sdp))
            Text(
                text = stringResource(R.string.microgame_tutorial_ask_known),
                fontSize = dimens.captionTextSize,
                color = Color(0xFFB0BEC5),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.sdp))
            // "I know it" first: it is the common case, and it sits under the thumb.
            Button(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.microgame_tutorial_skip), fontSize = dimens.bodyTextSize)
            }
            Spacer(Modifier.height(dimens.itemSpacing))
            Button(onClick = onExplain, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.microgame_tutorial_explain), fontSize = dimens.bodyTextSize)
            }
        }
    }

    /**
     * Step 2 — the instructions themselves.
     *
     * ODI is deliberately gone here: the face costs about a third of a 1.2" screen, and every
     * pixel it takes has to come out of the text the player actually needs to read. Without it
     * the copy fits at a comfortable size, and the "go" arrow moves to the right edge where it
     * does not sit under the words.
     */
    @Composable
    private fun TutorialText(onStart: () -> Unit) {
        val dimens = wearDimens
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    // Extra end padding reserves the arrow button's column so long lines never
                    // run underneath it.
                    .padding(
                        start = dimens.horizontalPadding,
                        end = dimens.horizontalPadding + 34.sdp,
                        top = dimens.verticalPadding + 8.sdp,
                        bottom = dimens.verticalPadding + 8.sdp,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(tutorialTitleRes),
                    fontSize = 15.ssp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.sdp))
                Text(
                    text = stringResource(tutorialTextRes),
                    fontSize = 14.ssp,
                    textAlign = TextAlign.Center,
                )
            }
            FilledIconButton(
                onClick = onStart,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.sdp)
                    .size(44.sdp),
            ) {
                Text("›", fontSize = 24.ssp, fontWeight = FontWeight.Bold)
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
        // A practice round is not study data: it never reaches the phone, so a participant
        // warming up cannot be mistaken for a completed intervention.
        if (!isPractice) sender.sendResponse(response)
        startActivity(Intent(this, PositiveFeedbackActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
        finish()
    }

    protected fun onGameFailed(response: String = "FAIL") {
        if (isCompleted) return
        isCompleted = true
        if (!isPractice) sender.sendResponse(response)
        finish()
    }

    protected fun vibratePulse() {
        vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    protected fun vibratePattern(pattern: LongArray, repeat: Int = -1) {
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, repeat))
    }

    /**
     * A single buzz at a controlled strength (1..255).
     *
     * The safe-cracking stage needs *proportional* haptics — a faint tick far from the notch
     * rising to a hard thud on it — which [vibratePulse]'s DEFAULT_AMPLITUDE cannot express.
     * Falls back to the default amplitude on hardware without amplitude control, where the
     * player still gets the rhythm (ticks speed up near the notch) even without the strength.
     */
    protected fun vibrateAmplitude(durationMs: Long, amplitude: Int) {
        val amp = amplitude.coerceIn(1, 255)
        val effect = if (vibrator.hasAmplitudeControl()) {
            VibrationEffect.createOneShot(durationMs, amp)
        } else {
            VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        vibrator.vibrate(effect)
    }
}
