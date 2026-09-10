package com.BWPStudio.JITAIWizard.ui.debugview

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jitaicompanion.convention.models.GameType
import com.example.jitaicompanion.convention.models.Intervention
import com.example.jitaicompanion.convention.models.LockPenalty
import com.example.jitaicompanion.convention.models.MicrogameSettings
import com.example.jitaicompanion.convention.models.NotificationType
import com.BWPStudio.JITAIWizard.JITAIWizardApp
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
fun ControlScreen(onWearError: (String) -> Unit = {}, onGameSettings: () -> Unit = {}) {
    val context = LocalContext.current
    // control_default_message existed but was never read; the literal here meant a German
    // or Dutch participant still got an English default on the watch.
    val defaultMessage = stringResource(R.string.control_default_message)
    var message by remember { mutableStateOf(defaultMessage) }
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
            .navigationBarsPadding()
            .padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(6.dp))

        // The difficulty is also on the banner as an icon, but it is spelled out here with the
        // values currently in force: a wizard setting a participant up wants to read the
        // difficulty without opening the editor to check it.
        GameSettingsCard(onGameSettings)

        Spacer(Modifier.height(8.dp))

        SectionCard(title = stringResource(R.string.control_title)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SendButton(stringResource(R.string.control_button_vib_short), Modifier.weight(1f)) {
                    send(context, selectedType, NotificationType.VIBRATION1, message, duration.toInt(), selectedGame, onWearError)
                }
                SendButton(stringResource(R.string.control_button_vib_long), Modifier.weight(1f)) {
                    send(context, selectedType, NotificationType.VIBRATION2, message, duration.toInt(), selectedGame, onWearError)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SendButton(stringResource(R.string.control_button_sound_short), Modifier.weight(1f)) {
                    send(context, selectedType, NotificationType.SOUND, message, duration.toInt(), selectedGame, onWearError)
                }
                SendButton(stringResource(R.string.control_button_sound_long), Modifier.weight(1f)) {
                    send(context, selectedType, NotificationType.SOUND, message, duration.toInt(), selectedGame, onWearError)
                }
            }
            // Fixed dp widths removed in favor of weight(1f) + maxLines/ellipsis so longer
            // German/Dutch labels ("Vib.+Ton kurz" etc.) do not clip.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SendButton(stringResource(R.string.control_button_vib_sound_short), Modifier.weight(1f)) {
                    send(context, selectedType, NotificationType.VIBRATION_SOUND, message, duration.toInt(), selectedGame, onWearError)
                }
                SendButton(stringResource(R.string.control_button_vib_sound_long), Modifier.weight(1f)) {
                    send(context, selectedType, NotificationType.VIBRATION_SOUND2, message, duration.toInt(), selectedGame, onWearError)
                }
            }
            // The repeating variants keep going until they are answered or cancelled, so they
            // are tinted apart from the one-shot buttons above rather than sitting in the same
            // grid of identical blue rectangles.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SendButton(stringResource(R.string.control_button_perma_vib), Modifier.weight(1f), insistent = true) {
                    send(context, selectedType, NotificationType.ANNOY_VIB, message, duration.toInt(), selectedGame, onWearError)
                }
                SendButton(stringResource(R.string.control_button_perma_sound), Modifier.weight(1f), insistent = true) {
                    send(context, selectedType, NotificationType.ANNOY_SOUND, message, duration.toInt(), selectedGame, onWearError)
                }
                SendButton(stringResource(R.string.control_button_perma_both), Modifier.weight(1f), insistent = true) {
                    send(context, selectedType, NotificationType.ANNOY_VIBRATION_SOUND, message, duration.toInt(), selectedGame, onWearError)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        SectionCard(title = null) {
            Text(
                stringResource(R.string.control_label_message),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.control_placeholder_message)) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.control_label_duration_seconds),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${duration.toInt()}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            SliderWithInput(duration, { duration = it }, 1f..60f, 1f, label = "")
        }

        Spacer(Modifier.height(10.dp))
        ConfirmActionButton {
            // "Stop" is a wire value the watch matches on (see WearMessageListener/InterventionActivity)
            // — must stay literal English, same as the Intervention.type wire values above.
            send(context, "Stop", NotificationType.CANCEL, "", 0, null, onWearError)
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** A titled panel. Groups the console into "what to send" and "what it says". */
@Composable
private fun SectionCard(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (title != null) {
                Text(
                    title,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp
                )
            }
            content()
        }
    }
}

@Composable
private fun SendButton(
    label: String,
    modifier: Modifier = Modifier,
    insistent: Boolean = false,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
        colors = if (insistent) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
        } else {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    ) {
        Text(
            label,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            fontSize = 12.sp,
            lineHeight = 14.sp
        )
    }
}

/**
 * Shows the mini-game difficulty currently attached to the active schedule, and opens the editor.
 */
@Composable
private fun GameSettingsCard(onGameSettings: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as JITAIWizardApp
    val settings by app.gameSettingsStore.settings.collectAsState()
    val inSync by app.gameSettingsStore.inSync.collectAsState()

    val max = MicrogameSettings.MAX_DIFFICULTY
    val penalty = stringResource(
        when (settings.lockPenalty) {
            LockPenalty.STUN -> R.string.settings_lock_penalty_stun
            LockPenalty.PUSHBACK -> R.string.settings_lock_penalty_pushback
            LockPenalty.RESET -> R.string.settings_lock_penalty_reset
        }
    )
    // Named by game rather than by the settings screen's short field labels: "Speed 2/5" is
    // clear under a "Simon Says" heading, but not on a line of its own.
    val summary = listOf(
        "${stringResource(R.string.game_simonsays_title)} ${settings.simonDifficulty}/$max (${settings.simonRounds}x)",
        "${stringResource(R.string.game_lockpicking_title)} ${settings.lockDifficulty}/$max",
        penalty,
    ).joinToString("  ·  ")

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onGameSettings)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Filled.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.game_settings_title),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.width(6.dp))
                    // The watch confirms what it stored; an unconfirmed push is worth seeing
                    // here rather than only inside the editor.
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                if (inSync) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.tertiary,
                                RoundedCornerShape(50)
                            )
                    )
                }
                Text(
                    summary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text("›", color = MaterialTheme.colorScheme.primary, fontSize = 20.sp)
        }
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
