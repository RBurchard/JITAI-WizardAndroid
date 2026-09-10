package com.BWPStudio.JITAIWizard.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.BWPStudio.JITAIWizard.JITAIWizardApp
import com.BWPStudio.JITAIWizard.R
import com.example.jitaicompanion.convention.models.LockPenalty
import com.example.jitaicompanion.convention.models.MicrogameSettings
import kotlin.math.roundToInt

/**
 * Phone-side editor for the watch's microgame difficulty.
 *
 * Reachable from both the participant-facing Settings screen and the Wizard view, because both
 * audiences need it at different moments: a researcher sets a participant up from the Wizard
 * view before a run, and adjusts it from ordinary Settings between runs without unlocking debug.
 *
 * Edits are written into the **active experiment**, which is what makes them consistent: the
 * value travels with the schedule, is pushed to the watch, is recorded in the session log, and
 * appears in the CSV export. The header states which schedule is being edited, so nobody
 * changes a difficulty believing it applies globally.
 */
@Composable
fun GameSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as JITAIWizardApp
    val store = app.gameSettingsStore

    val settings by store.settings.collectAsStateWithLifecycle()
    val inSync by store.inSync.collectAsStateWithLifecycle()
    val lastAck by store.lastAck.collectAsStateWithLifecycle()
    val experiment by app.experimentStore.active.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0D1B2A)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                        tint = Color.White
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(R.string.game_settings_title),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.game_settings_scope, experiment.name),
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(16.dp))
            WatchSyncCard(inSync = inSync, lastAck = lastAck, onResend = { store.resend() })

            Spacer(Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.game_simonsays_title))
            SettingSlider(
                label = stringResource(R.string.settings_simon_speed),
                value = settings.simonDifficulty,
                min = MicrogameSettings.MIN_DIFFICULTY,
                max = MicrogameSettings.MAX_DIFFICULTY,
                valueLabel = "${settings.simonDifficulty}/${MicrogameSettings.MAX_DIFFICULTY}",
                description = stringResource(R.string.settings_simon_speed_description),
            ) { value -> store.update(settings.copy(simonDifficulty = value), "phone-ui") }

            SettingSlider(
                label = stringResource(R.string.settings_simon_rounds),
                value = settings.simonRounds,
                min = MicrogameSettings.MIN_ROUNDS,
                max = MicrogameSettings.MAX_ROUNDS,
                valueLabel = "${settings.simonRounds}",
                description = stringResource(R.string.settings_simon_rounds_description),
            ) { value -> store.update(settings.copy(simonRounds = value), "phone-ui") }

            Spacer(Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.game_lockpicking_title))
            SettingSlider(
                label = stringResource(R.string.settings_lock_difficulty),
                value = settings.lockDifficulty,
                min = MicrogameSettings.MIN_DIFFICULTY,
                max = MicrogameSettings.MAX_DIFFICULTY,
                valueLabel = "${settings.lockDifficulty}/${MicrogameSettings.MAX_DIFFICULTY}",
                description = stringResource(R.string.settings_lock_difficulty_description),
            ) { value -> store.update(settings.copy(lockDifficulty = value), "phone-ui") }

            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_lock_penalty),
                color = Color(0xFF8899BB),
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                stringResource(R.string.settings_lock_penalty_description),
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A2A3A), RoundedCornerShape(12.dp))
            ) {
                LockPenalty.entries.forEachIndexed { index, option ->
                    if (index > 0) HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    PenaltyRow(
                        label = stringResource(
                            when (option) {
                                LockPenalty.STUN -> R.string.settings_lock_penalty_stun
                                LockPenalty.PUSHBACK -> R.string.settings_lock_penalty_pushback
                                LockPenalty.RESET -> R.string.settings_lock_penalty_reset
                            }
                        ),
                        selected = settings.lockPenalty == option,
                        onClick = { store.update(settings.copy(lockPenalty = option), "phone-ui") }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.game_settings_footer),
                color = Color.White.copy(alpha = 0.35f),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Whether the watch has confirmed the current values.
 *
 * Shown rather than assumed: a Data Layer push is fire-and-forget, so without this a researcher
 * would have no way to tell a watch running the intended difficulty from one that was asleep
 * when the schedule loaded and is still on the previous participant's settings.
 */
@Composable
private fun WatchSyncCard(
    inSync: Boolean,
    lastAck: MicrogameSettings?,
    onResend: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (inSync) Color(0xFF14351F) else Color(0xFF3A2A14)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(
                        if (inSync) R.string.game_settings_watch_in_sync
                        else R.string.game_settings_watch_out_of_sync
                    ),
                    color = if (inSync) Color(0xFF7BD88F) else Color(0xFFE0B457),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = lastAck?.summary()
                        ?: stringResource(R.string.game_settings_watch_never_acked),
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (!inSync) {
                Button(onClick = onResend) { Text(stringResource(R.string.game_settings_resend)) }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = Color(0xFF42A5F5),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(4.dp))
}

/**
 * A labelled integer slider.
 *
 * Commits on every step rather than only on release: each commit writes the active experiment
 * and pushes to the watch, and a researcher dragging while the participant watches expects the
 * wrist to follow. The write is cheap and idempotent, and [GameSettingsStore.update] drops
 * no-op changes.
 */
@Composable
private fun SettingSlider(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    valueLabel: String,
    description: String,
    onChange: (Int) -> Unit,
) {
    // Local float mirror so the thumb tracks the finger smoothly between integer steps.
    var raw by remember(value) { mutableStateOf(value.toFloat()) }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color.White, style = MaterialTheme.typography.bodyLarge)
            Text(valueLabel, color = Color(0xFF42A5F5), fontWeight = FontWeight.Bold)
        }
        Text(
            description,
            color = Color.White.copy(alpha = 0.45f),
            style = MaterialTheme.typography.bodySmall
        )
        Slider(
            value = raw,
            onValueChange = { next ->
                raw = next
                val stepped = next.roundToInt().coerceIn(min, max)
                if (stepped != value) onChange(stepped)
            },
            valueRange = min.toFloat()..max.toFloat(),
            steps = (max - min - 1).coerceAtLeast(0),
        )
    }
}

@Composable
private fun PenaltyRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White)
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = Color(0xFF42A5F5),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
