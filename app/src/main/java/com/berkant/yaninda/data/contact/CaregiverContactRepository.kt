package com.berkant.yaninda.data.contact

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.caregiverContactDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "caregiver_contact",
)

interface CaregiverContactRepository {
    val phoneNumber: Flow<String?>

    suspend fun savePhoneNumber(normalizedPhoneNumber: String?)
}

class DataStoreCaregiverContactRepository(
    private val dataStore: DataStore<Preferences>,
) : CaregiverContactRepository {
    override val phoneNumber: Flow<String?> = dataStore.data.map { preferences ->
        preferences[PHONE_NUMBER]?.also { storedNumber ->
            check(STORED_NUMBER_PATTERN.matches(storedNumber)) {
                "The stored caregiver phone number is invalid."
            }
        }
    }

    override suspend fun savePhoneNumber(normalizedPhoneNumber: String?) {
        require(
            normalizedPhoneNumber == null || STORED_NUMBER_PATTERN.matches(normalizedPhoneNumber)
        ) {
            "The caregiver phone number must already be normalized."
        }
        dataStore.edit { preferences ->
            if (normalizedPhoneNumber == null) {
                preferences.remove(PHONE_NUMBER)
            } else {
                preferences[PHONE_NUMBER] = normalizedPhoneNumber
            }
        }
    }

    companion object {
        private val PHONE_NUMBER = stringPreferencesKey("phone_number")
        private val STORED_NUMBER_PATTERN = Regex("^\\+?[0-9]{7,15}$")
    }
}
