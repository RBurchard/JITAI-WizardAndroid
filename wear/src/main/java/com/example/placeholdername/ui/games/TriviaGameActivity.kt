package com.example.jitaicompanion.ui.games

import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.example.jitaicompanion.R
import com.example.jitaicompanion.convention.Protocol
import com.example.jitaicompanion.convention.models.Intervention
import com.example.jitaicompanion.convention.trivia.TriviaQuestion
import com.example.jitaicompanion.ui.layout.sdp
import com.example.jitaicompanion.ui.layout.ssp
import com.example.jitaicompanion.ui.layout.wearDimens
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

class TriviaGameActivity : MicrogameActivity() {

    override val timeoutSeconds = 30
    override val tutorialTitleRes = R.string.game_trivia_title
    override val tutorialTextRes = R.string.game_trivia_tutorial

    private var question: TriviaQuestion? = null

    /** How long the answered board stays up before the activity hands back. */
    private val reviewDelayMs = 1900L

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
        // -1 until the player commits. Kept as the chosen *index* rather than a boolean so the
        // board can mark which option was picked as well as which one was right.
        var chosen by remember { mutableIntStateOf(-1) }
        var finished by remember { mutableStateOf(false) }
        val answered = chosen >= 0
        val wasCorrect = chosen == q.correctIndex

        // The old version reported the result inside the click handler, which finished the
        // activity in the same frame — so the "Correct!" screen it drew below was never on
        // screen for even one frame. The answer board now stays up long enough to read, and
        // only then hands back.
        LaunchedEffect(chosen) {
            if (!answered || finished) return@LaunchedEffect
            delay(reviewDelayMs)
            finished = true
            if (wasCorrect) onGameComplete("Trivia OK") else onGameFailed("Trivia FAIL")
        }

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
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 6.sdp)
                    )
                }
                items(q.answers.indices.toList()) { idx ->
                    AnswerButton(
                        letter = ANSWER_LETTERS.getOrElse(idx) { "${idx + 1}" },
                        answer = q.answers[idx],
                        state = when {
                            !answered -> AnswerState.OPEN
                            idx == q.correctIndex -> AnswerState.CORRECT
                            idx == chosen -> AnswerState.WRONG
                            else -> AnswerState.DIMMED
                        },
                        enabled = !answered,
                        onClick = {
                            chosen = idx
                            if (idx == q.correctIndex) {
                                vibratePulse()
                            } else {
                                vibratePattern(longArrayOf(0, 200, 90, 200))
                            }
                        },
                    )
                    Spacer(Modifier.height(dimens.itemSpacing))
                }
                if (answered) {
                    item {
                        Text(
                            text = if (wasCorrect) {
                                stringResource(R.string.game_trivia_correct)
                            } else {
                                stringResource(R.string.game_trivia_wrong)
                            },
                            fontSize = 14.ssp,
                            fontWeight = FontWeight.Bold,
                            color = if (wasCorrect) CORRECT_GREEN else WRONG_RED,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }

    private enum class AnswerState { OPEN, CORRECT, WRONG, DIMMED }

    /**
     * One answer row: a lettered badge and the answer text.
     *
     * The badge is the reason the letter is not just prefixed into the string — at 11 sp a
     * leading "A: " disappears into the sentence, whereas a filled disc gives every row the
     * same fixed anchor to aim a thumb at, and marks the graded state after the answer with a
     * ✓/✗ that does not depend on the row's colour.
     */
    @Composable
    private fun AnswerButton(
        letter: String,
        answer: String,
        state: AnswerState,
        enabled: Boolean,
        onClick: () -> Unit,
    ) {
        val dimens = wearDimens
        val container = when (state) {
            AnswerState.CORRECT -> CORRECT_GREEN.copy(alpha = 0.85f)
            AnswerState.WRONG -> WRONG_RED.copy(alpha = 0.85f)
            AnswerState.DIMMED -> Color(0xFF2A2F36).copy(alpha = 0.5f)
            AnswerState.OPEN -> Color(0xFF2A3A4F)
        }
        val badge = when (state) {
            AnswerState.CORRECT -> "✓"
            AnswerState.WRONG -> "✗"
            else -> letter
        }
        Button(
            onClick = onClick,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = container,
                contentColor = Color.White,
                disabledContainerColor = container,
                disabledContentColor = Color.White,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = dimens.minTouchTarget),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(20.sdp)
                        .background(Color.White.copy(alpha = 0.22f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(badge, fontSize = 11.ssp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(Modifier.width(7.sdp))
                Text(
                    text = answer,
                    fontSize = dimens.captionTextSize,
                    textAlign = TextAlign.Start,
                    maxLines = 2,
                )
            }
        }
    }

    private companion object {
        val ANSWER_LETTERS = listOf("A", "B", "C", "D")
        val CORRECT_GREEN = Color(0xFF2E7D32)
        val WRONG_RED = Color(0xFFC62828)
    }
}
