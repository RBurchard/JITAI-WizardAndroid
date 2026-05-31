package com.BWPStudio.JITAIWizard.triggers

import com.example.jitaicompanion.convention.models.TriggerEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object ManualTriggerSource {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun fire(triggerId: String, source: String = "manual-phone", details: String = "") {
        scope.launch {
            TriggerBus.emit(
                TriggerEvent(
                    triggerId = triggerId,
                    ts = System.currentTimeMillis(),
                    source = source,
                    details = details
                )
            )
        }
    }
}
