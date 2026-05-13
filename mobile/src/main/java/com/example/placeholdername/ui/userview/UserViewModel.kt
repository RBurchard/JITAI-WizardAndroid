package com.BWPStudio.JITAIWizard.ui.userview

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.BWPStudio.JITAIWizard.datalayer.WatchDataRelay
import com.BWPStudio.JITAIWizard.datalayer.WearMessageSender
import com.BWPStudio.JITAIWizard.server.ServerState
import com.BWPStudio.JITAIWizard.ui.odi.OdiAnimationState
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class UserViewUiState(
    val odiState: OdiAnimationState = OdiAnimationState.IDLE,
    val odiMessage: String = "Monitoring...",
    val lastBpm: Int = 0,
    val lastReactionTimeMs: Long? = null,
    val lastAction: String = "",
    val sessionElapsedMs: Long = 0L,
    val watchConnected: Boolean = false
)

class UserViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UserViewUiState())
    val uiState = _uiState.asStateFlow()

    private var _wasPreviouslyConnected = false

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
                    val bpmFromServer = ServerState.lastHeartRate.toInt()
                    it.copy(
                        sessionElapsedMs = ServerState.uptime,
                        lastReactionTimeMs = ServerState.lastReactionTimeMs.takeIf { v -> v > 0 },
                        lastAction = ServerState.lastAction,
                        watchConnected = ServerState.watchConnected,
                        lastBpm = if (bpmFromServer > 0) bpmFromServer else it.lastBpm
                    )
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                val connected = try {
                    Wearable.getNodeClient(getApplication<Application>()).connectedNodes.await().isNotEmpty()
                } catch (_: Exception) { false }

                ServerState.watchConnected = connected
                _uiState.update { it.copy(watchConnected = connected) }

                // On first connection detected each session, wake the watch so
                // WatchDataService starts if the OS had killed it
                if (connected && !_wasPreviouslyConnected) {
                    WearMessageSender(getApplication()).sendPing(onError = {})
                }
                _wasPreviouslyConnected = connected

                delay(30_000)
            }
        }
    }

    fun setOdiState(state: OdiAnimationState, message: String = "") {
        _uiState.update { it.copy(odiState = state, odiMessage = message.ifEmpty { it.odiMessage }) }
    }
}
