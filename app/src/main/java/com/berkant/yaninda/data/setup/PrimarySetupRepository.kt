package com.berkant.yaninda.data.setup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.primarySetupDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "primary_setup",
)

interface PrimarySetupRepository {
    val isCompleted: Flow<Boolean>

    suspend fun complete()
}

class DataStorePrimarySetupRepository(
    private val dataStore: DataStore<Preferences>,
) : PrimarySetupRepository {
    override val isCompleted: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[IS_COMPLETED] ?: false
    }

    override suspend fun complete() {
        dataStore.edit { preferences ->
            preferences[IS_COMPLETED] = true
        }
    }

    private companion object {
        val IS_COMPLETED = booleanPreferencesKey("is_completed")
    }
}
