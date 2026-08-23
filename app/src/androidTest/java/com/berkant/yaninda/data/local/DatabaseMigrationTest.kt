package com.berkant.yaninda.data.local

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.berkant.yaninda.domain.sync.SyncEventIdFactory
import com.berkant.yaninda.domain.sync.SyncEventType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        YanindaDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @After
    fun deleteTestDatabase() {
        context.deleteDatabase(TEST_DATABASE_NAME)
    }

    @Test
    fun migrate1To2_preservesMedicationAndCreatesOccurrenceTable() {
        migrationHelper.createDatabase(TEST_DATABASE_NAME, 1).apply {
            execSQL(
                """
                INSERT INTO medications (
                    id, displayName, dosageText, instructionText, photoUri,
                    scheduleType, active, createdAtEpochMillis, updatedAtEpochMillis, version
                ) VALUES (
                    'medication-1', 'Test ilacı', 'Yazılı doz', 'Yazılı talimat', NULL,
                    'FIXED_ONLY', 1, 100, 100, 1
                )
                """.trimIndent()
            )
            close()
        }

        migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE_NAME,
            2,
            true,
            MIGRATION_1_2,
        ).use { database ->
            database.query("SELECT COUNT(*) FROM medications").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            database.query(
                "SELECT COUNT(*) FROM sqlite_master " +
                    "WHERE type = 'table' AND name = 'dose_occurrences'"
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
        }
    }

    @Test
    fun migrate2To3_preservesPendingReminderTimesForScheduledAndSnoozedOccurrences() {
        migrationHelper.createDatabase(TEST_DATABASE_NAME, 2).apply {
            execSQL(
                """
                INSERT INTO medications (
                    id, displayName, dosageText, instructionText, photoUri,
                    scheduleType, active, createdAtEpochMillis, updatedAtEpochMillis, version
                ) VALUES (
                    'medication-1', 'Test ilacı', 'Yazılı doz', 'Yazılı talimat', NULL,
                    'FIXED_ONLY', 1, 100, 100, 1
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO medication_schedules (
                    id, medicationId, localTimeMinutes, daysOfWeekMask,
                    validFromEpochDay, validUntilEpochDay, snoozeEnabled,
                    snoozeMinutes, maxSnoozes, createdAtEpochMillis,
                    updatedAtEpochMillis, version
                ) VALUES (
                    'schedule-1', 'medication-1', 1200, 127,
                    1, NULL, 1, 10, 2, 100, 100, 1
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO dose_occurrences (
                    id, medicationId, scheduleId, scheduledAtEpochMillis, status,
                    acknowledgedAtEpochMillis, acknowledgementActor, snoozeCount,
                    lastAlertedAtEpochMillis, createdAtEpochMillis,
                    updatedAtEpochMillis, version
                ) VALUES
                    ('scheduled', 'medication-1', 'schedule-1', 1000, 'SCHEDULED',
                     NULL, NULL, 0, NULL, 100, 100, 1),
                    ('snoozed', 'medication-1', 'schedule-1', 2000, 'SNOOZED',
                     NULL, NULL, 1, 2000, 100, 3000, 3)
                """.trimIndent()
            )
            close()
        }

        migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE_NAME,
            3,
            true,
            MIGRATION_2_3,
        ).use { database ->
            database.query(
                "SELECT id, nextReminderAtEpochMillis FROM dose_occurrences ORDER BY id"
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("scheduled", cursor.getString(0))
                assertEquals(1000L, cursor.getLong(1))
                cursor.moveToNext()
                assertEquals("snoozed", cursor.getString(0))
                assertEquals(603000L, cursor.getLong(1))
            }
        }
    }

    @Test
    fun migrate3To4_backfillsAcknowledgementsIntoPendingOutbox() {
        migrationHelper.createDatabase(TEST_DATABASE_NAME, 3).apply {
            execSQL(
                """
                INSERT INTO medications (
                    id, displayName, dosageText, instructionText, photoUri,
                    scheduleType, active, createdAtEpochMillis, updatedAtEpochMillis, version
                ) VALUES (
                    'medication-1', 'Test ilacı', 'Yazılı doz', 'Yazılı talimat', NULL,
                    'FIXED_ONLY', 1, 100, 100, 1
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO medication_schedules (
                    id, medicationId, localTimeMinutes, daysOfWeekMask,
                    validFromEpochDay, validUntilEpochDay, snoozeEnabled,
                    snoozeMinutes, maxSnoozes, createdAtEpochMillis,
                    updatedAtEpochMillis, version
                ) VALUES (
                    'schedule-1', 'medication-1', 1200, 127,
                    1, NULL, 1, 10, 2, 100, 100, 1
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO dose_occurrences (
                    id, medicationId, scheduleId, scheduledAtEpochMillis, status,
                    acknowledgedAtEpochMillis, acknowledgementActor, snoozeCount,
                    lastAlertedAtEpochMillis, nextReminderAtEpochMillis,
                    createdAtEpochMillis, updatedAtEpochMillis, version
                ) VALUES
                    ('acknowledged', 'medication-1', 'schedule-1', 1000,
                     'ACKNOWLEDGED_TAKEN', 1200, 'GRANDFATHER', 0,
                     1000, NULL, 100, 1200, 3),
                    ('scheduled', 'medication-1', 'schedule-1', 2000,
                     'SCHEDULED', NULL, NULL, 0,
                     NULL, 2000, 100, 100, 1)
                """.trimIndent()
            )
            close()
        }

        migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE_NAME,
            4,
            true,
            MIGRATION_3_4,
        ).use { database ->
            database.query(
                "SELECT id, eventType, aggregateId, aggregateVersion, " +
                    "createdAtEpochMillis, attemptCount, syncState FROM sync_outbox"
            ).use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals(
                    SyncEventIdFactory.create(
                        SyncEventType.DOSE_OCCURRENCE_ACKNOWLEDGED,
                        "acknowledged",
                        3L,
                    ),
                    cursor.getString(0),
                )
                assertEquals(
                    SyncEventType.DOSE_OCCURRENCE_ACKNOWLEDGED.name,
                    cursor.getString(1),
                )
                assertEquals("acknowledged", cursor.getString(2))
                assertEquals(3L, cursor.getLong(3))
                assertEquals(1200L, cursor.getLong(4))
                assertEquals(0, cursor.getInt(5))
                assertEquals("PENDING", cursor.getString(6))
            }
        }
    }

    @Test
    fun migrate4To5_createsSecondaryReminderCacheWithoutChangingPrimaryData() {
        migrationHelper.createDatabase(TEST_DATABASE_NAME, 4).apply {
            execSQL(
                """
                INSERT INTO medications (
                    id, displayName, dosageText, instructionText, photoUri,
                    scheduleType, active, createdAtEpochMillis, updatedAtEpochMillis, version
                ) VALUES (
                    'medication-1', 'Test ilacı', 'Yazılı doz', 'Yazılı talimat', NULL,
                    'FIXED_ONLY', 1, 100, 100, 1
                )
                """.trimIndent()
            )
            close()
        }

        migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE_NAME,
            5,
            true,
            MIGRATION_4_5,
        ).use { database ->
            database.query("SELECT COUNT(*) FROM medications").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            database.query(
                "SELECT COUNT(*) FROM sqlite_master " +
                    "WHERE type = 'table' AND name = 'secondary_reminder_cache'"
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
        }
    }

    companion object {
        private const val TEST_DATABASE_NAME = "migration-test"
    }
}
