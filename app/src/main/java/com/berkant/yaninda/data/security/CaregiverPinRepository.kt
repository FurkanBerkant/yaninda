package com.berkant.yaninda.data.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

val Context.caregiverSecurityDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "caregiver_security",
)

interface CaregiverPinRepository {
    val isConfigured: Flow<Boolean>

    suspend fun configure(pin: String)

    suspend fun verify(pin: String): Boolean
}

class DataStoreCaregiverPinRepository(
    private val dataStore: DataStore<Preferences>,
    private val pinHasher: PinHasher,
) : CaregiverPinRepository {
    override val isConfigured: Flow<Boolean> = dataStore.data.map { it.hasCompletePinRecord() }

    override suspend fun configure(pin: String) {
        require(PIN_PATTERN.matches(pin)) { "Caregiver PIN must contain 4 to 6 digits." }
        val pinChars = pin.toCharArray()
        val stored = try {
            withContext(Dispatchers.Default) { pinHasher.create(pinChars) }
        } finally {
            pinChars.fill('\u0000')
        }
        dataStore.edit { preferences ->
            preferences[PIN_SALT] = stored.saltBase64
            preferences[PIN_HASH] = stored.hashBase64
            preferences[PIN_ITERATIONS] = stored.iterations
        }
    }

    override suspend fun verify(pin: String): Boolean {
        if (!PIN_PATTERN.matches(pin)) return false
        val stored = dataStore.data.first().toStoredPinHash() ?: return false
        val pinChars = pin.toCharArray()
        return try {
            withContext(Dispatchers.Default) { pinHasher.verify(pinChars, stored) }
        } finally {
            pinChars.fill('\u0000')
        }
    }

    private fun Preferences.toStoredPinHash(): StoredPinHash? {
        val salt = this[PIN_SALT]
        val hash = this[PIN_HASH]
        val iterations = this[PIN_ITERATIONS]
        if (salt == null && hash == null && iterations == null) return null
        check(salt != null && hash != null && iterations != null) {
            "Caregiver PIN storage is incomplete."
        }
        return StoredPinHash(salt, hash, iterations)
    }

    private fun Preferences.hasCompletePinRecord(): Boolean {
        val presentValues = listOf(this[PIN_SALT], this[PIN_HASH], this[PIN_ITERATIONS])
            .count { it != null }
        check(presentValues == 0 || presentValues == 3) {
            "Caregiver PIN storage is incomplete."
        }
        return presentValues == 3
    }

    companion object {
        private val PIN_PATTERN = Regex("^[0-9]{4,6}$")
        private val PIN_SALT = stringPreferencesKey("pin_salt")
        private val PIN_HASH = stringPreferencesKey("pin_hash")
        private val PIN_ITERATIONS = intPreferencesKey("pin_iterations")
    }
}
