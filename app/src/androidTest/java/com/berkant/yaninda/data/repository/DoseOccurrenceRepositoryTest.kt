
package com.berkant.yaninda.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.berkant.yaninda.core.time.TimeProvider
import com.berkant.yaninda.data.local.MedicationEntity
import com.berkant.yaninda.data.local.MedicationScheduleEntity
import com.berkant.yaninda.data.local.YanindaDatabase
import com.berkant.yaninda.domain.medication.MedicationScheduleType
import com.berkant.yaninda.domain.occurrence.AcknowledgementActor
import com.berkant.yaninda.domain.occurrence.DoseOccurrenceEvent
import com.berkant.yaninda.domain.occurrence.DoseOccurrenceStateMachine
import com.berkant.yaninda.domain.occurrence.DoseOccurrenceStatus
import com.berkant.yaninda.domain.occurrence.OccurrencePlanner
import com.berkant.yaninda.domain.occurrence.OccurrencePlanningWindow
import com.berkant.yaninda.domain.sync.SyncEventIdFactory
import com.berkant.yaninda.domain.sync.SyncEventType
import com.berkant.yaninda.domain.sync.SyncState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DoseOccurrenceRepositoryTest {
    private lateinit var database: YanindaDatabase
    private lateinit var repository: RoomDoseOccurrenceRepository
    private val timeProvider = FixedTimeProvider(
        instant = Instant.parse("2026-08-21T12:00:00Z"),
        zoneId = ZoneId.of("Europe/Istanbul"),
    )

    @Before
    fun createDatabase() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, YanindaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        database.medicationDao().replaceConfiguration(
            medication = MedicationEntity(
                id = "medication-1",
                displayName = "Test ilacı",
                dosageText = "Yazılı doz",
                instructionText = "Yazılı talimat",
                photoUri = null,
                scheduleType = MedicationScheduleType.FIXED_ONLY,
                active = true,
                createdAtEpochMillis = 100L,
                updatedAtEpochMillis = 100L,
                version = 1L,
            ),
            schedules = listOf(
                MedicationScheduleEntity(
                    id = "schedule-1",
                    medicationId = "medication-1",
                    localTimeMinutes = 20 * 60,
                    daysOfWeekMask = 127,
                    validFromEpochDay = LocalDate.of(2026, 8, 21).toEpochDay(),
                    validUntilEpochDay = null,
                    snoozeEnabled = true,
                    snoozeMinutes = 10,
                    maxSnoozes = 1,
                    createdAtEpochMillis = 100L,
                    updatedAtEpochMillis = 100L,
                    version = 1L,
                )
            ),
        )
        repository = RoomDoseOccurrenceRepository(
            database = database,
            medicationDao = database.medicationDao(),
            occurrenceDao = database.doseOccurrenceDao(),
            outboxDao = database.syncOutboxDao(),
            planner = OccurrencePlanner(),
            stateMachine = DoseOccurrenceStateMachine(),
            timeProvider = timeProvider,
        )
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun persistPlan_isIdempotentAndDoesNotOverwriteAcknowledgement() = runBlocking {
        val window = OccurrencePlanningWindow(
            startInclusive = Instant.parse("2026-08-21T00:00:00Z"),
            endExclusive = Instant.parse("2026-08-22T00:00:00Z"),
            zoneId = timeProvider.currentZoneId(),
        )

        val first = repository.persistPlan(window)
        val second = repository.persistPlan(window)
        val occurrence = database.doseOccurrenceDao().getAll().single()

        assertEquals(1, first.plannedCount)
        assertEquals(1, first.insertedCount)
        assertEquals(0, second.insertedCount)

        repository.applyEvent(
            occurrence.id,
            DoseOccurrenceEvent.ReminderDue(Instant.parse("2026-08-21T17:00:00Z")),
        )
        repository.applyEvent(
            occurrence.id,
            DoseOccurrenceEvent.TakenAcknowledged(
                occurredAt = Instant.parse("2026-08-21T17:01:00Z"),
                actor = AcknowledgementActor.GRANDFATHER,
            ),
        )
        repository.applyEvent(
            occurrence.id,
            DoseOccurrenceEvent.TakenAcknowledged(
                occurredAt = Instant.parse("2026-08-21T17:02:00Z"),
                actor = AcknowledgementActor.GRANDFATHER,
            ),
        )
        val third = repository.persistPlan(window)
        val persisted = database.doseOccurrenceDao().getAll().single()
        val outboxEvents = database.syncOutboxDao().getAll()
        val outboxEvent = outboxEvents.single {
            it.eventType == SyncEventType.DOSE_OCCURRENCE_ACKNOWLEDGED
        }

        assertEquals(0, third.insertedCount)
        assertEquals(DoseOccurrenceStatus.ACKNOWLEDGED_TAKEN, persisted.status)
        assertEquals(3L, persisted.version)
        assertEquals(
            SyncEventIdFactory.create(
                SyncEventType.DOSE_OCCURRENCE_ACKNOWLEDGED,
                occurrence.id,
                persisted.version,
            ),
            outboxEvent.id,
        )
        assertEquals(occurrence.id, outboxEvent.aggregateId)
        assertEquals(SyncState.PENDING, outboxEvent.syncState)
        assertEquals(0, outboxEvent.attemptCount)
        assertEquals(
            listOf(
                SyncEventType.DOSE_OCCURRENCE_SCHEDULED,
                SyncEventType.DOSE_OCCURRENCE_DUE,
                SyncEventType.DOSE_OCCURRENCE_ACKNOWLEDGED,
            ),
            outboxEvents.map { it.eventType },
        )
    }

    @Test
    fun calculateNextOccurrence_usesInjectedCurrentTimeAndZone() = runBlocking {
        val result = repository.calculateNextOccurrence()

        assertEquals(
            Instant.parse("2026-08-21T17:00:00Z"),
            result.occurrence?.scheduledAt,
        )
    }

    @Test
    fun persistPlan_cancelsFutureOccurrencesMadeObsoleteByScheduleEdit() = runBlocking {
        val window = OccurrencePlanningWindow(
            startInclusive = Instant.parse("2026-08-21T12:00:00Z"),
            endExclusive = Instant.parse("2026-08-23T12:00:00Z"),
            zoneId = timeProvider.currentZoneId(),
        )
        val initialPlan = repository.persistPlan(window)
        val oldIds = initialPlan.scheduledOccurrences.mapTo(mutableSetOf()) { it.id }
        val existing = database.medicationDao().getConfiguration("medication-1")!!
        database.medicationDao().replaceConfiguration(
            medication = existing.medication.copy(
                updatedAtEpochMillis = 200L,
                version = 2L,
            ),
            schedules = existing.schedules.map { schedule ->
                schedule.copy(
                    localTimeMinutes = 21 * 60,
                    updatedAtEpochMillis = 200L,
                    version = 2L,
                )
            },
        )

        val updatedPlan = repository.persistPlan(window)
        val persisted = database.doseOccurrenceDao().getAll()

        assertEquals(oldIds, updatedPlan.cancelledOccurrenceIds.toSet())
        assertTrue(
            persisted.filter { it.id in oldIds }
                .all { it.status == DoseOccurrenceStatus.CANCELLED }
        )
        assertTrue(updatedPlan.scheduledOccurrences.isNotEmpty())
        assertTrue(updatedPlan.scheduledOccurrences.none { it.id in oldIds })
    }

    @Test
    fun markReminderDue_cancelsOccurrenceIfItsScheduleChangedBeforeDelivery() = runBlocking {
        val window = OccurrencePlanningWindow(
            startInclusive = Instant.parse("2026-08-21T12:00:00Z"),
            endExclusive = Instant.parse("2026-08-22T12:00:00Z"),
            zoneId = timeProvider.currentZoneId(),
        )
        val occurrence = repository.persistPlan(window).scheduledOccurrences.single()
        val existing = database.medicationDao().getConfiguration("medication-1")!!
        database.medicationDao().replaceConfiguration(
            medication = existing.medication.copy(
                updatedAtEpochMillis = 200L,
                version = 2L,
            ),
            schedules = existing.schedules.map { schedule ->
                schedule.copy(
                    localTimeMinutes = 21 * 60,
                    updatedAtEpochMillis = 200L,
                    version = 2L,
                )
            },
        )

        val transition = repository.markReminderDue(
            occurrenceId = occurrence.id,
            firedAt = occurrence.scheduledAt,
        )

        assertTrue(transition.stateChanged)
        assertEquals(DoseOccurrenceStatus.CANCELLED, transition.occurrence.status)
        assertEquals(
            DoseOccurrenceStatus.CANCELLED,
            database.doseOccurrenceDao().getById(occurrence.id)?.status,
        )
    }

    @Test
    fun persistPlan_keepsSnoozedOccurrenceAndItsPersistedReminderTime() = runBlocking {
        val window = OccurrencePlanningWindow(
            startInclusive = Instant.parse("2026-08-21T12:00:00Z"),
            endExclusive = Instant.parse("2026-08-22T12:00:00Z"),
            zoneId = timeProvider.currentZoneId(),
        )
        val occurrence = repository.persistPlan(window).scheduledOccurrences.single()
        repository.markReminderDue(
            occurrenceId = occurrence.id,
            firedAt = occurrence.scheduledAt,
        )
        val remindAt = occurrence.scheduledAt.plusSeconds(600)
        repository.applyEvent(
            occurrenceId = occurrence.id,
            event = DoseOccurrenceEvent.SnoozeRequested(
                occurredAt = occurrence.scheduledAt.plusSeconds(15),
                remindAt = remindAt,
                maxSnoozes = 1,
            ),
        )

        val refreshed = repository.persistPlan(window)

        assertTrue(refreshed.cancelledOccurrenceIds.isEmpty())
        assertEquals(1, refreshed.pendingAlarms.size)
        assertEquals(occurrence.id, refreshed.pendingAlarms.single().occurrenceId)
        assertEquals(remindAt, refreshed.pendingAlarms.single().triggerAt)
    }
}

private data class FixedTimeProvider(
    private val instant: Instant,
    private val zoneId: ZoneId,
) : TimeProvider {
    override fun now(): Instant = instant

    override fun currentZoneId(): ZoneId = zoneId
}
