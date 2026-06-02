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
import com.example.jitaicompanion.convention.models.PhoneTaskType
import com.example.jitaicompanion.convention.models.TriggerKind
import com.BWPStudio.JITAIWizard.JITAIWizardApp
import com.BWPStudio.JITAIWizard.server.ServerState
import com.BWPStudio.JITAIWizard.experiment.EngineMode
import com.BWPStudio.JITAIWizard.experiment.Event
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
    var participantId by remember {
        mutableStateOf(ServerState.participantInfo?.label?.takeIf { it.isNotBlank() } ?: "defaultParticipant")
    }
    var saveLogsBool by remember { mutableStateOf(true) }
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
            }
        }
    }

    val createFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { context.contentResolver.openOutputStream(it)?.use { s -> s.write(Json.encodeToString(list).toByteArray()) } }
    }
    val loadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { r ->
                list = Json.decodeFromString<List<Event>>(r.readText())
            }
        }
    }

    LaunchedEffect(running) {
        if (running) { elapsedSeconds = 0; while (running) { delay(1000); elapsedSeconds++ } }
    }

    val logger = app.logger
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm-ss.SSS")

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top bar
                Row(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!running) {
                        Button(onClick = { settingsOpen = true }, modifier = Modifier.weight(0.25f)) { Text("Settings") }
                    } else {
                        Column(modifier = Modifier.weight(0.25f)) {
                            Text(experiment, maxLines = 1, fontSize = 10.sp, lineHeight = 10.sp)
                            Text(participantId, maxLines = 1, fontSize = 10.sp, lineHeight = 10.sp)
                            Text(if (saveLogsBool) "Saving Logs" else "No logs", fontSize = 10.sp, lineHeight = 10.sp)
                        }
                    }
                    Button(onClick = { loadLauncher.launch(arrayOf("application/json")) }, enabled = !running) { Text("Load") }
                    Button(onClick = { createFileLauncher.launch("events.json") }, enabled = !running) { Text("Save") }
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
                    }, enabled = !editMode) { Text(if (running) "Stop" else "Start") }

                    Button(onClick = {
                        if (list.isEmpty()) { Toast.makeText(context, "No events!", Toast.LENGTH_SHORT).show(); return@Button }
                        val current = app.experimentStore.active.value
                        val updated = current.copy(
                            name = experiment,
                            events = list.map { it.toShared() },
                            triggers = current.triggers
                        )
                        app.experimentStore.replace(updated)
                        com.BWPStudio.JITAIWizard.triggers.TriggerEngine.setTriggers(updated.triggers)
                        logger.createLogFile(experiment, participantId, LocalDateTime.now().format(formatter), saveLogsBool)
                        app.experimentEngine.start(EngineMode.AUTO)
                        Toast.makeText(context, "Auto run started", Toast.LENGTH_SHORT).show()
                    }, enabled = !editMode && !running) { Text("Auto") }
                }

                // Event list
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(bottom = if (running) 72.dp else 0.dp),
                    state = lazyListState,
                    contentPadding = PaddingValues(8.dp),
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
                EditSettingsDialog(onDismiss = { settingsOpen = false }) { expName, part, saveLogs ->
                    experiment = expName; participantId = part; saveLogsBool = saveLogs
                }
            }

            // Edit/Add FABs
            if (!running) {
                FloatingActionButton(
                    onClick = { editMode = !editMode; if (!editMode) scope.launch { lazyListState.animateScrollToItem(0) } },
                    containerColor = if (editMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    contentColor = if (editMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp).align(Alignment.BottomEnd)
                ) { Icon(Icons.Default.Edit, null) }
                if (editMode) {
                    SmallFloatingActionButton(
                        onClick = {
                            val newEvent = Event(UUID.randomUUID().toString(), 30f, "", null)
                            list = list + newEvent; eventToEdit = list.last()
                        },
                        modifier = Modifier.padding(end = 80.dp, bottom = 16.dp).align(Alignment.BottomEnd)
                    ) { Icon(Icons.Default.Add, null) }
                }
            }

            // Run controls
            if (running && eventToHighlight != null) {
                Surface(
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth().height(72.dp).align(Alignment.BottomCenter)
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
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

@Composable
private fun EditSettingsDialog(onDismiss: () -> Unit, onSettingsChanged: (String, String, Boolean) -> Unit) {
    var experimentName by remember { mutableStateOf("") }
    var participant by remember { mutableStateOf("") }
    var saveLogs by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 8.dp, modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Settings", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = experimentName, onValueChange = { experimentName = it }, label = { Text("Experiment Name") }, singleLine = true)
                OutlinedTextField(value = participant, onValueChange = { participant = it }, label = { Text("Participant ID") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().clickable { saveLogs = !saveLogs }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = saveLogs, onCheckedChange = { saveLogs = it })
                    Text("Save Logs")
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { onDismiss(); onSettingsChanged(experimentName, participant, saveLogs) }) { Text("OK") }
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
