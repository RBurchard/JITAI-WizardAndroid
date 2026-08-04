package com.example.jitaicompanion.ui.games

import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.example.jitaicompanion.R
import com.example.jitaicompanion.convention.Protocol
import com.example.jitaicompanion.convention.models.Intervention
import com.example.jitaicompanion.convention.trivia.TriviaQuestion
import com.example.jitaicompanion.ui.layout.ssp
import com.example.jitaicompanion.ui.layout.wearDimens
import kotlinx.serialization.json.Json

class TriviaGameActivity : MicrogameActivity() {

    override val timeoutSeconds = 30
    override val tutorialTitleRes = R.string.game_trivia_title
    override val tutorialTextRes = R.string.game_trivia_tutorial

    private var question: TriviaQuestion? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val interventionJson = intent.getStringExtra(Protocol.KEY_INTERVENTION) ?: ""
        // Question may be embedded in intervention message as JSON, or fall back to a default
        question = try {
            Json.decodeFromString<Intervention>(interventionJson).triviaQuestion
        } catch (e: Exception) { null } ?: buildFallbackQuestions().random()
    }

    /**
     * Fallback questions used only when no question arrives from the intervention.
     * Built here (not as a top-level val) because resolving localized copy needs Activity
     * [getString]/[getResources] — not available at class-init time for a top-level property.
     * `correctIndex` is a plain literal tied 1:1 to each question, independent of the answer
     * array's item order, so a translator reordering a `<string-array>` can't change which
     * answer scores as correct.
     */
    private fun buildFallbackQuestions(): List<TriviaQuestion> = listOf(
        TriviaQuestion(
            question = getString(R.string.trivia_fallback_q1_question),
            answers = resources.getStringArray(R.array.trivia_fallback_q1_answers).toList(),
            correctIndex = 1
        ),
        TriviaQuestion(
            question = getString(R.string.trivia_fallback_q2_question),
            answers = listOf("5", "6", "7", "8"),
            correctIndex = 1
        ),
        TriviaQuestion(
            question = getString(R.string.trivia_fallback_q3_question),
            answers = resources.getStringArray(R.array.trivia_fallback_q3_answers).toList(),
            correctIndex = 2
        ),
        TriviaQuestion(
            question = getString(R.string.trivia_fallback_q4_question),
            answers = resources.getStringArray(R.array.trivia_fallback_q4_answers).toList(),
            correctIndex = 3
        ),
        TriviaQuestion(
            question = getString(R.string.trivia_fallback_q5_question),
            answers = listOf("54", "56", "48", "64"),
            correctIndex = 1
        ),
        TriviaQuestion(
            question = getString(R.string.trivia_fallback_q6_question),
            answers = listOf("5", "6", "7", "8"),
            correctIndex = 2
        ),
        TriviaQuestion(
            question = getString(R.string.trivia_fallback_q7_question),
            answers = resources.getStringArray(R.array.trivia_fallback_q7_answers).toList(),
            correctIndex = 2
        ),
        TriviaQuestion(
            question = getString(R.string.trivia_fallback_q8_question),
            answers = resources.getStringArray(R.array.trivia_fallback_q8_answers).toList(),
            correctIndex = 1
        ),
        TriviaQuestion(
            question = getString(R.string.trivia_fallback_q9_question),
            answers = listOf("30", "45", "60", "90"),
            correctIndex = 2
        ),
        TriviaQuestion(
            question = getString(R.string.trivia_fallback_q10_question),
            answers = resources.getStringArray(R.array.trivia_fallback_q10_answers).toList(),
            correctIndex = 3
        )
    )

    @Composable
    override fun GameContent() {
        val q = question ?: return
        var answered by remember { mutableStateOf(false) }
        // Driven directly by a boolean set at the moment the answer is checked — never by
        // matching against the (localized) result text — so translating "Correct! ★" can't
        // make every right answer render red (see game_trivia_correct).
        var wasCorrect by remember { mutableStateOf(false) }
        // Only the raw substituted answer is stored; the surrounding sentence is resolved
        // from the resource at render time so the value itself (study data or fallback
        // copy) is never touched.
        var correctAnswerText by remember { mutableStateOf("") }

        MaterialTheme {
            val dimens = wearDimens
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 16.dp)
            ) {
                item {
                    Text(
                        text = q.question,
                        fontSize = 13.ssp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                if (!answered) {
                    items(q.answers.indices.toList()) { idx ->
                        Button(
                            onClick = {
                                answered = true
                                if (idx == q.correctIndex) {
                                    wasCorrect = true
                                    vibratePulse()
                                    onGameComplete("Trivia OK")
                                } else {
                                    wasCorrect = false
                                    correctAnswerText = q.answers[q.correctIndex]
                                    onGameFailed("Trivia FAIL")
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = dimens.itemSpacing)
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.game_trivia_answer_option,
                                    listOf("A", "B", "C", "D")[idx],
                                    q.answers[idx]
                                ),
                                fontSize = dimens.captionTextSize,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    item {
                        Text(
                            text = if (wasCorrect) {
                                stringResource(R.string.game_trivia_correct)
                            } else {
                                stringResource(R.string.game_trivia_wrong_answer_was, correctAnswerText)
                            },
                            fontSize = 14.ssp,
                            color = if (wasCorrect) Color.Green else Color.Red,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
