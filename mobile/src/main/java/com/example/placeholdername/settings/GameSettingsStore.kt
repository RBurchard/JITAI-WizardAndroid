package com.BWPStudio.JITAIWizard.settings

import android.content.Context
import android.util.Log
import com.BWPStudio.JITAIWizard.datalayer.WearMessageSender
import com.BWPStudio.JITAIWizard.experiment.ExperimentStore
import com.BWPStudio.JITAIWizard.experiment.SessionLogStore
import com.example.jitaicompanion.convention.models.MicrogameSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Keeps the watch's microgame difficulty in step with the active experiment.
 *
 * The settings live on [com.example.jitaicompanion.convention.models.Experiment], not in a
 * preferences file of their own. That is the whole point: the watch has its own store and its
 * own Settings screen, so with two independent copies neither device would be wrong and the
 * pair could silently disagree. With the experiment as the single owner there is exactly one
 * value, loading a schedule sets the difficulty for that condition in the same action that
 * loads its events, and exporting the schedule captures what participants actually faced.
 *
 * Everything funnels through one observer on [ExperimentStore.active]: whoever changed the
 * experiment — this phone's settings screen, a schedule load, an import, a Control Station sync
 * — the new value is pushed to the watch and written to the session log from the same place.
 *
 * Pushes go over the Data Layer, which is unreliable by nature (watch asleep, out of range, app
 * not started). [lastAck] holds what the watch reported back after actually storing a push and
 * [inSync] compares the two, so a researcher can see whether the wrist matches rather than
 * assume it does. [resend] covers the case where it does not.
 */
class GameSettingsStore(
    context: Context,
    private val experimentStore: ExperimentStore,
) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val sender by lazy { WearMessageSender(appContext) }

    /** The active experiment's settings. Read-only; write through [update]. */
    val settings: StateFlow<MicrogameSettings>
        get() = _settings.asStateFlow()

    private val _settings = MutableStateFlow(experimentStore.active.value.gameSettings.sanitized())

    private val _lastAck = MutableStateFlow<MicrogameSettings?>(null)
    /** What the watch last confirmed it stored, or null if it has never acknowledged a push. */
    val lastAck: StateFlow<MicrogameSettings?> = _lastAck.asStateFlow()

    private val _inSync = MutableStateFlow(false)
    /** True only while the watch's acknowledged settings equal the active experiment's. */
    val inSync: StateFlow<Boolean> = _inSync.asStateFlow()

    companion object {
        private const val TAG = "GameSettingsStore"

        /** Session-log `kind` for every settings change, so exports can find them by name. */
        const val LOG_KIND = "game-settings"
    }

    init {
        scope.launch {
            experimentStore.active
                .map { it.gameSettings.sanitized() }
                .distinctUntilChanged()
                // The first emission is just the value already loaded at construction, which
                // JITAIWizardApp pushes explicitly at startup. Logging it as a change would put
                // a spurious "settings changed" row at the head of every session's export.
                .drop(1)
                .collect { next ->
                    _settings.value = next
                    // A changed value has not been acknowledged yet. Showing it as synced until
                    // the watch confirms is exactly the false assurance this class exists to
                    // remove.
                    _inSync.value = _lastAck.value == next
                    push(next, origin = "experiment:${experimentStore.active.value.name}")
                    logChange(next, origin = "experiment:${experimentStore.active.value.name}")
                }
        }
    }

    /**
     * Writes [next] into the active experiment, which propagates it to the watch and the log.
     *
     * @param origin short tag for the log, so an export shows *why* the difficulty changed.
     */
    fun update(next: MicrogameSettings, origin: String) {
        val clean = next.sanitized()
        if (clean == _settings.value) return
        scope.launch {
            val active = experimentStore.active.value
            experimentStore.replace(active.copy(gameSettings = clean))
            Log.i(TAG, "Settings written to experiment '${active.name}' ($origin): ${clean.summary()}")
        }
    }

    /** Re-pushes the current settings, for the "watch says something else" case. */
    fun resend() {
        push(_settings.value, "manual-resend")
    }

    /**
     * Pushes without logging, for moments where the watch may have missed an earlier push
     * (app start, watch reconnect). Not a settings *change*, so it must not add a log row that
     * would read as one in the export.
     */
    fun pushCurrent(reason: String) {
        push(_settings.value, reason)
    }

    /** Records the watch's acknowledgement of a push. Called from the Data Layer listener. */
    fun onWatchAck(rawJson: String) {
        val acked = runCatching { json.decodeFromString<MicrogameSettings>(rawJson) }.getOrNull()
        if (acked == null) {
            Log.w(TAG, "Unparseable settings ack from watch")
            return
        }
        _lastAck.value = acked
        _inSync.value = acked == _settings.value
        scope.launch {
            SessionLogStore.append(
                source = "watch",
                kind = "$LOG_KIND-ack",
                payloadJson = json.encodeToString(acked),
            )
        }
        Log.i(TAG, "Watch acknowledged settings: ${acked.summary()} (inSync=${_inSync.value})")
    }

    private fun push(settings: MicrogameSettings, origin: String) {
        sender.sendGameSettings(json.encodeToString(settings))
        Log.i(TAG, "Pushed settings to watch ($origin): ${settings.summary()}")
    }

    private fun logChange(settings: MicrogameSettings, origin: String) {
        scope.launch {
            // Serialized under the same field names the CSV export uses, so a log row and the
            // export's column headers cannot drift apart.
            val fields = settings.toLogFields().joinToString(",") { (k, v) ->
                "\"$k\":${v.toIntOrNull()?.toString() ?: "\"$v\""}"
            }
            SessionLogStore.append(
                source = "phone",
                kind = LOG_KIND,
                payloadJson = "{\"origin\":\"$origin\",$fields}",
            )
        }
    }
}
