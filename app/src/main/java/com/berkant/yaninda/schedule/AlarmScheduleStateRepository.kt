package com.berkant.yaninda.data.schedule

import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first

val Context.alarmScheduleStateDataStore by preferencesDataStore(
    name = "alarm_schedule_state",
)

interface AlarmScheduleStateRepository {

    suspend fun getAppliedVersion(
        familyId: String,
    ): Long

    suspend fun recordAppliedVersion(
        familyId: String,
        version: Long,
    )
}

class DataStoreAlarmScheduleStateRepository(
    private val dataStore: DataStore<Preferences>,
) : AlarmScheduleStateRepository {

    override suspend fun getAppliedVersion(
        familyId: String,
    ): Long {
        val preferences = dataStore.data.first()

        val storedFamilyId =
            preferences[FAMILY_ID]

        if (storedFamilyId != familyId) {
            return 0L
        }

        return preferences[APPLIED_VERSION] ?: 0L
    }

    override suspend fun recordAppliedVersion(
        familyId: String,
        version: Long,
    ) {
        require(familyId.isNotBlank())
        require(version >= 0L)

        dataStore.edit { preferences ->
            preferences[FAMILY_ID] = familyId
            preferences[APPLIED_VERSION] = version
        }
    }

    private companion object {
        val FAMILY_ID =
            stringPreferencesKey("family_id")

        val APPLIED_VERSION =
            longPreferencesKey("applied_schedule_version")
    }
}