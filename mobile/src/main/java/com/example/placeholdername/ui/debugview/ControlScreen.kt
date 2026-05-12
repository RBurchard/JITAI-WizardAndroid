package com.BWPStudio.JITAIWizard.ui.debugview

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jitaicompanion.convention.models.GameType
import com.example.jitaicompanion.convention.models.Intervention
import com.example.jitaicompanion.convention.models.NotificationType
import com.BWPStudio.JITAIWizard.datalayer.WearMessageSender
import com.BWPStudio.JITAIWizard.server.ServerState
import com.BWPStudio.JITAIWizard.ui.components.ConfirmActionButton
import com.BWPStudio.JITAIWizard.ui.components.SliderWithInput
import com.BWPStudio.JITAIWizard.ui.components.StringOptionDropdown
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Composable
fun ControlScreen(onWearError: (String) -> Unit = {}) {
    val context = LocalContext.current
    var message by remember { mutableStateOf("Stop!") }
    var duration by remember { mutableStateOf(15f) }
    var selectedType by remember { mutableStateOf("Text") }
    var selectedGame by remember { mutableStateOf("None") }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(10.dp))
        Text("Send Interventions", fontSize = 24.sp, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(5.dp))

        Row(Modifier.padding(5.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { send(context, selectedType, NotificationType.VIBRATION1, message, duration.toInt(), selectedGame, onWearError) }) { Text("Vib Short") }
            Button(onClick = { send(context, selectedType, NotificationType.VIBRATION2, message, duration.toInt(), selectedGame, onWearError) }) { Text("Vib Long") }
        }
        Row(Modifier.padding(5.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { send(context, selectedType, NotificationType.SOUND, message, duration.toInt(), selectedGame, onWearError) }) { Text("Sound Short") }
            Button(onClick = { send(context, selectedType, NotificationType.SOUND, message, duration.toInt(), selectedGame, onWearError) }) { Text("Sound Long") }
        }
        Row(Modifier.padding(5.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { send(context, selectedType, NotificationType.VIBRATION_SOUND, message, duration.toInt(), selectedGame, onWearError) }, Modifier.width(140.dp)) { Text("Vib+Sound Short") }
            Button(onClick = { send(context, selectedType, NotificationType.VIBRATION_SOUND2, message, duration.toInt(), selectedGame, onWearError) }, Modifier.width(140.dp)) { Text("Vib+Sound Long") }
        }
        Row(Modifier.padding(5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = { send(context, selectedType, NotificationType.ANNOY_VIB, message, duration.toInt(), selectedGame, onWearError) }, Modifier.width(105.dp)) { Text("Perma Vib") }
            Button(onClick = { send(context, selectedType, NotificationType.ANNOY_SOUND, message, duration.toInt(), selectedGame, onWearError) }, Modifier.width(95.dp)) { Text("Perma Sound") }
            Button(onClick = { send(context, selectedType, NotificationType.ANNOY_VIBRATION_SOUND, message, duration.toInt(), selectedGame, onWearError) }, Modifier.width(115.dp)) { Text("Perma Both") }
        }

        Spacer(Modifier.height(5.dp))
        Text("Message:", fontSize = 14.sp, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth())
        TextField(value = message, onValueChange = { message = it }, singleLine = true,
            placeholder = { Text("Enter message") }, modifier = Modifier.padding(10.dp))

        StringOptionDropdown(selected = selectedType, label = "Intervention Type",
            options = listOf("Text", "Timer", "Yes/No"), onSelect = { selectedType = it })

        StringOptionDropdown(selected = selectedGame, label = "Microgame (optional)",
            options = listOf("None", "Lock Picking", "Simon Says", "Trivia", "Stand Still"),
            onSelect = { selectedGame = it })

        Text("Duration (seconds):", fontSize = 14.sp, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth())
        SliderWithInput(duration, { duration = it }, 1f..60f, 1f, label = "")

        ConfirmActionButton {
            send(context, "Stop", NotificationType.CANCEL, "", 0, "None", onWearError)
        }
    }
}

private fun send(context: Context, type: String, notification: NotificationType,
                 message: String, duration: Int, gameName: String, onWearError: (String) -> Unit) {
    val gameType = when (gameName) {
        "Lock Picking" -> GameType.LOCK_PICKING
        "Simon Says" -> GameType.SIMON_SAYS
        "Trivia" -> GameType.TRIVIA
        "Stand Still" -> GameType.STAND_STILL
        else -> null
    }
    val intervention = Intervention(
        id = UUID.randomUUID().toString(),
        type = type,
        message = message,
        notification = notification,
        durationSeconds = duration,
        gameType = gameType
    )
    ServerState.lastInterventionSentAt = System.currentTimeMillis()
    WearMessageSender(context).sendIntervention(Json.encodeToString(intervention), onError = onWearError)
}
