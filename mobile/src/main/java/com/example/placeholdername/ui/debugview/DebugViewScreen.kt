package com.BWPStudio.JITAIWizard.ui.debugview

import android.app.Activity
import android.content.Context
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WatchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.BWPStudio.JITAIWizard.JITAIWizardApp
import com.BWPStudio.JITAIWizard.R
import com.BWPStudio.JITAIWizard.datalayer.WearMessageSender
import com.BWPStudio.JITAIWizard.experiment.EngineStatus
import com.BWPStudio.JITAIWizard.server.ServerState
import com.BWPStudio.JITAIWizard.triggers.ManualTriggerSource
import com.BWPStudio.JITAIWizard.ui.theme.JitaiColors
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        WizardBanner(
            onBack = onBack,
            onGameSettings = onGameSettings,
            onForceEnd = { forceEndApp(context) }
        )
        WatchStatusCard(onWearError = { wearErrorMessage = it })
        EngineStatusChips()
        TriggerQuickFireRow()
        SegmentedTabs(tabs = tabs, selected = selectedTab, onSelect = { selectedTab = it })

        when (selectedTab) {
            0 -> ControlScreen(onWearError = { wearErrorMessage = it }, onGameSettings = onGameSettings)
            1 -> ExperimentScreen(onWearError = { wearErrorMessage = it })
        }
    }
}

/**
 * The "this is not the participant screen" header.
 *
 * Reads as a warning strip rather than a page title on purpose: this view can fire vibrations
 * and end a run on someone else's wrist, and a wizard glancing at a phone that was handed back
 * to a participant needs to notice in one look which side of the app is open.
 */
@Composable
private fun WizardBanner(
    onBack: () -> Unit,
    onGameSettings: () -> Unit,
    onForceEnd: () -> Unit,
) {
    // A slow pulse rather than a blink: enough to catch the eye in peripheral vision without
    // becoming the most distracting thing on a screen someone watches for an hour.
    val pulse by rememberInfiniteTransition(label = "banner").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "dot"
    )

    Surface(color = MaterialTheme.colorScheme.errorContainer) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // statusBars padding keeps the colored bar drawing behind the camera/status
                    // bar while the controls sit safely below it (edge-to-edge in MainActivity).
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 4.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = pulse), CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.debugview_banner_debug_mode),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // Sits in the banner rather than in a tab so it is reachable from both the
                // Control and Experiment tabs, which is where a wizard actually needs it.
                IconButton(onClick = onGameSettings) {
                    Icon(
                        Icons.Filled.Tune,
                        contentDescription = stringResource(R.string.game_settings_title),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                IconButton(onClick = onForceEnd) {
                    Icon(
                        Icons.Filled.PowerSettingsNew,
                        contentDescription = stringResource(R.string.debugview_button_force_end),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.error,
                                MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                            )
                        )
                    )
            )
        }
    }
}

/**
 * State of an in-flight watch ping. The UI renders the localized label from this enum, and
 * never compares against localized/mutated status text (bug #3: previously the "pinging" text
 * was both a displayed sentinel and a string compared with ==, which broke once localized).
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
private fun WatchStatusCard(onWearError: (String) -> Unit = {}) {
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

    val connected = watchNodes != null && watchNodes!!.isNotEmpty()
    val (statusText, statusColor) = when {
        watchNodes == null -> stringResource(R.string.watchbar_status_checking) to JitaiColors.Muted
        watchNodes!!.isEmpty() -> stringResource(R.string.watchbar_status_not_reachable) to MaterialTheme.colorScheme.error
        else -> stringResource(R.string.watchbar_status_connected, watchNodes!!.joinToString { it.displayName }) to JitaiColors.Good
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(modifier = Modifier.size(8.dp).background(statusColor, CircleShape))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    statusText,
                    color = statusColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Ping result and last watch action share one subline: both answer "is the
                // wrist still with us", and neither deserves its own row of height.
                val subline = pingStatusLabel(pingState)
                    ?: lastAction.takeIf { it.isNotEmpty() }
                        ?.let { stringResource(R.string.watchbar_last_action, it) }
                if (subline != null) {
                    Text(
                        subline,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (connected) {
                val hrColor = if (bpm > 0f) JitaiColors.Bad else MaterialTheme.colorScheme.onSurfaceVariant
                Text(
                    if (bpm > 0f) stringResource(R.string.watchbar_heart_rate, bpm.toInt())
                    else stringResource(R.string.watchbar_no_hr_signal),
                    color = hrColor,
                    fontSize = 11.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .background(hrColor.copy(alpha = 0.12f), RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
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
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Text(stringResource(R.string.watchbar_button_ping), fontSize = 11.sp, maxLines = 1)
            }
            IconButton(
                onClick = { WearMessageSender(context).sendExit(onError = onWearError) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Filled.WatchOff,
                    contentDescription = stringResource(R.string.watchbar_button_close_watch),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** A read-only status pill: tinted fill, matching outline, colored label. */
@Composable
private fun StatusPill(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontSize = 11.sp,
        maxLines = 1,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun EngineStatusChips() {
    val context = LocalContext.current
    val app = context.applicationContext as JITAIWizardApp
    val state by app.experimentEngine.state.collectAsState()
    val statusColor = when (state.status) {
        EngineStatus.RUNNING -> JitaiColors.Good
        EngineStatus.PAUSED -> JitaiColors.Warn
        EngineStatus.FINISHED -> MaterialTheme.colorScheme.primary
        else -> JitaiColors.Muted
    }
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    // FlowRow so the chips wrap onto a new line instead of squeezing + wrapping their
    // labels (which made the row balloon vertically once the Event/Run chips appear at run start).
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        StatusPill(stringResource(R.string.engine_chip_engine, state.status.name), statusColor)
        StatusPill(stringResource(R.string.engine_chip_mode, state.mode.name), neutral)
        state.currentEvent?.let { ev ->
            StatusPill(stringResource(R.string.engine_chip_event, ev.type), MaterialTheme.colorScheme.primary)
        }
        state.runId?.let { rid ->
            StatusPill(stringResource(R.string.engine_chip_run, rid.take(8)), neutral)
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.trigger_quickfire_label),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        manualTriggers.forEach { t ->
            FilledTonalButton(
                onClick = { ManualTriggerSource.fire(t.id, source = "manual-phone", details = t.name) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                shape = RoundedCornerShape(50)
            ) {
                Text(stringResource(R.string.trigger_quickfire_fire_button, t.name), fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

/**
 * Control / Experiment switch.
 *
 * A segmented control rather than Material's underlined TabRow: the two halves are modes of the
 * same console, not pages of a document, and a filled selection survives a glance from arm's
 * length better than a 3dp underline does.
 */
@Composable
private fun SegmentedTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        tabs.forEachIndexed { index, title ->
            val active = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    title,
                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }
        }
    }
}
