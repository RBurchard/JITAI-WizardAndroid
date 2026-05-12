package com.BWPStudio.JITAIWizard

import android.app.Application
import com.BWPStudio.JITAIWizard.datalayer.WearSyncLogger
import com.BWPStudio.JITAIWizard.experiment.ExperimentLogger
import com.BWPStudio.JITAIWizard.server.KtorServer
import com.BWPStudio.JITAIWizard.server.UdpBeacon

class JITAIWizardApp : Application() {

    val logger = ExperimentLogger(this)
    val wearSyncLogger by lazy { WearSyncLogger(this) }
    private val server = KtorServer(this)
    private val beacon = UdpBeacon(this)

    override fun onCreate() {
        super.onCreate()
        server.start()
        beacon.start()
    }

    override fun onTerminate() {
        super.onTerminate()
        beacon.stop()
        server.stop()
    }
}
