package com.BWPStudio.JITAIWizard.experiment

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable

@Serializable
data class InterventionResponse(
    val ts: Long,
    val payload: String,
    val reactionMs: Long?,
    val actionId: String? = null
)

object InterventionResponseBus {
    private val _flow = MutableSharedFlow<InterventionResponse>(replay = 0, extraBufferCapacity = 16)
    val flow = _flow.asSharedFlow()

    suspend fun emit(response: InterventionResponse) {
        _flow.emit(response)
    }
}
