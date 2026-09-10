package com.BWPStudio.JITAIWizard.ui.debugview

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.BWPStudio.JITAIWizard.JITAIWizardApp
import com.BWPStudio.JITAIWizard.R
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
fun DebugViewScreen(onBack: () -> Unit, onGameSettings: () -> Unit = {}) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf(stringResource(R.string.debugview_tab_control), stringResource(R.string.debugview_tab_experiment))
    var wearErrorMessage by remember { mutableStateOf<String?>(null) }

    wearErrorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { wearErrorMessage = null },
            title = { Text(stringResource(R.string.debugview_wear_error_title)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { wearErrorMessage = null }) { Text(stringResource(R.string.common_ok)) }
            }
        )
    }

    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.errorContainer, tonalElevation = 4.dp) {
            // statusBars padding keeps the colored bar drawing behind the camera/status bar
            // while the buttons sit safely below it (edge-to-edge is enabled in MainActivity).
            Row(
                modifier = Modifier.fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.debugview_banner_debug_mode), color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Sits in the banner rather than in a tab so it is reachable from both the
                    // Control and Experiment tabs, which is where a wizard actually needs it.
                    TextButton(onClick = onGameSettings) {
                        Text(
                            stringResource(R.string.game_settings_title),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    TextButton(onClick = { forceEndApp(context) }) {
                        Text(stringResource(R.string.debugview_button_force_end), color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.common_back), color = MaterialTheme.colorScheme.onErrorContainer)
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

/**
 * State of an in-flight watch ping. The UI renders the localized label from this enum — it
 * never compares against localized/mutated status text (bug #3: previously "pinging…" was both
 * a displayed sentinel and a string compared with ==, which would break once localized).
 */
private enum class PingState { IDLE, PINGING, FAILED, TIMEOUT }

@Composable
private fun pingStatusLabel(state: PingState): String? = when (state) {
    PingState.IDLE -> null
    PingState.PINGING -> stringResource(R.string.watchbar_ping_in_progress)
    PingState.FAILED -> stringResource(R.string.watchbar_ping_failed)
    PingState.TIMEOUT -> stringResource(R.string.watchbar_ping_no_reply)
}

@Composable
private fun WatchStatusBar(onWearError: (String) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // null = still checking, empty = not found, non-empty = found nodes
    var watchNodes by remember { mutableStateOf<List<Node>?>(null) }
    var pingState by remember { mutableStateOf(PingState.IDLE) }
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
        watchNodes == null -> stringResource(R.string.watchbar_status_checking) to Color.Gray
        watchNodes!!.isEmpty() -> stringResource(R.string.watchbar_status_not_reachable) to MaterialTheme.colorScheme.error
        else -> stringResource(R.string.watchbar_status_connected, watchNodes!!.joinToString { it.displayName }) to Color(0xFF2E7D32)
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
                    Text(stringResource(R.string.watchbar_heart_rate, bpm.toInt()), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(stringResource(R.string.watchbar_no_hr_signal), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }
            if (lastAction.isNotEmpty()) {
                Text(stringResource(R.string.watchbar_last_action, lastAction), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1)
            }
            pingStatusLabel(pingState)?.let { label ->
                Text(label, fontSize = 11.sp, color = Color.Gray)
            }
            TextButton(
                onClick = {
                    pingState = PingState.PINGING
                    scope.launch {
                        WearMessageSender(context).sendPing(onError = { pingState = PingState.FAILED })
                        delay(2000)
                        if (pingState == PingState.PINGING) pingState = PingState.TIMEOUT
                    }
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(stringResource(R.string.watchbar_button_ping), fontSize = 11.sp)
            }
            TextButton(
                onClick = { WearMessageSender(context).sendExit(onError = onWearError) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(stringResource(R.string.watchbar_button_close_watch), fontSize = 11.sp)
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
    // FlowRow so the chips wrap onto a new line instead of squeezing + wrapping their
    // labels (which made the row balloon vertically once the Event/Run chips appear at run start).
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AssistChip(onClick = {}, label = { Text(stringResource(R.string.engine_chip_engine, state.status.name), fontSize = 11.sp) },
            colors = AssistChipDefaults.assistChipColors(labelColor = statusColor))
        AssistChip(onClick = {}, label = { Text(stringResource(R.string.engine_chip_mode, state.mode.name), fontSize = 11.sp) })
        state.currentEvent?.let { ev ->
            AssistChip(onClick = {}, label = { Text(stringResource(R.string.engine_chip_event, ev.type), fontSize = 11.sp) })
        }
        state.runId?.let { rid ->
            AssistChip(onClick = {}, label = { Text(stringResource(R.string.engine_chip_run, rid.take(8)), fontSize = 10.sp) })
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
    // FlowRow so several manual triggers wrap onto new lines instead of overflowing the row.
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.trigger_quickfire_label), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        manualTriggers.forEach { t ->
            FilledTonalButton(
                onClick = { ManualTriggerSource.fire(t.id, source = "manual-phone", details = t.name) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(stringResource(R.string.trigger_quickfire_fire_button, t.name), fontSize = 11.sp)
            }
        }
    }
}
