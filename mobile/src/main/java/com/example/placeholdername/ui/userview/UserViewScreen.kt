package com.BWPStudio.JITAIWizard.ui.userview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.BWPStudio.JITAIWizard.ui.components.LongPressProgressButton
import com.BWPStudio.JITAIWizard.ui.odi.OdiAnimationState
import com.BWPStudio.JITAIWizard.ui.odi.OdiCharacter
import com.BWPStudio.JITAIWizard.ui.odi.OdiConfetti
import com.BWPStudio.JITAIWizard.ui.odi.OdiDialogues
import kotlinx.coroutines.delay

@Composable
fun UserViewScreen(
    onDebugUnlocked: () -> Unit,
    viewModel: UserViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Tapping ODI throws a little celebration.
    var celebrating by remember { mutableStateOf(false) }
    LaunchedEffect(celebrating) {
        if (celebrating) { delay(2800); celebrating = false }
    }

    // ODI reacts to what's happening: an explicit mood wins, otherwise it naps
    // while the watch is away and frets when the heart rate spikes.
    val odiState = when {
        celebrating -> OdiAnimationState.CELEBRATING
        state.odiState != OdiAnimationState.IDLE -> state.odiState
        !state.watchConnected -> OdiAnimationState.SLEEPING
        state.lastBpm > 120 -> OdiAnimationState.CONCERNED
        else -> OdiAnimationState.IDLE
    }

    // Re-roll ODI's line on every mood change and every few seconds of idling.
    var messageTick by remember { mutableStateOf(0) }
    LaunchedEffect(odiState) {
        while (true) { delay(4200); messageTick++ }
    }
    val currentMessage = remember(odiState, messageTick) { OdiDialogues.forState(odiState) }

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0D1B2A)
        ) {
        Column(
            modifier = Modifier.fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))

            // Odi character — tap to make it celebrate
            OdiCharacter(
                state = odiState,
                size = 180.dp,
                modifier = Modifier.clickable(enabled = !celebrating) { celebrating = true }
            )

            Spacer(Modifier.height(8.dp))

            // Odi name + message
            Text("O D I", color = Color(0xFF42A5F5), fontSize = 13.sp, letterSpacing = 6.sp, fontWeight = FontWeight.Light)
            TypewriterText(
                text = currentMessage,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(Modifier.height(20.dp))

            // Stats card — long-press 3s to unlock debug
            LongPressProgressButton(onActivated = onDebugUnlocked) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2A3A))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatItem(label = "BPM", value = if (state.lastBpm > 0) "${state.lastBpm}" else "--")
                            StatItem(label = "Reaction", value = state.lastReactionTimeMs?.let { "${it}ms" } ?: "--")
                            StatItem(label = "Watch", value = if (state.watchConnected) "●" else "○", valueColor = if (state.watchConnected) Color.Green else Color.Gray)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (state.lastAction.isEmpty()) "No activity yet" else "Last: ${state.lastAction}",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp
                        )
                        if (state.sessionElapsedMs > 0) {
                            val elapsed = state.sessionElapsedMs / 1000
                            Text(
                                text = "Session: ${elapsed / 60}m ${elapsed % 60}s",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "JITAI Research System v1.0",
                color = Color.White.copy(alpha = 0.2f),
                fontSize = 10.sp
            )
        }
        }

        OdiConfetti(
            active = odiState == OdiAnimationState.CELEBRATING,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** Reveals [text] one character at a time, retyping whenever the text changes. */
@Composable
private fun TypewriterText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp
) {
    var shown by remember(text) { mutableStateOf("") }
    LaunchedEffect(text) {
        shown = ""
        for (i in text.indices) {
            shown = text.substring(0, i + 1)
            delay(28)
        }
    }
    Text(
        text = shown,
        color = color,
        fontSize = fontSize,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

@Composable
private fun StatItem(label: String, value: String, valueColor: Color = Color.White) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = valueColor, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
    }
}
