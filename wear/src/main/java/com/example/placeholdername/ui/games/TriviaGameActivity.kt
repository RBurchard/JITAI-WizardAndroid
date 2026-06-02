package com.example.jitaicompanion.ui.games

import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.example.jitaicompanion.convention.Protocol
import com.example.jitaicompanion.convention.models.Intervention
import com.example.jitaicompanion.convention.trivia.TriviaQuestion
import kotlinx.serialization.json.Json

class TriviaGameActivity : MicrogameActivity() {

    override val timeoutSeconds = 30
    override val tutorialTitle = "Trivia"
    override val tutorialText =
        "Read the question, then tap the answer (A, B, C or D) you think is correct."

    private var question: TriviaQuestion? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val interventionJson = intent.getStringExtra(Protocol.KEY_INTERVENTION) ?: ""
        // Question may be embedded in intervention message as JSON, or fall back to a default
        question = try {
            Json.decodeFromString<Intervention>(interventionJson).triviaQuestion
        } catch (e: Exception) { null } ?: FallbackQuestions.random()
    }

    @Composable
    override fun GameContent() {
        val q = question ?: return
        var answered by remember { mutableStateOf(false) }
        var resultMessage by remember { mutableStateOf("") }

        MaterialTheme {
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 16.dp)
            ) {
                item {
                    Text(
                        text = q.question,
                        fontSize = 13.sp,
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
                                    resultMessage = "Correct! ★"
                                    vibratePulse()
                                    onGameComplete("Trivia OK")
                                } else {
                                    resultMessage = "Wrong! The answer was: ${q.answers[q.correctIndex]}"
                                    onGameFailed("Trivia FAIL")
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = "${listOf("A", "B", "C", "D")[idx]}: ${q.answers[idx]}",
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    item {
                        Text(
                            text = resultMessage,
                            fontSize = 14.sp,
                            color = if (resultMessage.startsWith("Correct")) Color.Green else Color.Red,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

private val FallbackQuestions = listOf(
    TriviaQuestion(question = "What is the capital of France?", answers = listOf("Berlin", "Paris", "Madrid", "Rome"), correctIndex = 1),
    TriviaQuestion(question = "How many sides does a hexagon have?", answers = listOf("5", "6", "7", "8"), correctIndex = 1),
    TriviaQuestion(question = "What color is the sky on a clear day?", answers = listOf("Green", "Red", "Blue", "Yellow"), correctIndex = 2),
    TriviaQuestion(question = "Which planet is closest to the Sun?", answers = listOf("Venus", "Earth", "Mars", "Mercury"), correctIndex = 3),
    TriviaQuestion(question = "What is 7 × 8?", answers = listOf("54", "56", "48", "64"), correctIndex = 1),
    TriviaQuestion(question = "How many continents are there?", answers = listOf("5", "6", "7", "8"), correctIndex = 2),
    TriviaQuestion(question = "What is H2O commonly known as?", answers = listOf("Salt", "Sugar", "Water", "Acid"), correctIndex = 2),
    TriviaQuestion(question = "Which animal is known as man's best friend?", answers = listOf("Cat", "Dog", "Horse", "Rabbit"), correctIndex = 1),
    TriviaQuestion(question = "How many minutes in an hour?", answers = listOf("30", "45", "60", "90"), correctIndex = 2),
    TriviaQuestion(question = "What is the largest ocean?", answers = listOf("Atlantic", "Indian", "Arctic", "Pacific"), correctIndex = 3)
)
