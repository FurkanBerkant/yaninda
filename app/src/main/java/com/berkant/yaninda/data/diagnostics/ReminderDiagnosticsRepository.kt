package com.berkant.yaninda.data.diagnostics

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.Instant
import kotlinx.coroutines.flow.first

val Context.reminderDiagnosticsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "reminder_diagnostics",
)

enum class AlarmDeliveryOutcome {
    DELIVERED,
    BLOCKED,
    PLATFORM_FAILURE,
}

data class AlarmDeliveryDiagnostic(
    val firedAt: Instant,
    val outcome: AlarmDeliveryOutcome,
)

data class ReminderDiagnosticsSnapshot(
    val lastMedicationAlarm: AlarmDeliveryDiagnostic? = null,
    val lastTestAlarm: AlarmDeliveryDiagnostic? = null,
)

interface ReminderDiagnosticsRepository {
    suspend fun read(): ReminderDiagnosticsSnapshot

    suspend fun recordMedicationAlarm(diagnostic: AlarmDeliveryDiagnostic)

    suspend fun recordTestAlarm(diagnostic: AlarmDeliveryDiagnostic)
}

class DataStoreReminderDiagnosticsRepository(
    private val dataStore: DataStore<Preferences>,
) : ReminderDiagnosticsRepository {
    override suspend fun read(): ReminderDiagnosticsSnapshot {
        val preferences = dataStore.data.first()
        return ReminderDiagnosticsSnapshot(
            lastMedicationAlarm = preferences.readDiagnostic(MEDICATION_KEYS),
            lastTestAlarm = preferences.readDiagnostic(TEST_KEYS),
        )
    }

    override suspend fun recordMedicationAlarm(diagnostic: AlarmDeliveryDiagnostic) {
        dataStore.edit { preferences ->
            preferences.writeDiagnostic(MEDICATION_KEYS, diagnostic)
        }
    }

    override suspend fun recordTestAlarm(diagnostic: AlarmDeliveryDiagnostic) {
        dataStore.edit { preferences ->
            preferences.writeDiagnostic(TEST_KEYS, diagnostic)
        }
    }

    private fun Preferences.readDiagnostic(keys: DiagnosticKeys): AlarmDeliveryDiagnostic? {
        val firedAtEpochMillis = this[keys.firedAt]
        val outcomeName = this[keys.outcome]
        if (firedAtEpochMillis == null && outcomeName == null) return null
        check(firedAtEpochMillis != null && outcomeName != null) {
            "Reminder diagnostics storage is incomplete."
        }
        val outcome = AlarmDeliveryOutcome.entries.firstOrNull { it.name == outcomeName }
        checkNotNull(outcome) { "Reminder diagnostics storage contains an unknown outcome." }
        return AlarmDeliveryDiagnostic(
            firedAt = Instant.ofEpochMilli(firedAtEpochMillis),
            outcome = outcome,
        )
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.writeDiagnostic(
        keys: DiagnosticKeys,
        diagnostic: AlarmDeliveryDiagnostic,
    ) {
        this[keys.firedAt] = diagnostic.firedAt.toEpochMilli()
        this[keys.outcome] = diagnostic.outcome.name
    }

    private data class DiagnosticKeys(
        val firedAt: Preferences.Key<Long>,
        val outcome: Preferences.Key<String>,
    )

    companion object {
        private val MEDICATION_KEYS = DiagnosticKeys(
            firedAt = longPreferencesKey("last_medication_alarm_fired_at"),
            outcome = stringPreferencesKey("last_medication_alarm_delivery_outcome"),
        )
        private val TEST_KEYS = DiagnosticKeys(
            firedAt = longPreferencesKey("last_test_alarm_fired_at"),
            outcome = stringPreferencesKey("last_test_alarm_delivery_outcome"),
        )
    }
}
