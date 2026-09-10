package com.BWPStudio.JITAIWizard

import android.app.Application
import android.content.Context
import com.BWPStudio.JITAIWizard.datalayer.WearSyncLogger
import com.BWPStudio.JITAIWizard.experiment.ExperimentEngine
import com.BWPStudio.JITAIWizard.experiment.ExperimentLogger
import com.BWPStudio.JITAIWizard.experiment.ExperimentStore
import com.BWPStudio.JITAIWizard.experiment.PhoneCsvLogger
import com.BWPStudio.JITAIWizard.server.KtorServer
import com.BWPStudio.JITAIWizard.server.ServerState
import com.BWPStudio.JITAIWizard.server.UdpBeacon
import com.BWPStudio.JITAIWizard.settings.GameSettingsStore
import com.BWPStudio.JITAIWizard.settings.SettingsKeys
import com.BWPStudio.JITAIWizard.settings.SettingsRepository
import com.BWPStudio.JITAIWizard.triggers.TriggerEngine
import com.example.jitaicompanion.convention.locale.LocaleController
import com.example.jitaicompanion.convention.models.ParticipantInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.UUID

class JITAIWizardApp : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleController.wrap(base))
    }

    val logger = ExperimentLogger(this)
    val csvLogger by lazy { PhoneCsvLogger(this) }
    val wearSyncLogger by lazy { WearSyncLogger(this) }
    lateinit var experimentStore: ExperimentStore
        private set
    lateinit var experimentEngine: ExperimentEngine
        private set
    lateinit var gameSettingsStore: GameSettingsStore
        private set
    private lateinit var server: KtorServer
    private val beacon = UdpBeacon(this)

    override fun onCreate() {
        super.onCreate()
        experimentStore = ExperimentStore(this)
        experimentEngine = ExperimentEngine(this, experimentStore)
        gameSettingsStore = GameSettingsStore(this, experimentStore)
        TriggerEngine.setTriggers(experimentStore.active.value.triggers)

        // The watch keeps its own copy across reboots, so it may be running whatever the last
        // session left behind. Pushing the active experiment's settings at startup makes the
        // pair agree before anyone opens a screen.
        gameSettingsStore.pushCurrent("app_start")

        // Restore the last-known participant so GET /participant exposes it to the
        // ControlStation immediately, even before a session has been started.
        val savedParticipant = runCatching {
            runBlocking { SettingsRepository(this@JITAIWizardApp).getSetting(SettingsKeys.PARTICIPANT, "").first() }
        }.getOrDefault("")
        if (savedParticipant.isNotBlank()) {
            ServerState.participantInfo = ParticipantInfo(
                id = UUID.randomUUID().toString(),
                sessionId = "",
                label = savedParticipant,
                deviceIp = ""
            )
        }

        server = KtorServer(this)
        server.start()
        beacon.start()
    }

    override fun onTerminate() {
        super.onTerminate()
        beacon.stop()
        server.stop()
    }

    /** Stops all background services (HTTP server + UDP beacon) so the app can fully close. */
    fun shutdown() {
        runCatching { beacon.stop() }
        runCatching { server.stop() }
    }
}
