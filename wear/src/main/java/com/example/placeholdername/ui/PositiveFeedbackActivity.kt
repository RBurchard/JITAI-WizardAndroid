package com.example.jitaicompanion.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.example.jitaicompanion.ui.odi.OdiAnimationState
import com.example.jitaicompanion.ui.odi.OdiCharacter
import com.example.jitaicompanion.ui.odi.OdiConfetti
import kotlinx.coroutines.delay

class PositiveFeedbackActivity : ComponentActivity() {

    private val messages = listOf(
        "You resisted!",
        "Excellent control!",
        "Mind over matter!",
        "Strong work!",
        "Keep it up!",
        "Well done!",
        "You did it!"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val message = messages.random()

        setContent {
            MaterialTheme {
                LaunchedEffect(Unit) {
                    delay(3000L)
                    finish()
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1B5E20)),
                    contentAlignment = Alignment.Center
                ) {
                    OdiConfetti(active = true, modifier = Modifier.fillMaxSize())
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        OdiCharacter(state = OdiAnimationState.CELEBRATING, size = 120.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Odi says: $message",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}
