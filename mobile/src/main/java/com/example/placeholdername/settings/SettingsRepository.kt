package com.BWPStudio.JITAIWizard.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

object SettingsKeys {
    val PARTICIPANT = stringPreferencesKey("participant")
    val DURATION_UNTIL_INTERVENTION = floatPreferencesKey("duration_until_intervention")
    val INTERVENTION_TYPE = stringPreferencesKey("intervention_type")
    val INTERVENTION_MESSAGE = stringPreferencesKey("intervention_message")
}

class SettingsRepository(private val context: Context) {

    fun <T> getSetting(key: Preferences.Key<T>, default: T): Flow<T> =
        context.dataStore.data.map { it[key] ?: default }

    suspend fun <T> setSetting(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }

    fun getSettingsFlow(): Flow<SettingsUiState> =
        context.dataStore.data.map { prefs ->
            SettingsUiState(
                participant = prefs[SettingsKeys.PARTICIPANT] ?: "",
                durationUntilIntervention = prefs[SettingsKeys.DURATION_UNTIL_INTERVENTION] ?: 0f,
                interventionType = prefs[SettingsKeys.INTERVENTION_TYPE] ?: "",
                interventionMessage = prefs[SettingsKeys.INTERVENTION_MESSAGE] ?: ""
            )
        }
}
