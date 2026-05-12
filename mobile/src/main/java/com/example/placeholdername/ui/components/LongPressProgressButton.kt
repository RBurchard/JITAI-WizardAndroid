package com.BWPStudio.JITAIWizard.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LongPressProgressButton(
    onActivated: () -> Unit,
    holdDurationMs: Long = 3000L,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var progress by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()
    var holdJob by remember { mutableStateOf<Job?>(null) }

    Box(
        modifier = modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitFirstDown(requireUnconsumed = false)
                    holdJob = scope.launch {
                        val start = System.currentTimeMillis()
                        while (true) {
                            progress = ((System.currentTimeMillis() - start).toFloat() / holdDurationMs).coerceIn(0f, 1f)
                            if (progress >= 1f) {
                                onActivated()
                                progress = 0f
                                break
                            }
                            delay(16)
                        }
                    }
                    waitForUpOrCancellation()
                    holdJob?.cancel()
                    holdJob = null
                    progress = 0f
                }
            }
        }
    ) {
        content()
        if (progress > 0f) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.matchParentSize().padding(4.dp),
                color = Color.White.copy(alpha = 0.6f),
                trackColor = Color.Transparent,
                strokeWidth = 3.dp
            )
            Box(modifier = Modifier.align(Alignment.Center)) {
                // Dim overlay hint
            }
        }
    }
}
