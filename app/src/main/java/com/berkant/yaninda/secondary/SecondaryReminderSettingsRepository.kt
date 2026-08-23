package com.berkant.yaninda.secondary

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.secondaryReminderDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "secondary_reminder_settings",
)

interface SecondaryReminderSettingsRepository {
    val enabled: Flow<Boolean>

    suspend fun isEnabled(): Boolean

    suspend fun setEnabled(enabled: Boolean)
}

class DataStoreSecondaryReminderSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SecondaryReminderSettingsRepository {
    override val enabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[ENABLED] ?: false
    }

    override suspend fun isEnabled(): Boolean = enabled.first()

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[ENABLED] = enabled }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("enabled")
    }
}
