package com.berkant.yaninda.data.device

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.berkant.yaninda.domain.family.DeviceRole
import com.berkant.yaninda.domain.family.FamilyPairing
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

val Context.deviceIdentityDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "device_identity",
)

interface DeviceIdentityRepository {
    val selectedRole: Flow<DeviceRole?>
    val pairing: Flow<FamilyPairing?>

    suspend fun selectRole(role: DeviceRole)

    suspend fun getOrCreateDeviceId(): String

    suspend fun recordPairing(pairing: FamilyPairing)

    suspend fun clearPairing()
}

class DataStoreDeviceIdentityRepository(
    private val dataStore: DataStore<Preferences>,
    private val createDeviceId: () -> String = { UUID.randomUUID().toString() },
) : DeviceIdentityRepository {
    private val identityMutex = Mutex()

    override val selectedRole: Flow<DeviceRole?> = dataStore.data.map { preferences ->
        preferences[SELECTED_ROLE]?.toDeviceRole()
    }

    override val pairing: Flow<FamilyPairing?> = dataStore.data.map { preferences ->
        val familyId = preferences[PAIRED_FAMILY_ID]
        val role = preferences[PAIRED_DEVICE_ROLE]?.toDeviceRole()
        check((familyId == null) == (role == null)) {
            "Stored family pairing is incomplete."
        }
        if (familyId == null || role == null) {
            null
        } else {
            FamilyPairing(familyId = familyId, deviceRole = role)
        }
    }

    override suspend fun selectRole(role: DeviceRole) {
        dataStore.edit { preferences ->
            preferences[SELECTED_ROLE] = role.name
        }
    }

    override suspend fun getOrCreateDeviceId(): String = identityMutex.withLock {
        dataStore.data.first()[DEVICE_ID]?.let { return@withLock it }
        val deviceId = createDeviceId()
        require(deviceId.isNotBlank() && deviceId.length <= MAX_ID_LENGTH) {
            "Generated device identity is invalid."
        }
        dataStore.edit { preferences ->
            preferences[DEVICE_ID] = deviceId
        }
        deviceId
    }

    override suspend fun recordPairing(pairing: FamilyPairing) {
        require(pairing.familyId.isNotBlank() && pairing.familyId.length <= MAX_ID_LENGTH) {
            "Family identity is invalid."
        }
        dataStore.edit { preferences ->
            preferences[PAIRED_FAMILY_ID] = pairing.familyId
            preferences[PAIRED_DEVICE_ROLE] = pairing.deviceRole.name
            preferences[SELECTED_ROLE] = pairing.deviceRole.name
        }
    }

    override suspend fun clearPairing() {
        dataStore.edit { preferences ->
            preferences.remove(PAIRED_FAMILY_ID)
            preferences.remove(PAIRED_DEVICE_ROLE)
        }
    }

    private companion object {
        const val MAX_ID_LENGTH = 128
        val DEVICE_ID = stringPreferencesKey("device_id")
        val SELECTED_ROLE = stringPreferencesKey("selected_role")
        val PAIRED_FAMILY_ID = stringPreferencesKey("paired_family_id")
        val PAIRED_DEVICE_ROLE = stringPreferencesKey("paired_device_role")

        private fun String.toDeviceRole(): DeviceRole? = when (this) {
            "ALARM_DEVICE" -> DeviceRole.ALARM_DEVICE
            "ADMIN_DEVICE" -> DeviceRole.ADMIN_DEVICE
            // Legacy mapping
            "PRIMARY_MEDICATION_DEVICE" -> DeviceRole.ALARM_DEVICE
            "CAREGIVER_DEVICE" -> DeviceRole.ADMIN_DEVICE
            else -> runCatching { DeviceRole.valueOf(this) }.getOrNull()
        }
    }
}
