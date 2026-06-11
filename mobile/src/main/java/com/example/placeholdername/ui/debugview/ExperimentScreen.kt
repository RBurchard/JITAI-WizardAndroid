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
import androidx.compose.ui.text.input.KeyboardType
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
import com.BWPStudio.JITAIWizard.settings.SettingsKeys
import com.BWPStudio.JITAIWizard.settings.SettingsRepository
import com.BWPStudio.JITAIWizard.JITAIWizardApp
import com.BWPStudio.JITAIWizard.server.ServerState
import com.BWPStudio.JITAIWizard.experiment.EngineMode
import com.BWPStudio.JITAIWizard.experiment.Event
import com.BWPStudio.JITAIWizard.experiment.ExperimentStore
import com.BWPStudio.JITAIWizard.experiment.LogEvent
import com.BWPStudio.JITAIWizard.ui.components.StringOptionDropdown
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

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
            Toast.makeText(context, "Imported \"$slot\"", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Import failed: not a valid experiment file", Toast.LENGTH_LONG).show()
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

    // Buffer for the Android navigation / home-back bar so bottom controls aren't hidden.
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top bar — split into two rows so the controls never squish into vertical
                // text on narrow devices. Row 1: identity + file ops, Row 2: run controls.
                Column(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!running) {
                            Button(onClick = { settingsOpen = true }, modifier = Modifier.weight(1f)) { Text("Settings") }
                        } else {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(experiment, maxLines = 1, fontSize = 10.sp, lineHeight = 12.sp)
                                Text(participantId, maxLines = 1, fontSize = 10.sp, lineHeight = 12.sp)
                                Text(if (saveLogsBool) "Saving Logs" else "No logs", fontSize = 10.sp, lineHeight = 12.sp)
                            }
                        }
                        Button(onClick = { scheduleSlots = app.experimentStore.listSchedules(); loadDialogOpen = true }, enabled = !running, modifier = Modifier.weight(1f)) { Text("Load") }
                        Button(onClick = { saveDialogOpen = true }, enabled = !running, modifier = Modifier.weight(1f)) { Text("Save") }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(onClick = {
                            running = !running
                            if (running) {
                                if (list.isEmpty()) { Toast.makeText(context, "No events!", Toast.LENGTH_SHORT).show(); running = false; return@Button }
                                eventToHighlight = list[0]
                                logger.createLogFile(experiment, participantId, LocalDateTime.now().format(formatter), saveLogsBool)
                                logger.log(LogEvent(eventType = "Experiment", value = "start"))
                            } else {
                                eventToHighlight = null
                                logger.log(LogEvent(eventType = "Experiment", value = "stop"))
                            }
                        }, enabled = !editMode, modifier = Modifier.weight(1f)) { Text(if (running) "Stop" else "Start") }

                        Button(onClick = {
                            if (list.isEmpty()) { Toast.makeText(context, "No events!", Toast.LENGTH_SHORT).show(); return@Button }
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
                            Toast.makeText(context, "Auto run started", Toast.LENGTH_SHORT).show()
                        }, enabled = !editMode && !running, modifier = Modifier.weight(1f)) { Text("Auto") }

                        // Jot a session note ("Person felt uncomfortable" etc.). Recorded as a log
                        // event that syncs into the Control Station DB / CSV via the /logs pipeline.
                        Button(onClick = { noteDialogOpen = true }, modifier = Modifier.weight(1f)) { Text("📝 Note") }
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
                            Surface(shadowElevation = elevation) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 3.dp)
                                        .background(
                                            if (eventIsValid(event)) {
                                                if (event == eventToHighlight) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surfaceContainer
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
                                            Icon(Icons.Rounded.Menu, contentDescription = "Reorder")
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
                                            "${iv.type} · ${iv.notification}  ${iv.durationSeconds}s" +
                                            (if (extras.isNotEmpty()) "\n$extras" else "") +
                                            "\n\"$msgPreview\"",
                                            Modifier.padding(5.dp).fillMaxWidth(if (editMode) 0.45f else 1f),
                                            fontSize = 13.sp
                                        )
                                    } else {
                                        Text("Duration: ${event.duration.toInt()} s")
                                    }
                                    if (editMode) {
                                        Row(horizontalArrangement = Arrangement.End) {
                                            IconButton(onClick = { eventToEdit = event }) { Icon(Icons.Rounded.Edit, "Edit") }
                                            IconButton(onClick = { list = list.toMutableList().apply { remove(event) } }) { Icon(Icons.Rounded.Delete, "Delete") }
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
                        Toast.makeText(context, "Note recorded", Toast.LENGTH_SHORT).show()
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
                            Toast.makeText(context, "No events to save!", Toast.LENGTH_SHORT).show()
                        } else {
                            val built = buildCurrentExperiment()
                            app.experimentStore.saveSchedule(slot, built)
                            // Also make it the active schedule so it persists + syncs to the ControlStation.
                            app.experimentStore.replace(built)
                            com.BWPStudio.JITAIWizard.triggers.TriggerEngine.setTriggers(built.triggers)
                            Toast.makeText(context, "Saved schedule \"$slot\"", Toast.LENGTH_SHORT).show()
                        }
                        saveDialogOpen = false
                    },
                    onExport = { slot ->
                        if (list.isEmpty()) {
                            Toast.makeText(context, "No events to export!", Toast.LENGTH_SHORT).show()
                        } else {
                            val exp = buildCurrentExperiment().copy(name = slot)
                            val path = app.experimentStore.exportToDownloads(exp)
                            if (path != null) {
                                Toast.makeText(context, "Exported to $path", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Export failed - check storage", Toast.LENGTH_LONG).show()
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
                            Toast.makeText(context, "Could not load \"$slot\"", Toast.LENGTH_SHORT).show()
                        } else {
                            list = loaded.events.map { Event.fromShared(it) }
                            experiment = loaded.name
                            app.experimentStore.replace(loaded)
                            com.BWPStudio.JITAIWizard.triggers.TriggerEngine.setTriggers(loaded.triggers)
                            Toast.makeText(context, "Loaded schedule \"$slot\"", Toast.LENGTH_SHORT).show()
                        }
                        loadDialogOpen = false
                    }
                )
            }

            // Edit/Add FABs
            if (!running) {
                FloatingActionButton(
                    onClick = { editMode = !editMode; if (!editMode) scope.launch { lazyListState.animateScrollToItem(0) } },
                    containerColor = if (editMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
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
                                    else Toast.makeText(context, "Already at first event!", Toast.LENGTH_SHORT).show()
                                    elapsedSeconds = 0
                                    logger.log(LogEvent(eventType = "Experiment", value = "previous"))
                                }, modifier = Modifier.size(32.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                                Text("Previous", fontSize = 10.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(onClick = {
                                    elapsedSeconds = 0; interventionSent = false
                                    logger.log(LogEvent(eventType = "Experiment", value = "restart"))
                                }, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.Refresh, "Restart") }
                                Text("Restart", fontSize = 10.sp)
                            }
                        }
                        Text(
                            "${elapsedSeconds}s",
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
                                    }, modifier = Modifier.size(32.dp), enabled = !interventionSent) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
                                    Text("Intervention", fontSize = 10.sp)
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(onClick = {
                                    val idx = list.indexOf(eventToHighlight)
                                    if (idx < list.size - 1) {
                                        eventToHighlight = list[idx + 1]; interventionSent = false
                                        logger.log(LogEvent(eventType = "Experiment", value = "next"))
                                    } else {
                                        Toast.makeText(context, "Finished!", Toast.LENGTH_SHORT).show()
                                        eventToHighlight = null; running = false
                                        logger.log(LogEvent(eventType = "Experiment", value = "finished"))
                                    }
                                    elapsedSeconds = 0
                                }, modifier = Modifier.size(32.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next") }
                                Text("Next", fontSize = 10.sp)
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
                Text("Settings", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = experimentName, onValueChange = { experimentName = it }, label = { Text("Experiment Name") }, singleLine = true)
                OutlinedTextField(value = participant, onValueChange = { participant = it }, label = { Text("Participant ID") }, singleLine = true)
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Experiment Notes / Rundown") }, minLines = 2, maxLines = 5)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().clickable { saveLogs = !saveLogs }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = saveLogs, onCheckedChange = { saveLogs = it })
                    Text("Save Logs")
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { onDismiss(); onSettingsChanged(experimentName, participant, saveLogs, notes) }) { Text("OK") }
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
                Text("Session note", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    label = { Text("What happened?") },
                    placeholder = { Text("e.g. Participant felt uncomfortable") },
                    minLines = 2, maxLines = 5
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onSave(text); onDismiss() }) { Text("Save") }
                }
            }
        }
    }
}

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
                Text("Edit Event", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                OutlinedTextField(value = duration, onValueChange = { duration = it }, label = { Text("Duration (s)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                Spacer(Modifier.height(8.dp))
                if (intervention == null) {
                    Button(onClick = {
                        intervention = Intervention(UUID.randomUUID().toString(), "Text", NotificationType.VIBRATION1, "", 10)
                        interventionType = "Text"
                        interventionNotification = NotificationType.VIBRATION1
                        interventionMessage = ""
                        interventionDuration = "10"
                        interventionGameType = null
                        interventionPhoneTask = null
                    }) { Text("Add Intervention") }
                } else {
                    Text("Intervention", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))

                    StringOptionDropdown(selected = interventionType, label = "Type",
                        options = listOf("Text", "Timer", "Yes/No"), onSelect = { interventionType = it })
                    StringOptionDropdown(
                        selected = interventionNotification.name, label = "Notify",
                        options = NotificationType.entries.filter { it != NotificationType.CANCEL }.map { it.name },
                        onSelect = { interventionNotification = NotificationType.valueOf(it) }
                    )
                    OutlinedTextField(value = interventionMessage, onValueChange = { interventionMessage = it },
                        label = { Text("Message") }, maxLines = 3)
                    OutlinedTextField(value = interventionDuration, onValueChange = { interventionDuration = it },
                        label = { Text("Duration (s)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)

                    Spacer(Modifier.height(8.dp))
                    Text("Microgame (optional)", style = MaterialTheme.typography.labelMedium)
                    // "None" + all GameType entries
                    StringOptionDropdown(
                        selected = interventionGameType?.name ?: "None",
                        label = "Microgame",
                        options = listOf("None") + GameType.entries.map { it.name },
                        onSelect = { selected ->
                            if (selected == "None") {
                                interventionGameType = null
                            } else {
                                interventionGameType = GameType.valueOf(selected)
                                interventionPhoneTask = null   // mutually exclusive
                            }
                        }
                    )

                    Spacer(Modifier.height(4.dp))
                    Text("— or Phone Task —", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    StringOptionDropdown(
                        selected = interventionPhoneTask?.name ?: "None",
                        label = "Phone Task",
                        options = listOf("None") + PhoneTaskType.entries.map { it.name },
                        onSelect = { selected ->
                            if (selected == "None") {
                                interventionPhoneTask = null
                            } else {
                                interventionPhoneTask = PhoneTaskType.valueOf(selected)
                                interventionGameType = null   // mutually exclusive
                            }
                        }
                    )

                    Spacer(Modifier.height(4.dp))
                    Button(onClick = { intervention = null },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Remove Intervention") }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
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
                            Toast.makeText(context, "Fill in all fields!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        onSave(updated); onDismiss()
                    }) { Text("Save") }
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
                "Triggers",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                triggers.forEach { trigger ->
                    val kindLabel = when (trigger.kind) {
                        is TriggerKind.Manual -> "Manual"
                        is TriggerKind.RapidMovement -> "Rapid (${(trigger.kind as TriggerKind.RapidMovement).accelThreshold}g)"
                        is TriggerKind.RepeatingMovement -> {
                            val k = trigger.kind as TriggerKind.RepeatingMovement
                            "Repeat ${k.minHz}–${k.maxHz} Hz"
                        }
                        is TriggerKind.HeartRate -> {
                            val k = trigger.kind as TriggerKind.HeartRate
                            "HR ${if (k.above) ">" else "<"} ${k.bpm}"
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
                Text("Save Schedule", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = slotName,
                    onValueChange = { slotName = it },
                    label = { Text("Schedule name") },
                    singleLine = true
                )
                if (existing.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Overwrite existing:", style = MaterialTheme.typography.labelMedium)
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
                ) { Text("Export to file (Downloads)") }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onSave(slotName.trim().ifBlank { ExperimentStore.DEFAULT_SCHEDULE }) }) { Text("Save") }
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
                Text("Load Schedule", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                // Bring in an experiment JSON from the phone; it is saved as a slot and
                // appears in the list below right away.
                OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                    Text("Import from phone…")
                }
                Spacer(Modifier.height(8.dp))
                if (schedules.isEmpty()) {
                    Text("No saved schedules yet.", style = MaterialTheme.typography.bodyMedium)
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
                                        Icon(Icons.Rounded.Delete, contentDescription = "Delete")
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}
