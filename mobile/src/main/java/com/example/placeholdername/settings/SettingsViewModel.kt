package com.BWPStudio.JITAIWizard.settings

import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val participant: String = "",
    val durationUntilIntervention: Float = 0f,
    val interventionType: String = "",
    val interventionMessage: String = ""
)

class SettingsViewModel(private val repo: SettingsRepository) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = repo.getSettingsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun <T> setSetting(key: Preferences.Key<T>, value: T) {
        viewModelScope.launch { repo.setSetting(key, value) }
    }
}
