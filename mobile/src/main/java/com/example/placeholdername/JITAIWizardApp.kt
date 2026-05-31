package com.BWPStudio.JITAIWizard

import android.app.Application
import com.BWPStudio.JITAIWizard.datalayer.WearSyncLogger
import com.BWPStudio.JITAIWizard.experiment.ExperimentEngine
import com.BWPStudio.JITAIWizard.experiment.ExperimentLogger
import com.BWPStudio.JITAIWizard.experiment.ExperimentStore
import com.BWPStudio.JITAIWizard.server.KtorServer
import com.BWPStudio.JITAIWizard.server.UdpBeacon
import com.BWPStudio.JITAIWizard.triggers.TriggerEngine

class JITAIWizardApp : Application() {

    val logger = ExperimentLogger(this)
    val wearSyncLogger by lazy { WearSyncLogger(this) }
    lateinit var experimentStore: ExperimentStore
        private set
    lateinit var experimentEngine: ExperimentEngine
        private set
    private lateinit var server: KtorServer
    private val beacon = UdpBeacon(this)

    override fun onCreate() {
        super.onCreate()
        experimentStore = ExperimentStore(this)
        experimentEngine = ExperimentEngine(this, experimentStore)
        TriggerEngine.setTriggers(experimentStore.active.value.triggers)
        server = KtorServer(this)
        server.start()
        beacon.start()
    }

    override fun onTerminate() {
        super.onTerminate()
        beacon.stop()
        server.stop()
    }
}
