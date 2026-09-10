package com.BWPStudio.JITAIWizard.ui.debugview

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.jitaicompanion.convention.models.Experiment
import com.example.jitaicompanion.convention.models.GameType
import com.example.jitaicompanion.convention.models.Intervention
import com.example.jitaicompanion.convention.models.NotificationType
import com.example.jitaicompanion.convention.models.ParticipantInfo
import com.example.jitaicompanion.convention.models.PhoneTaskType
import com.example.jitaicompanion.convention.models.TriggerKind
import com.BWPStudio.JITAIWizard.R
import com.BWPStudio.JITAIWizard.settings.SettingsKeys
import com.BWPStudio.JITAIWizard.settings.SettingsRepository
import com.BWPStudio.JITAIWizard.JITAIWizardApp
import com.BWPStudio.JITAIWizard.server.ServerState
import com.BWPStudio.JITAIWizard.experiment.EngineMode
import com.BWPStudio.JITAIWizard.experiment.Event
import com.BWPStudio.JITAIWizard.experiment.ExperimentStore
import com.BWPStudio.JITAIWizard.experiment.LogEvent
import com.BWPStudio.JITAIWizard.ui.components.StringOptionDropdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Tone of a toolbar button.
 *
 * The row used to be six identical filled buttons, which made Start look exactly as
 * consequential as Save. Colour carries the difference now: neutral for file operations, accent
 * for the ones that reach the watch, and green/red for the run switch.
 */
private enum class ToolbarTone { NEUTRAL, ACCENT, GO, STOP }

@Composable
private fun toolbarColors(tone: ToolbarTone): ButtonColors = when (tone) {
    ToolbarTone.NEUTRAL -> ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.primary,
    )
    ToolbarTone.ACCENT -> ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
    ToolbarTone.GO -> ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )
    ToolbarTone.STOP -> ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    )
}

