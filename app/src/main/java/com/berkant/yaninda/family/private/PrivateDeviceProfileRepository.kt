package com.berkant.yaninda.family.private

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.privateDeviceProfileDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "private_device_profile",
)

interface PrivateDeviceProfileRepository {
    val profile: Flow<PrivateDeviceProfile?>

    suspend fun save(profile: PrivateDeviceProfile)
}

class DataStorePrivateDeviceProfileRepository(
    private val dataStore: DataStore<Preferences>,
) : PrivateDeviceProfileRepository {
    override val profile: Flow<PrivateDeviceProfile?> = dataStore.data.map { preferences ->
        PrivateDeviceProfile.fromStoredValue(preferences[PROFILE])
    }

    override suspend fun save(profile: PrivateDeviceProfile) {
        dataStore.edit { preferences ->
            preferences[PROFILE] = profile.name
        }
    }

    private companion object {
        val PROFILE = stringPreferencesKey("profile")
    }
}
