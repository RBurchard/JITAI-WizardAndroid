package com.BWPStudio.JITAIWizard.ui.debugview

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.BWPStudio.JITAIWizard.datalayer.WearMessageSender
import com.BWPStudio.JITAIWizard.server.ServerState
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun DebugViewScreen(onBack: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Control", "Experiment")
    var wearErrorMessage by remember { mutableStateOf<String?>(null) }

    wearErrorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { wearErrorMessage = null },
            title = { Text("WearOS Error") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { wearErrorMessage = null }) { Text("OK") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.errorContainer, tonalElevation = 4.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("DEBUG MODE", color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = onBack) {
                    Text("Back", color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        WatchStatusBar()

        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = selectedTab == index, onClick = { selectedTab = index },
                    text = { Text(title) })
            }
        }

        when (selectedTab) {
            0 -> ControlScreen(onWearError = { wearErrorMessage = it })
            1 -> ExperimentScreen(onWearError = { wearErrorMessage = it })
        }
    }
}

@Composable
private fun WatchStatusBar() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // null = still checking, empty = not found, non-empty = found nodes
    var watchNodes by remember { mutableStateOf<List<Node>?>(null) }
    var pingStatus by remember { mutableStateOf("") }
    var bpm by remember { mutableFloatStateOf(ServerState.lastHeartRate) }
    var lastAction by remember { mutableStateOf(ServerState.lastAction) }

    LaunchedEffect(Unit) {
        while (true) {
            watchNodes = try {
                // FILTER_REACHABLE tests the live connection — no stale cache
                Wearable.getCapabilityClient(context)
                    .getCapability("jitai_wizard_wear", CapabilityClient.FILTER_REACHABLE)
                    .await()
                    .nodes
                    .toList()
            } catch (_: Exception) {
                emptyList()
            }
            bpm = ServerState.lastHeartRate
            lastAction = ServerState.lastAction
            delay(3000)
        }
    }

    val (statusText, statusColor) = when {
        watchNodes == null -> "Watch: checking…" to Color.Gray
        watchNodes!!.isEmpty() -> "Watch: NOT reachable (app not found)" to MaterialTheme.colorScheme.error
        else -> "Watch: ${watchNodes!!.joinToString { it.displayName }}" to Color(0xFF2E7D32)
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(statusText, color = statusColor, fontSize = 12.sp, modifier = Modifier.weight(1f))
            if (bpm > 0f) {
                Text("${bpm.toInt()} BPM", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (lastAction.isNotEmpty()) {
                Text("Last: $lastAction", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1)
            }
            if (pingStatus.isNotEmpty()) {
                Text(pingStatus, fontSize = 11.sp, color = Color.Gray)
            }
            TextButton(
                onClick = {
                    pingStatus = "pinging…"
                    scope.launch {
                        WearMessageSender(context).sendPing(onError = { pingStatus = "ping failed" })
                        delay(2000)
                        if (pingStatus == "pinging…") pingStatus = "no reply"
                    }
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("Ping", fontSize = 11.sp)
            }
        }
    }
}