@Composable
fun ExperimentScreen(onWearError: (String) -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as JITAIWizardApp

    var list by remember {
        mutableStateOf(app.experimentStore.active.value.events.map { Event.fromShared(it) })
    }
    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        list = list.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }
    val scope = rememberCoroutineScope()
    var editMode by remember { mutableStateOf(false) }
    var running by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var experiment by remember { mutableStateOf(app.experimentStore.active.value.name) }
    var notes by remember { mutableStateOf(app.experimentStore.active.value.notes) }
    var participantId by remember {
        mutableStateOf(ServerState.participantInfo?.label?.takeIf { it.isNotBlank() } ?: "defaultParticipant")
    }
    var saveLogsBool by remember { mutableStateOf(true) }
    var noteDialogOpen by remember { mutableStateOf(false) }
    var eventToEdit by remember { mutableStateOf<Event?>(null) }
    var eventToHighlight by remember { mutableStateOf<Event?>(null) }
    var elapsedSeconds by rememberSaveable { mutableStateOf(0) }
    var interventionSent by remember { mutableStateOf(false) }

    // Live sync: update the list whenever the store changes (e.g. ControlStation pushes)
    LaunchedEffect(Unit) {
        app.experimentStore.active.collect { exp ->
            if (!running) {
                list = exp.events.map { Event.fromShared(it) }
                experiment = exp.name
                notes = exp.notes
            }
        }
    }

    var saveDialogOpen by remember { mutableStateOf(false) }
    var loadDialogOpen by remember { mutableStateOf(false) }
    var scheduleSlots by remember { mutableStateOf<List<String>>(emptyList()) }

    // Lets the researcher import an experiment JSON from the phone's storage. The imported
    // schedule is saved as a slot so it shows up in the Load list immediately.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val slot = importScheduleFromUri(context, app, uri)
        if (slot != null) {
            scheduleSlots = app.experimentStore.listSchedules()
            Toast.makeText(context, context.getString(R.string.experiment_toast_import_success, slot), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, context.getString(R.string.experiment_toast_import_failed), Toast.LENGTH_LONG).show()
        }
    }

    // Builds an Experiment snapshot from the current editor state (events + name + triggers).
    fun buildCurrentExperiment(): Experiment {
        val current = app.experimentStore.active.value
        return current.copy(
            name = experiment,
            notes = notes,
            events = list.map { it.toShared() },
            triggers = current.triggers
        )
    }

    LaunchedEffect(running) {
        if (running) { elapsedSeconds = 0; while (running) { delay(1000); elapsedSeconds++ } }
    }

    val logger = app.logger
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm-ss.SSS")

    // Phone-side 1:1 CSV export (sensor stream + logs + interventions), gated on the same
    // "Save Logs" toggle as the TSV log. Started when a manual run begins and finalized on
    // stop/finish, mirroring the Control Station's multi-section CSV.
    fun startCsvExport() {
        if (!saveLogsBool) return
        // participantId is the session/participant name; experiment name feeds only the RUNS
        // section; the session notes are gathered from in-run Note entries at finalize.
        app.csvLogger.start(experiment, participantId)
    }
    fun finalizeCsvExport() {
        scope.launch {
            val path = withContext(Dispatchers.IO) { app.csvLogger.finalizeExport() }
            if (path != null) Toast.makeText(context, context.getString(R.string.experiment_toast_csv_saved, path), Toast.LENGTH_LONG).show()
        }
    }

    // Buffer for the Android navigation / home-back bar so bottom controls aren't hidden.
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top bar — split into two rows so the controls never squish into vertical
                // text on narrow devices. Row 1: identity + file ops, Row 2: run controls.
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!running) {
                            Button(onClick = { settingsOpen = true }, modifier = Modifier.weight(1f).height(42.dp),
                                shape = RoundedCornerShape(12.dp), colors = toolbarColors(ToolbarTone.NEUTRAL),
                                contentPadding = PaddingValues(horizontal = 6.dp)) {
                                Text(stringResource(R.string.experiment_settings), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        } else {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(experiment, maxLines = 1, fontSize = 10.sp, lineHeight = 12.sp)
                                Text(participantId, maxLines = 1, fontSize = 10.sp, lineHeight = 12.sp)
                                Text(
                                    if (saveLogsBool) stringResource(R.string.experiment_status_saving_logs) else stringResource(R.string.experiment_status_no_logs),
                                    fontSize = 10.sp, lineHeight = 12.sp
                                )
                            }
                        }
                        Button(onClick = { scheduleSlots = app.experimentStore.listSchedules(); loadDialogOpen = true },
                            enabled = !running, modifier = Modifier.weight(1f).height(42.dp),
                            shape = RoundedCornerShape(12.dp), colors = toolbarColors(ToolbarTone.NEUTRAL),
                            contentPadding = PaddingValues(horizontal = 6.dp)) {
                            Text(stringResource(R.string.experiment_button_load), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Button(onClick = { saveDialogOpen = true }, enabled = !running,
                            modifier = Modifier.weight(1f).height(42.dp),
                            shape = RoundedCornerShape(12.dp), colors = toolbarColors(ToolbarTone.NEUTRAL),
                            contentPadding = PaddingValues(horizontal = 6.dp)) {
                            Text(stringResource(R.string.common_save), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(onClick = {
                            running = !running
                            if (running) {
                                if (list.isEmpty()) { Toast.makeText(context, context.getString(R.string.experiment_toast_no_events), Toast.LENGTH_SHORT).show(); running = false; return@Button }
                                eventToHighlight = list[0]
                                logger.createLogFile(experiment, participantId, LocalDateTime.now().format(formatter), saveLogsBool)
                                logger.log(LogEvent(eventType = "Experiment", value = "start"))
                                startCsvExport()
                            } else {
                                eventToHighlight = null
                                logger.log(LogEvent(eventType = "Experiment", value = "stop"))
                                finalizeCsvExport()
                            }
                        }, enabled = !editMode, modifier = Modifier.weight(1f).height(42.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = toolbarColors(if (running) ToolbarTone.STOP else ToolbarTone.GO),
                            contentPadding = PaddingValues(horizontal = 6.dp)) {
                            Text(
                                if (running) stringResource(R.string.experiment_button_stop) else stringResource(R.string.experiment_button_start),
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(onClick = {
                            if (list.isEmpty()) { Toast.makeText(context, context.getString(R.string.experiment_toast_no_events), Toast.LENGTH_SHORT).show(); return@Button }
                            val current = app.experimentStore.active.value
                            val updated = current.copy(
                                name = experiment,
                                notes = notes,
                                events = list.map { it.toShared() },
                                triggers = current.triggers
                            )
                            app.experimentStore.replace(updated)
                            com.BWPStudio.JITAIWizard.triggers.TriggerEngine.setTriggers(updated.triggers)
                            logger.createLogFile(experiment, participantId, LocalDateTime.now().format(formatter), saveLogsBool)
                            app.experimentEngine.start(EngineMode.AUTO)
                            Toast.makeText(context, context.getString(R.string.experiment_toast_auto_run_started), Toast.LENGTH_SHORT).show()
                        }, enabled = !editMode && !running, modifier = Modifier.weight(1f).height(42.dp),
                            shape = RoundedCornerShape(12.dp), colors = toolbarColors(ToolbarTone.ACCENT),
                            contentPadding = PaddingValues(horizontal = 6.dp)) {
                            Text(stringResource(R.string.experiment_button_auto), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        // Jot a session note ("Person felt uncomfortable" etc.). Recorded as a log
                        // event that syncs into the Control Station DB / CSV via the /logs pipeline.
                        Button(onClick = { noteDialogOpen = true }, modifier = Modifier.weight(1f).height(42.dp),
                            shape = RoundedCornerShape(12.dp), colors = toolbarColors(ToolbarTone.ACCENT),
                            contentPadding = PaddingValues(horizontal = 6.dp)) {
                            Text(stringResource(R.string.experiment_button_note), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                // Event list
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = lazyListState,
                    // Reserve room for the nav bar plus the floating run-control bar (when running)
                    // so the last event never hides behind them.
                    contentPadding = PaddingValues(
                        start = 8.dp, end = 8.dp, top = 8.dp,
                        bottom = 8.dp + navBarBottom + if (running) 72.dp else 0.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "__triggers__") {
                        TriggerSummaryRow(app)
                    }
                    items(list, key = { it.id }) { event ->
                        ReorderableItem(reorderState, key = event.id) { isDragging ->
                            val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)
                            // Shaped so the list reads as a stack of cards. The Surface clips
                            // to the shape, which rounds the background of the row inside it too.
                            Surface(shadowElevation = elevation, shape = RoundedCornerShape(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .background(
                                            if (eventIsValid(event)) {
                                                if (event == eventToHighlight) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surfaceContainerHigh
                                            } else MaterialTheme.colorScheme.errorContainer
                                        )
                                        .combinedClickable(onClick = {}, onDoubleClick = {
                                            if (!running || event == eventToHighlight) return@combinedClickable
                                            eventToHighlight = event; elapsedSeconds = 0; interventionSent = false
                                            logger.log(LogEvent(eventType = "Experiment", value = "JumpTo", details = event.type))
                                        }),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = if (editMode) Arrangement.SpaceBetween else Arrangement.Center
                                ) {
                                    if (editMode) {
                                        IconButton(modifier = Modifier.draggableHandle(), onClick = {}) {
                                            Icon(Icons.Rounded.Menu, contentDescription = stringResource(R.string.experiment_cd_reorder))
                                        }
                                    }
                                    Text(event.type, Modifier.padding(5.dp).fillMaxWidth(if (editMode) 0.18f else 0.3f), fontSize = 18.sp)
                                    if (event.intervention != null) {
                                        val iv = event.intervention!!
                                        val msgPreview = iv.message.take(40)
                                        val gameLabel = iv.gameType?.name ?: event.gameType?.name
                                        val taskLabel = iv.phoneTaskType?.name
                                        val extras = listOfNotNull(
                                            gameLabel?.let { "🎮 $it" },
                                            taskLabel?.let { "📱 $it" }
                                        ).joinToString("  ")
                                        Text(
                                            stringResource(R.string.experiment_event_intervention_summary, iv.type, iv.notification, iv.durationSeconds) +
                                            (if (extras.isNotEmpty()) "\n$extras" else "") +
                                            "\n\"$msgPreview\"",
                                            Modifier.padding(5.dp).fillMaxWidth(if (editMode) 0.45f else 1f),
                                            fontSize = 13.sp
                                        )
                                    } else {
                                        Text(stringResource(R.string.experiment_event_duration, event.duration.toInt()))
                                    }
                                    if (editMode) {
                                        Row(horizontalArrangement = Arrangement.End) {
                                            IconButton(onClick = { eventToEdit = event }) { Icon(Icons.Rounded.Edit, stringResource(R.string.common_edit)) }
                                            IconButton(onClick = { list = list.toMutableList().apply { remove(event) } }) { Icon(Icons.Rounded.Delete, stringResource(R.string.common_delete)) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Edit event dialog
            eventToEdit?.let { evt ->
                EditEventDialog(event = evt, onDismiss = { eventToEdit = null }, onSave = { updated ->
                    list = list.map { if (it.id == updated.id) updated else it }
                })
            }

            // Settings dialog
            if (settingsOpen) {
                EditSettingsDialog(
                    initialExperiment = experiment,
                    // Prefer the latest synced participant (e.g. just pushed by the ControlStation).
                    initialParticipant = ServerState.participantInfo?.label?.takeIf { it.isNotBlank() } ?: participantId,
                    initialSaveLogs = saveLogsBool,
                    initialNotes = notes,
                    onDismiss = { settingsOpen = false }
                ) { expName, part, saveLogs, newNotes ->
                    experiment = expName; participantId = part; saveLogsBool = saveLogs; notes = newNotes
                    // Sync the participant to ServerState + persist it, so the ControlStation
                    // can read it back via GET /participant (bidirectional participant sync).
                    setPhoneParticipant(part)
                    scope.launch { SettingsRepository(context).setSetting(SettingsKeys.PARTICIPANT, part) }
                }
            }

            // Session note dialog
            if (noteDialogOpen) {
                SessionNoteDialog(onDismiss = { noteDialogOpen = false }) { text ->
                    if (text.isNotBlank()) {
                        logger.log(LogEvent(eventType = "Note", value = text))
                        Toast.makeText(context, context.getString(R.string.experiment_toast_note_recorded), Toast.LENGTH_SHORT).show()
                    }
                }
            }

            // Save schedule dialog
            if (saveDialogOpen) {
                SaveScheduleDialog(
                    initialName = experiment.ifBlank { ExperimentStore.DEFAULT_SCHEDULE },
                    existing = app.experimentStore.listSchedules(),
                    onDismiss = { saveDialogOpen = false },
                    onSave = { slot ->
                        if (list.isEmpty()) {
                            Toast.makeText(context, context.getString(R.string.experiment_toast_no_events_to_save), Toast.LENGTH_SHORT).show()
                        } else {
                            val built = buildCurrentExperiment()
                            app.experimentStore.saveSchedule(slot, built)
                            // Also make it the active schedule so it persists + syncs to the ControlStation.
                            app.experimentStore.replace(built)
                            com.BWPStudio.JITAIWizard.triggers.TriggerEngine.setTriggers(built.triggers)
                            Toast.makeText(context, context.getString(R.string.experiment_toast_schedule_saved, slot), Toast.LENGTH_SHORT).show()
                        }
                        saveDialogOpen = false
                    },
                    onExport = { slot ->
                        if (list.isEmpty()) {
                            Toast.makeText(context, context.getString(R.string.experiment_toast_no_events_to_export), Toast.LENGTH_SHORT).show()
                        } else {
                            val exp = buildCurrentExperiment().copy(name = slot)
                            val path = app.experimentStore.exportToDownloads(exp)
                            if (path != null) {
                                Toast.makeText(context, context.getString(R.string.experiment_toast_exported_to, path), Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, context.getString(R.string.experiment_toast_export_failed), Toast.LENGTH_LONG).show()
                            }
                        }
                        saveDialogOpen = false
                    }
                )
            }

            // Load schedule dialog
            if (loadDialogOpen) {
                LoadScheduleDialog(
                    schedules = scheduleSlots,
                    onImport = { importLauncher.launch("*/*") },
                    onDismiss = { loadDialogOpen = false },
                    onDelete = { slot ->
                        app.experimentStore.deleteSchedule(slot)
                        scheduleSlots = app.experimentStore.listSchedules()
                    },
                    onLoad = { slot ->
                        val loaded = app.experimentStore.loadSchedule(slot)
                        if (loaded == null) {
                            Toast.makeText(context, context.getString(R.string.experiment_toast_load_failed, slot), Toast.LENGTH_SHORT).show()
                        } else {
                            list = loaded.events.map { Event.fromShared(it) }
                            experiment = loaded.name
                            app.experimentStore.replace(loaded)
                            com.BWPStudio.JITAIWizard.triggers.TriggerEngine.setTriggers(loaded.triggers)
                            Toast.makeText(context, context.getString(R.string.experiment_toast_schedule_loaded, slot), Toast.LENGTH_SHORT).show()
                        }
                        loadDialogOpen = false
                    }
                )
            }

            // Edit/Add FABs
            if (!running) {
                FloatingActionButton(
                    onClick = { editMode = !editMode; if (!editMode) scope.launch { lazyListState.animateScrollToItem(0) } },
                    containerColor = if (editMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = if (editMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp)
                ) { Icon(Icons.Default.Edit, null) }
                if (editMode) {
                    SmallFloatingActionButton(
                        onClick = {
                            val newEvent = Event(UUID.randomUUID().toString(), 30f, "", null)
                            list = list + newEvent; eventToEdit = list.last()
                        },
                        modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 80.dp, bottom = 16.dp)
                    ) { Icon(Icons.Default.Add, null) }
                }
            }

            // Run controls
            if (running && eventToHighlight != null) {
                Surface(
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                ) {
                    // navigationBars padding lifts the controls above the home/back bar while the
                    // Surface background still fills the area behind it.
                    Box(modifier = Modifier.fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .height(72.dp)
                        .padding(horizontal = 16.dp)) {
                        Row(Modifier.align(Alignment.CenterStart), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(onClick = {
                                    val idx = list.indexOf(eventToHighlight)
                                    if (idx > 0) { eventToHighlight = list[idx - 1]; interventionSent = false }
                                    else Toast.makeText(context, context.getString(R.string.experiment_toast_already_first_event), Toast.LENGTH_SHORT).show()
                                    elapsedSeconds = 0
                                    logger.log(LogEvent(eventType = "Experiment", value = "previous"))
                                }, modifier = Modifier.size(32.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
                                Text(stringResource(R.string.experiment_run_previous), fontSize = 10.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(onClick = {
                                    elapsedSeconds = 0; interventionSent = false
                                    logger.log(LogEvent(eventType = "Experiment", value = "restart"))
                                }, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.Refresh, stringResource(R.string.experiment_run_restart)) }
                                Text(stringResource(R.string.experiment_run_restart), fontSize = 10.sp)
                            }
                        }
                        Text(
                            stringResource(R.string.experiment_run_elapsed_seconds, elapsedSeconds),
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (elapsedSeconds <= eventToHighlight!!.duration) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                        )
                        Row(Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            eventToHighlight!!.intervention?.let { intervention ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    IconButton(onClick = {
                                        interventionSent = true
                                        sendIntervention(context, intervention, onWearError)
                                        logger.log(LogEvent(eventType = "Intervention", value = "start", details = intervention.message))
                                    }, modifier = Modifier.size(32.dp), enabled = !interventionSent) { Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.experiment_run_send_cd)) }
                                    Text(stringResource(R.string.intervention_label), fontSize = 10.sp)
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(onClick = {
                                    val idx = list.indexOf(eventToHighlight)
                                    if (idx < list.size - 1) {
                                        eventToHighlight = list[idx + 1]; interventionSent = false
                                        logger.log(LogEvent(eventType = "Experiment", value = "next"))
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.experiment_toast_finished), Toast.LENGTH_SHORT).show()
                                        eventToHighlight = null; running = false
                                        logger.log(LogEvent(eventType = "Experiment", value = "finished"))
                                        finalizeCsvExport()
                                    }
                                    elapsedSeconds = 0
                                }, modifier = Modifier.size(32.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.experiment_run_next)) }
                                Text(stringResource(R.string.experiment_run_next), fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun sendIntervention(context: android.content.Context, intervention: Intervention, onWearError: (String) -> Unit) {
    val json = Json.encodeToString(intervention)
    com.BWPStudio.JITAIWizard.datalayer.WearMessageSender(context).sendIntervention(json, onError = onWearError)
}

/**
 * Reads an experiment JSON the researcher picked from phone storage and saves it as a
 * schedule slot. Returns the slot name shown in the Load list, or null if it could not
 * be read or parsed.
 */
private fun importScheduleFromUri(
    context: android.content.Context,
    app: JITAIWizardApp,
    uri: android.net.Uri
): String? {
    val text = runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    }.getOrNull() ?: return null
    return app.experimentStore.importSchedule(text)
}

/** Sets the phone-side participant into ServerState so GET /participant exposes it to the ControlStation. */
private fun setPhoneParticipant(label: String) {
    val existing = ServerState.participantInfo
    ServerState.participantInfo = ParticipantInfo(
        id = existing?.id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
        sessionId = existing?.sessionId ?: ServerState.sessionId,
        label = label,
        deviceIp = existing?.deviceIp ?: ""
    )
}

@Composable
private fun EditSettingsDialog(
    initialExperiment: String,
    initialParticipant: String,
    initialSaveLogs: Boolean,
    initialNotes: String,
    onDismiss: () -> Unit,
    onSettingsChanged: (String, String, Boolean, String) -> Unit
) {
    var experimentName by remember { mutableStateOf(initialExperiment) }
    var participant by remember { mutableStateOf(initialParticipant) }
    var saveLogs by remember { mutableStateOf(initialSaveLogs) }
    var notes by remember { mutableStateOf(initialNotes) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 8.dp, modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.experiment_settings), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = experimentName, onValueChange = { experimentName = it }, label = { Text(stringResource(R.string.settings_field_experiment_name)) }, singleLine = true)
                OutlinedTextField(value = participant, onValueChange = { participant = it }, label = { Text(stringResource(R.string.settings_field_participant_id)) }, singleLine = true)
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(stringResource(R.string.settings_field_notes)) }, minLines = 2, maxLines = 5)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().clickable { saveLogs = !saveLogs }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = saveLogs, onCheckedChange = { saveLogs = it })
                    Text(stringResource(R.string.settings_checkbox_save_logs))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { onDismiss(); onSettingsChanged(experimentName, participant, saveLogs, notes) }) { Text(stringResource(R.string.common_ok)) }
                }
            }
        }
    }
}

@Composable
private fun SessionNoteDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 8.dp, modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.note_dialog_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    label = { Text(stringResource(R.string.note_dialog_field_label)) },
                    placeholder = { Text(stringResource(R.string.note_dialog_placeholder)) },
                    minLines = 2, maxLines = 5
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onSave(text); onDismiss() }) { Text(stringResource(R.string.common_save)) }
                }
            }
        }
    }
}

/**
 * Intervention.type wire values ("Text"/"Timer"/"Yes/No") are consumed by the watch and must
 * stay literal English in code/data. This maps each wire value to a localized display label
 * for dropdowns; the stored/selected value passed around in code remains the English literal.
 */
@Composable
private fun interventionTypeDisplayOptions(): List<Pair<String, String>> = listOf(
    "Text" to stringResource(R.string.intervention_type_text_display),
    "Timer" to stringResource(R.string.intervention_type_timer_display),
    "Yes/No" to stringResource(R.string.intervention_type_yesno_display)
)

@Composable
private fun EditEventDialog(event: Event, onDismiss: () -> Unit, onSave: (Event) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(event.type) }
    var duration by remember { mutableStateOf(event.duration.toString()) }
    var intervention by remember { mutableStateOf(event.intervention) }
    var interventionType by remember { mutableStateOf(intervention?.type ?: "Text") }
    var interventionNotification by remember { mutableStateOf(intervention?.notification ?: NotificationType.VIBRATION1) }
    var interventionMessage by remember { mutableStateOf(intervention?.message ?: "") }
    var interventionDuration by remember { mutableStateOf(intervention?.durationSeconds?.toString() ?: "10") }
    var interventionGameType by remember { mutableStateOf(intervention?.gameType) }
    var interventionPhoneTask by remember { mutableStateOf(intervention?.phoneTaskType) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 8.dp, modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.event_editor_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.event_editor_field_name)) })
                OutlinedTextField(value = duration, onValueChange = { duration = it }, label = { Text(stringResource(R.string.event_editor_field_duration_seconds)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                Spacer(Modifier.height(8.dp))
                if (intervention == null) {
                    Button(onClick = {
                        // "Text" is a wire value consumed by the watch — must stay literal English.
                        intervention = Intervention(UUID.randomUUID().toString(), "Text", NotificationType.VIBRATION1, "", 10)
                        interventionType = "Text"
                        interventionNotification = NotificationType.VIBRATION1
                        interventionMessage = ""
                        interventionDuration = "10"
                        interventionGameType = null
                        interventionPhoneTask = null
                    }) { Text(stringResource(R.string.event_editor_add_intervention)) }
                } else {
                    Text(stringResource(R.string.intervention_label), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))

                    // interventionType stays one of the literal English wire values ("Text"/"Timer"/
                    // "Yes/No") consumed by the watch; only the dropdown's displayed label is localized.
                    val typeOptions = interventionTypeDisplayOptions()
                    StringOptionDropdown(
                        selected = typeOptions.first { it.first == interventionType }.second,
                        label = stringResource(R.string.intervention_field_type),
                        options = typeOptions.map { it.second },
                        onSelect = { label -> interventionType = typeOptions.first { it.second == label }.first }
                    )
                    StringOptionDropdown(
                        selected = interventionNotification.name, label = stringResource(R.string.intervention_field_notify),
                        options = NotificationType.entries.filter { it != NotificationType.CANCEL }.map { it.name },
                        onSelect = { interventionNotification = NotificationType.valueOf(it) }
                    )
                    OutlinedTextField(value = interventionMessage, onValueChange = { interventionMessage = it },
                        label = { Text(stringResource(R.string.intervention_field_message)) }, maxLines = 3)
                    OutlinedTextField(value = interventionDuration, onValueChange = { interventionDuration = it },
                        label = { Text(stringResource(R.string.event_editor_field_duration_seconds)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)

                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.intervention_microgame_section), style = MaterialTheme.typography.labelMedium)
                    // "None" sentinel is never compared as text: selecting a label that doesn't
                    // match a GameType entry name (i.e. the None label) naturally yields null.
                    val noneLabel = stringResource(R.string.common_none)
                    StringOptionDropdown(
                        selected = interventionGameType?.name ?: noneLabel,
                        label = stringResource(R.string.intervention_field_microgame),
                        options = listOf(noneLabel) + GameType.entries.map { it.name },
                        onSelect = { selected ->
                            interventionGameType = GameType.entries.firstOrNull { it.name == selected }
                            if (interventionGameType != null) interventionPhoneTask = null   // mutually exclusive
                        }
                    )

                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.intervention_or_phone_task), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    StringOptionDropdown(
                        selected = interventionPhoneTask?.name ?: noneLabel,
                        label = stringResource(R.string.intervention_field_phone_task),
                        options = listOf(noneLabel) + PhoneTaskType.entries.map { it.name },
                        onSelect = { selected ->
                            interventionPhoneTask = PhoneTaskType.entries.firstOrNull { it.name == selected }
                            if (interventionPhoneTask != null) interventionGameType = null   // mutually exclusive
                        }
                    )

                    Spacer(Modifier.height(4.dp))
                    Button(onClick = { intervention = null },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text(stringResource(R.string.event_editor_remove_intervention)) }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val gt = interventionGameType
                        val updated = event.copy(
                            type = name,
                            duration = duration.toFloatOrNull() ?: event.duration,
                            gameType = gt,
                            intervention = intervention?.copy(
                                type = interventionType,
                                notification = interventionNotification,
                                message = interventionMessage,
                                durationSeconds = interventionDuration.toIntOrNull() ?: 0,
                                gameType = gt,
                                phoneTaskType = interventionPhoneTask
                            )
                        )
                        if (!eventIsValid(updated)) {
                            Toast.makeText(context, context.getString(R.string.experiment_toast_fill_all_fields), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        onSave(updated); onDismiss()
                    }) { Text(stringResource(R.string.common_save)) }
                }
            }
        }
    }
}

