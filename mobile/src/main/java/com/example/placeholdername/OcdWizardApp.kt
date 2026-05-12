package com.BWPStudio.JITAIWizard

import android.app.Application
import com.BWPStudio.JITAIWizard.experiment.ExperimentLogger
import com.BWPStudio.JITAIWizard.server.KtorServer

class OcdWizardApp : Application() {

    val logger = ExperimentLogger(this)
    private val server = KtorServer(this)

    override fun onCreate() {
        super.onCreate()
        server.start()
    }

    override fun onTerminate() {
        super.onTerminate()
        server.stop()
    }
}
