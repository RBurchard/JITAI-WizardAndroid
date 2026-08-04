package com.example.jitaicompanion.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.example.jitaicompanion.R
import com.example.jitaicompanion.ui.layout.sdp
import com.example.jitaicompanion.ui.layout.wearDimens
import kotlinx.coroutines.delay
import androidx.compose.runtime.key

class SimonSaysGameActivity : MicrogameActivity() {

    override val timeoutSeconds = 60
    override val tutorialTitleRes = R.string.game_simonsays_title
    override val tutorialTextRes = R.string.game_simonsays_tutorial

    private val quadrantColors = listOf(Color.Red, Color.Blue, Color.Green, Color.Yellow)

    enum class Phase { SHOW, INPUT, RESULT }

    @Composable
    override fun GameContent() {
        var gameRestartCount by remember { mutableIntStateOf(0) }

        key(gameRestartCount) {
            var sequence by remember { mutableStateOf(listOf(quadrantColors.indices.random())) }
            var phase by remember { mutableStateOf(Phase.SHOW) }
            var playerInput by remember { mutableStateOf(listOf<Int>()) }
            var highlightedIndex by remember { mutableStateOf(-1) }
            // Display-only status text. Built with getString() (not stringResource()) because
            // it's assigned from LaunchedEffect/clickable callbacks, which are plain, non-@Composable
            // lambdas — getString() works there since this is an Activity/Context either way.
            var resultText by remember { mutableStateOf(getString(R.string.game_simonsays_watch_carefully)) }
            var round by remember { mutableStateOf(1) }
            var shouldRestart by remember { mutableStateOf(false) }
            val totalRounds = 4

            LaunchedEffect(shouldRestart) {
                if (shouldRestart) {
                    delay(1500L)
                    resetCompletion()
                    gameRestartCount++
                }
            }

            LaunchedEffect(phase, sequence) {
                if (phase == Phase.SHOW) {
                    delay(600L)
                    for (idx in sequence) {
                        highlightedIndex = idx
                        vibratePattern(longArrayOf(0, 100), -1)
                        delay(500L)
                        vibrator.cancel()
                        highlightedIndex = -1
                        delay(300L)
                    }
                    phase = Phase.INPUT
                    resultText = getString(R.string.game_simonsays_your_turn_round, round, totalRounds)
                }
            }

            MaterialTheme {
                val dimens = wearDimens
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = resultText, fontSize = dimens.captionTextSize, textAlign = TextAlign.Center, modifier = Modifier.padding(4.dp))
                        Spacer(Modifier.height(4.dp))
                        // 2×2 grid of colored squares
                        Column {
                            for (row in 0..1) {
                                Row {
                                    for (col in 0..1) {
                                        val idx = row * 2 + col
                                        val isLit = highlightedIndex == idx
                                        Box(
                                            modifier = Modifier
                                                // .clickable must apply to the full 52dp box, before
                                                // .padding shrinks the visible/tappable area — otherwise
                                                // the real tap target is only 46dp and a missed edge tap
                                                // on a small watch gets scored as a wrong answer.
                                                .size(maxOf(52.sdp, dimens.minTouchTarget))
                                                .clickable(enabled = phase == Phase.INPUT) {
                                                    val newInput = playerInput + idx
                                                    playerInput = newInput
                                                    vibratePulse()
                                                    val expectedSoFar = sequence.take(newInput.size)
                                                    if (newInput != expectedSoFar) {
                                                        phase = Phase.RESULT
                                                        resultText = getString(R.string.game_simonsays_wrong_restarting)
                                                        shouldRestart = true
                                                    } else if (newInput.size == sequence.size) {
                                                        if (round >= totalRounds) {
                                                            phase = Phase.RESULT
                                                            resultText = getString(R.string.game_result_perfect)
                                                            onGameComplete("SimonSays OK")
                                                        } else {
                                                            round++
                                                            sequence = sequence + listOf(quadrantColors.indices.random())
                                                            playerInput = listOf()
                                                            phase = Phase.SHOW
                                                            resultText = getString(R.string.game_simonsays_good_watch)
                                                        }
                                                    }
                                                }
                                                .padding(3.dp)
                                                .background(
                                                    if (isLit) quadrantColors[idx]
                                                    else quadrantColors[idx].copy(alpha = 0.35f)
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
