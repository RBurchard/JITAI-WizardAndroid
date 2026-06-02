package com.BWPStudio.JITAIWizard.ui.debugview

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.BWPStudio.JITAIWizard.JITAIWizardApp
import com.BWPStudio.JITAIWizard.datalayer.WearMessageSender
import com.BWPStudio.JITAIWizard.experiment.EngineStatus
import com.BWPStudio.JITAIWizard.server.ServerState
import com.BWPStudio.JITAIWizard.triggers.ManualTriggerSource
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.system.exitProcess

/**
 * Fully closes the phone app with no lingering background processes: stops the HTTP
 * server and UDP beacon, finishes the activity stack and terminates the process.
 */
private fun forceEndApp(context: Context) {
    runCatching { (context.applicationContext as? JITAIWizardApp)?.shutdown() }
    (context as? Activity)?.finishAffinity()
    exitProcess(0)
}

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

    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.errorContainer, tonalElevation = 4.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("DEBUG MODE", color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { forceEndApp(context) }) {
                        Text("⏻ Force End", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = onBack) {
                        Text("Back", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }

        WatchStatusBar(onWearError = { wearErrorMessage = it })
        EngineStatusChips()
        TriggerQuickFireRow()

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
private fun WatchStatusBar(onWearError: (String) -> Unit = {}) {
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
                Wearable.getNodeClient(context).connectedNodes.await().toList()
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
            if (watchNodes != null && watchNodes!!.isNotEmpty()) {
                if (bpm > 0f) {
                    Text("${bpm.toInt()} BPM", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("No HR signal", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
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
            TextButton(
                onClick = { WearMessageSender(context).sendExit(onError = onWearError) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("Close Watch", fontSize = 11.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EngineStatusChips() {
    val context = LocalContext.current
    val app = context.applicationContext as JITAIWizardApp
    val state by app.experimentEngine.state.collectAsState()
    val statusColor = when (state.status) {
        EngineStatus.RUNNING -> Color(0xFF2E7D32)
        EngineStatus.PAUSED -> Color(0xFFF9A825)
        EngineStatus.FINISHED -> MaterialTheme.colorScheme.primary
        else -> Color.Gray
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AssistChip(onClick = {}, label = { Text("Engine: ${state.status.name}", fontSize = 11.sp) },
            colors = AssistChipDefaults.assistChipColors(labelColor = statusColor))
        AssistChip(onClick = {}, label = { Text("Mode: ${state.mode.name}", fontSize = 11.sp) })
        state.currentEvent?.let { ev ->
            AssistChip(onClick = {}, label = { Text("Event: ${ev.type}", fontSize = 11.sp) })
        }
        state.runId?.let { rid ->
            AssistChip(onClick = {}, label = { Text("Run: ${rid.take(8)}", fontSize = 10.sp) })
        }
    }
}

@Composable
private fun TriggerQuickFireRow() {
    val context = LocalContext.current
    val app = context.applicationContext as JITAIWizardApp
    val triggers by app.experimentStore.active.collectAsState()
    val manualTriggers = triggers.triggers.filter {
        it.kind is com.example.jitaicompanion.convention.models.TriggerKind.Manual
    }
    if (manualTriggers.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Triggers:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        manualTriggers.forEach { t ->
            FilledTonalButton(
                onClick = { ManualTriggerSource.fire(t.id, source = "manual-phone", details = t.name) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("Fire ${t.name}", fontSize = 11.sp)
            }
        }
    }
}
