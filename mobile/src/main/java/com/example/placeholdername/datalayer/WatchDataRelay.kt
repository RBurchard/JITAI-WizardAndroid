package com.BWPStudio.JITAIWizard.datalayer

import com.example.jitaicompanion.convention.models.WatchDataBatch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object WatchDataRelay {
    private val _flow = MutableSharedFlow<WatchDataBatch>(replay = 1, extraBufferCapacity = 64)
    val flow = _flow.asSharedFlow()

    suspend fun emit(batch: WatchDataBatch) {
        _flow.emit(batch)
    }
}
