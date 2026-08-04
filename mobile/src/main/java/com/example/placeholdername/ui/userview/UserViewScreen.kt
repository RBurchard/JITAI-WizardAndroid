package com.BWPStudio.JITAIWizard.ui.userview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.BWPStudio.JITAIWizard.BuildConfig
import com.BWPStudio.JITAIWizard.R
import com.BWPStudio.JITAIWizard.ui.components.LongPressProgressButton
import com.BWPStudio.JITAIWizard.ui.odi.OdiAnimationState
import com.BWPStudio.JITAIWizard.ui.odi.OdiCharacter
import com.BWPStudio.JITAIWizard.ui.odi.OdiConfetti
import com.BWPStudio.JITAIWizard.ui.odi.OdiDialogues
import kotlinx.coroutines.delay
import kotlin.math.max

@Composable
fun UserViewScreen(
    onDebugUnlocked: () -> Unit,
    onSettingsClick: () -> Unit = {},
    viewModel: UserViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

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
    // The re-roll delay scales with the current line's length so longer translated
    // strings (e.g. German/Dutch) aren't cut off mid-word by the typewriter effect.
    var messageTick by remember { mutableStateOf(0) }
    val currentMessage = remember(odiState, messageTick) { OdiDialogues.forState(context, odiState) }
    val latestMessage = rememberUpdatedState(currentMessage)
    LaunchedEffect(odiState) {
        while (true) {
            delay(max(4200L, latestMessage.value.length * 120L))
            messageTick++
        }
    }

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
            Text(stringResource(R.string.userview_odi_name), color = Color(0xFF42A5F5), fontSize = 13.sp, letterSpacing = 6.sp, fontWeight = FontWeight.Light)
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
                            StatItem(
                                label = stringResource(R.string.userview_stat_bpm_label),
                                value = if (state.lastBpm > 0) "${state.lastBpm}" else stringResource(R.string.userview_stat_value_none)
                            )
                            StatItem(
                                label = stringResource(R.string.userview_stat_reaction_label),
                                value = state.lastReactionTimeMs?.let { stringResource(R.string.userview_stat_reaction_value_ms, it) }
                                    ?: stringResource(R.string.userview_stat_value_none)
                            )
                            StatItem(
                                label = stringResource(R.string.userview_stat_watch_label),
                                value = if (state.watchConnected) "●" else "○",
                                valueColor = if (state.watchConnected) Color.Green else Color.Gray,
                                valueContentDescription = if (state.watchConnected) {
                                    stringResource(R.string.userview_watch_connected_desc)
                                } else {
                                    stringResource(R.string.userview_watch_disconnected_desc)
                                }
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (state.lastAction.isEmpty()) {
                                stringResource(R.string.userview_last_action_empty)
                            } else {
                                stringResource(R.string.userview_last_action, state.lastAction)
                            },
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp
                        )
                        if (state.sessionElapsedMs > 0) {
                            val elapsed = state.sessionElapsedMs / 1000
                            Text(
                                text = stringResource(R.string.userview_session_elapsed, elapsed / 60, elapsed % 60),
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                color = Color.White.copy(alpha = 0.2f),
                fontSize = 10.sp
            )
        }
        }

        OdiConfetti(
            active = odiState == OdiAnimationState.CELEBRATING,
            modifier = Modifier.fillMaxSize()
        )

        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .safeDrawingPadding()
                .align(Alignment.TopEnd)
        ) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = stringResource(R.string.settings_title),
                tint = Color.White.copy(alpha = 0.6f)
            )
        }
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
private fun StatItem(
    label: String,
    value: String,
    valueColor: Color = Color.White,
    valueContentDescription: String? = null
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = valueColor,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = if (valueContentDescription != null) {
                Modifier.semantics { contentDescription = valueContentDescription }
            } else {
                Modifier
            }
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