@Composable
private fun TriggerSummaryRow(app: JITAIWizardApp) {
    val experiment by app.experimentStore.active.collectAsState()
    val triggers = experiment.triggers
    if (triggers.isEmpty()) return

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                stringResource(R.string.trigger_summary_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                triggers.forEach { trigger ->
                    val kindLabel = when (trigger.kind) {
                        is TriggerKind.Manual -> stringResource(R.string.trigger_kind_manual)
                        is TriggerKind.RapidMovement -> stringResource(R.string.trigger_kind_rapid, (trigger.kind as TriggerKind.RapidMovement).accelThreshold)
                        is TriggerKind.RepeatingMovement -> {
                            val k = trigger.kind as TriggerKind.RepeatingMovement
                            stringResource(R.string.trigger_kind_repeat, k.minHz, k.maxHz)
                        }
                        is TriggerKind.HeartRate -> {
                            val k = trigger.kind as TriggerKind.HeartRate
                            stringResource(R.string.trigger_kind_heart_rate, if (k.above) ">" else "<", k.bpm)
                        }
                    }
                    FilterChip(
                        selected = trigger.enabled,
                        onClick = {},
                        label = { Text("${trigger.name}  ·  $kindLabel", fontSize = 11.sp) }
                    )
                }
            }
        }
    }
}

