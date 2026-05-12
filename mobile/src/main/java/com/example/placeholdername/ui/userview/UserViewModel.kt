package com.BWPStudio.JITAIWizard.ui.userview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.BWPStudio.JITAIWizard.datalayer.WatchDataRelay
import com.BWPStudio.JITAIWizard.server.ServerState
import com.BWPStudio.JITAIWizard.ui.odi.OdiAnimationState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UserViewUiState(
    val odiState: OdiAnimationState = OdiAnimationState.IDLE,
    val odiMessage: String = "Monitoring...",
    val lastBpm: Int = 0,
    val lastReactionTimeMs: Long? = null,
    val lastAction: String = "",
    val sessionElapsedMs: Long = 0L,
    val watchConnected: Boolean = false
)

class UserViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(UserViewUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            WatchDataRelay.flow.collect { batch ->
                val hr = batch.snapshots.lastOrNull()?.heartRate?.toInt() ?: 0
                _uiState.update { it.copy(lastBpm = hr, watchConnected = true) }
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(1000)
                _uiState.update {
                    it.copy(
                        sessionElapsedMs = ServerState.uptime,
                        lastReactionTimeMs = ServerState.lastReactionTimeMs.takeIf { v -> v > 0 },
                        lastAction = ServerState.lastAction
                    )
                }
            }
        }
    }

    fun setOdiState(state: OdiAnimationState, message: String = "") {
        _uiState.update { it.copy(odiState = state, odiMessage = message.ifEmpty { it.odiMessage }) }
    }
}
