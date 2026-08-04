package com.BWPStudio.JITAIWizard.ui.debugview

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jitaicompanion.convention.models.GameType
import com.example.jitaicompanion.convention.models.Intervention
import com.example.jitaicompanion.convention.models.NotificationType
import com.BWPStudio.JITAIWizard.R
import com.BWPStudio.JITAIWizard.datalayer.WearMessageSender
import com.BWPStudio.JITAIWizard.server.ServerState
import com.BWPStudio.JITAIWizard.ui.components.ConfirmActionButton
import com.BWPStudio.JITAIWizard.ui.components.SliderWithInput
import com.BWPStudio.JITAIWizard.ui.components.StringOptionDropdown
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Intervention.type wire values ("Text"/"Timer"/"Yes/No") are consumed by the watch and must
 * stay literal English in code/data (see EditEventDialog in ExperimentScreen.kt for the same
 * pattern). Maps each wire value to a localized display label; the stored/selected value is
 * still the English literal.
 */
@Composable
private fun interventionTypeDisplayOptions(): List<Pair<String, String>> = listOf(
    "Text" to stringResource(R.string.intervention_type_text_display),
    "Timer" to stringResource(R.string.intervention_type_timer_display),
    "Yes/No" to stringResource(R.string.intervention_type_yesno_display)
)

/**
 * Microgame selection is modeled as GameType? (null = "None") rather than a display string, so
 * the "no microgame" sentinel is never compared against localized text. The None entry uses the
 * shared common_none display label; the real entries reuse the same labels as their (nicely
 * spelled) English source, localized per-language.
 */
@Composable
private fun microgameDisplayOptions(): List<Pair<GameType?, String>> = listOf(
    null to stringResource(R.string.common_none),
    GameType.LOCK_PICKING to stringResource(R.string.game_lock_picking),
    GameType.SIMON_SAYS to stringResource(R.string.game_simon_says),
    GameType.TRIVIA to stringResource(R.string.game_trivia),
    GameType.STAND_STILL to stringResource(R.string.game_stand_still)
)

@Composable
fun ControlScreen(onWearError: (String) -> Unit = {}) {
    val context = LocalContext.current
    var message by remember { mutableStateOf("Stop!") }
    var duration by remember { mutableStateOf(15f) }
    // "Text" is a wire value consumed by the watch — must stay literal English (see bug #4 note
    // on interventionTypeDisplayOptions above).
    var selectedType by remember { mutableStateOf("Text") }
    // null represents "no microgame selected" — never a localized/compared "None" string (bug #1).
    var selectedGame by remember { mutableStateOf<GameType?>(null) }

    Column(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.control_title), fontSize = 24.sp, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(5.dp))

        Row(Modifier.padding(5.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { send(context, selectedType, NotificationType.VIBRATION1, message, duration.toInt(), selectedGame, onWearError) }) { Text(stringResource(R.string.control_button_vib_short)) }
            Button(onClick = { send(context, selectedType, NotificationType.VIBRATION2, message, duration.toInt(), selectedGame, onWearError) }) { Text(stringResource(R.string.control_button_vib_long)) }
        }
        Row(Modifier.padding(5.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { send(context, selectedType, NotificationType.SOUND, message, duration.toInt(), selectedGame, onWearError) }) { Text(stringResource(R.string.control_button_sound_short)) }
            Button(onClick = { send(context, selectedType, NotificationType.SOUND, message, duration.toInt(), selectedGame, onWearError) }) { Text(stringResource(R.string.control_button_sound_long)) }
        }
        // Fixed dp widths removed in favor of weight(1f) + maxLines/ellipsis so longer German/Dutch
        // labels ("Vib.+Ton kurz" etc.) don't clip.
        Row(Modifier.padding(5.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { send(context, selectedType, NotificationType.VIBRATION_SOUND, message, duration.toInt(), selectedGame, onWearError) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.control_button_vib_sound_short), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
            Button(onClick = { send(context, selectedType, NotificationType.VIBRATION_SOUND2, message, duration.toInt(), selectedGame, onWearError) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.control_button_vib_sound_long), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
        Row(Modifier.padding(5.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = { send(context, selectedType, NotificationType.ANNOY_VIB, message, duration.toInt(), selectedGame, onWearError) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.control_button_perma_vib), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
            Button(onClick = { send(context, selectedType, NotificationType.ANNOY_SOUND, message, duration.toInt(), selectedGame, onWearError) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.control_button_perma_sound), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
            Button(onClick = { send(context, selectedType, NotificationType.ANNOY_VIBRATION_SOUND, message, duration.toInt(), selectedGame, onWearError) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.control_button_perma_both), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }

        Spacer(Modifier.height(5.dp))
        Text(stringResource(R.string.control_label_message), fontSize = 14.sp, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth())
        TextField(value = message, onValueChange = { message = it }, singleLine = true,
            placeholder = { Text(stringResource(R.string.control_placeholder_message)) }, modifier = Modifier.padding(10.dp))

        // interventionType dropdown: stored/selected value stays the English wire literal;
        // only the displayed label is localized (bug #4).
        val typeOptions = interventionTypeDisplayOptions()
        StringOptionDropdown(
            selected = typeOptions.first { it.first == selectedType }.second,
            label = stringResource(R.string.control_field_intervention_type),
            options = typeOptions.map { it.second },
            onSelect = { label -> selectedType = typeOptions.first { it.second == label }.first }
        )

        // Microgame dropdown: selectedGame is GameType?, never a raw display string (bug #1/#2).
        val gameOptions = microgameDisplayOptions()
        StringOptionDropdown(
            selected = gameOptions.first { it.first == selectedGame }.second,
            label = stringResource(R.string.intervention_microgame_section),
            options = gameOptions.map { it.second },
            onSelect = { label -> selectedGame = gameOptions.first { it.second == label }.first }
        )

        Text(stringResource(R.string.control_label_duration_seconds), fontSize = 14.sp, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth())
        SliderWithInput(duration, { duration = it }, 1f..60f, 1f, label = "")

        ConfirmActionButton {
            // "Stop" is a wire value the watch matches on (see WearMessageListener/InterventionActivity)
            // — must stay literal English, same as the Intervention.type wire values above.
            send(context, "Stop", NotificationType.CANCEL, "", 0, null, onWearError)
        }
        Spacer(Modifier.height(16.dp))
    }
}

private fun send(context: Context, type: String, notification: NotificationType,
                 message: String, duration: Int, gameType: GameType?, onWearError: (String) -> Unit) {
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
