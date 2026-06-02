package com.example.jitaicompanion.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.delay
import androidx.compose.runtime.key

class SimonSaysGameActivity : MicrogameActivity() {

    override val timeoutSeconds = 60
    override val tutorialTitle = "Simon Says"
    override val tutorialText =
        "Watch which colored squares light up, then tap them back in the same order. " +
        "The sequence gets one step longer each round — clear 4 rounds to win!"

    private val quadrantColors = listOf(Color.Red, Color.Blue, Color.Green, Color.Yellow)
    private val colorNames = listOf("Red", "Blue", "Green", "Yellow")

    enum class Phase { SHOW, INPUT, RESULT }

    @Composable
    override fun GameContent() {
        var gameRestartCount by remember { mutableIntStateOf(0) }

        key(gameRestartCount) {
            var sequence by remember { mutableStateOf(listOf(quadrantColors.indices.random())) }
            var phase by remember { mutableStateOf(Phase.SHOW) }
            var playerInput by remember { mutableStateOf(listOf<Int>()) }
            var highlightedIndex by remember { mutableStateOf(-1) }
            var resultText by remember { mutableStateOf("Watch carefully!") }
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
                    resultText = "Your turn! Round $round/$totalRounds"
                }
            }

            MaterialTheme {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = resultText, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(4.dp))
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
                                                .size(52.dp)
                                                .padding(3.dp)
                                                .background(
                                                    if (isLit) quadrantColors[idx]
                                                    else quadrantColors[idx].copy(alpha = 0.35f)
                                                )
                                                .clickable(enabled = phase == Phase.INPUT) {
                                                    val newInput = playerInput + idx
                                                    playerInput = newInput
                                                    vibratePulse()
                                                    val expectedSoFar = sequence.take(newInput.size)
                                                    if (newInput != expectedSoFar) {
                                                        phase = Phase.RESULT
                                                        resultText = "Wrong! Restarting..."
                                                        shouldRestart = true
                                                    } else if (newInput.size == sequence.size) {
                                                        if (round >= totalRounds) {
                                                            phase = Phase.RESULT
                                                            resultText = "Perfect!"
                                                            onGameComplete("SimonSays OK")
                                                        } else {
                                                            round++
                                                            sequence = sequence + listOf(quadrantColors.indices.random())
                                                            playerInput = listOf()
                                                            phase = Phase.SHOW
                                                            resultText = "Good! Watch..."
                                                        }
                                                    }
                                                }
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