private fun eventIsValid(event: Event): Boolean {
    if (event.type.isEmpty() || event.duration < 0) return false
    event.intervention?.let { if (it.type.isEmpty()) return false }
    return true
}

@Composable
private fun SaveScheduleDialog(
    initialName: String,
    existing: List<String>,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onExport: (String) -> Unit
) {
    var slotName by remember { mutableStateOf(initialName) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 8.dp, modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.save_schedule_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = slotName,
                    onValueChange = { slotName = it },
                    label = { Text(stringResource(R.string.save_schedule_field_name)) },
                    singleLine = true
                )
                if (existing.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.save_schedule_overwrite_existing), style = MaterialTheme.typography.labelMedium)
                    LazyColumn(modifier = Modifier.heightIn(max = 140.dp)) {
                        items(existing) { name ->
                            Text(
                                name,
                                modifier = Modifier.fillMaxWidth().clickable { slotName = name }.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                // Writes the current experiment to a shareable .json in Downloads (uses the
                // name above for the file). Independent of saving to an internal slot.
                OutlinedButton(
                    onClick = { onExport(slotName.trim().ifBlank { ExperimentStore.DEFAULT_SCHEDULE }) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.save_schedule_export_button)) }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onSave(slotName.trim().ifBlank { ExperimentStore.DEFAULT_SCHEDULE }) }) { Text(stringResource(R.string.common_save)) }
                }
            }
        }
    }
}

@Composable
private fun LoadScheduleDialog(
    schedules: List<String>,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: (String) -> Unit,
    onLoad: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 8.dp, modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.load_schedule_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                // Bring in an experiment JSON from the phone; it is saved as a slot and
                // appears in the list below right away.
                OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.load_schedule_import_button))
                }
                Spacer(Modifier.height(8.dp))
                if (schedules.isEmpty()) {
                    Text(stringResource(R.string.load_schedule_empty), style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                        items(schedules, key = { it }) { name ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    name,
                                    modifier = Modifier.weight(1f).clickable { onLoad(name) }.padding(vertical = 10.dp)
                                )
                                if (name != ExperimentStore.DEFAULT_SCHEDULE) {
                                    IconButton(onClick = { onDelete(name) }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.common_delete))
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
                }
            }
        }
    }
}
