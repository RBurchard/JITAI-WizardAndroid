package com.BWPStudio.JITAIWizard.triggers

import com.example.jitaicompanion.convention.models.TriggerEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object TriggerBus {
    private val _flow = MutableSharedFlow<TriggerEvent>(replay = 0, extraBufferCapacity = 64)
    val flow = _flow.asSharedFlow()

    suspend fun emit(event: TriggerEvent) {
        _flow.emit(event)
    }
}
