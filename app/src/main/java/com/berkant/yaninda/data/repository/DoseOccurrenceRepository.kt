package com.berkant.yaninda.data.repository

import androidx.room.withTransaction
import com.berkant.yaninda.core.time.TimeProvider
import com.berkant.yaninda.data.local.DoseOccurrenceDao
import com.berkant.yaninda.data.local.DoseOccurrenceEntity
import com.berkant.yaninda.data.local.MedicationDao
import com.berkant.yaninda.data.local.SyncOutboxDao
import com.berkant.yaninda.data.local.SyncOutboxEntity
import com.berkant.yaninda.data.local.YanindaDatabase
import com.berkant.yaninda.domain.occurrence.DoseOccurrence
import com.berkant.yaninda.domain.occurrence.DoseOccurrenceEvent
import com.berkant.yaninda.domain.occurrence.DoseOccurrenceStateMachine
import com.berkant.yaninda.domain.occurrence.DoseOccurrenceStatus
import com.berkant.yaninda.domain.occurrence.NextOccurrenceResult
import com.berkant.yaninda.domain.occurrence.OccurrencePlan
import com.berkant.yaninda.domain.occurrence.OccurrencePlanner
import com.berkant.yaninda.domain.occurrence.OccurrencePlanningIssue
import com.berkant.yaninda.domain.occurrence.OccurrencePlanningWindow
import com.berkant.yaninda.domain.sync.SyncEventIdFactory
import com.berkant.yaninda.domain.sync.SyncEventType
import com.berkant.yaninda.domain.sync.SyncState
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class PersistedOccurrencePlan(
    val plannedCount: Int,
    val insertedCount: Int,
    val scheduledOccurrences: List<DoseOccurrence>,
    val pendingAlarms: List<PendingReminderAlarm> = scheduledOccurrences.map { occurrence ->
        PendingReminderAlarm(
            occurrenceId = occurrence.id,
            triggerAt = checkNotNull(occurrence.nextReminderAt),
        )
    },
    val cancelledOccurrenceIds: List<String>,
    val issues: List<OccurrencePlanningIssue>,
    val awaitingResponseOccurrences: List<DoseOccurrence> = emptyList(),
)

data class PendingReminderAlarm(
    val occurrenceId: String,
    val triggerAt: Instant,
)

data class DoseOccurrenceTransition(
    val occurrence: DoseOccurrence,
    val stateChanged: Boolean,
)

interface DoseOccurrenceRepository {
    fun observeActionable(fromInclusive: Instant): Flow<List<DoseOccurrence>>

    suspend fun get(occurrenceId: String): DoseOccurrence?

    suspend fun calculateNextOccurrence(): NextOccurrenceResult

    suspend fun persistPlan(window: OccurrencePlanningWindow): PersistedOccurrencePlan

    suspend fun markReminderDue(
        occurrenceId: String,
        firedAt: Instant,
    ): DoseOccurrenceTransition

    suspend fun applyEvent(
        occurrenceId: String,
        event: DoseOccurrenceEvent,
    ): DoseOccurrenceTransition
}

class RoomDoseOccurrenceRepository(
    private val database: YanindaDatabase,
    private val medicationDao: MedicationDao,
    private val occurrenceDao: DoseOccurrenceDao,
    private val outboxDao: SyncOutboxDao,
    private val planner: OccurrencePlanner,
    private val stateMachine: DoseOccurrenceStateMachine,
    private val timeProvider: TimeProvider,
) : DoseOccurrenceRepository {
    override fun observeActionable(fromInclusive: Instant): Flow<List<DoseOccurrence>> =
        occurrenceDao.observeFrom(
            fromEpochMillis = fromInclusive.toEpochMilli(),
            statuses = ACTIONABLE_STATUSES,
        ).map { records -> records.map(DoseOccurrenceEntity::toDomain) }

    override suspend fun get(occurrenceId: String): DoseOccurrence? =
        occurrenceDao.getById(occurrenceId)?.toDomain()

    override suspend fun calculateNextOccurrence(): NextOccurrenceResult = database.withTransaction {
        val configurations = medicationDao.getActiveConfigurations().map { it.toDomain() }
        planner.nextOccurrence(
            configurations = configurations,
            atOrAfter = timeProvider.now(),
            zoneId = timeProvider.currentZoneId(),
        )
    }

    override suspend fun persistPlan(
        window: OccurrencePlanningWindow,
    ): PersistedOccurrencePlan = database.withTransaction {
        val configurations = medicationDao.getActiveConfigurations().map { it.toDomain() }
        val plan = planner.plan(configurations, window)
        val createdAt = timeProvider.now()
        val entities = plan.toEntities(createdAt)
        val plannedIds = entities.mapTo(mutableSetOf()) { it.id }
        val pendingOccurrences = occurrenceDao.getByStatuses(PENDING_ALARM_STATUSES)
        val obsoleteOccurrences = pendingOccurrences.filter { entity ->
            when (entity.status) {
                DoseOccurrenceStatus.SCHEDULED -> entity.id !in plannedIds
                DoseOccurrenceStatus.SNOOZED -> !isStillConfigured(
                    occurrence = entity.toDomain(),
                    configurations = configurations,
                )

                else -> false
            }
        }

        obsoleteOccurrences.forEach { entity ->
            val cancelled = stateMachine.transition(
                current = entity.toDomain(),
                event = DoseOccurrenceEvent.Cancelled(createdAt),
            )
            check(occurrenceDao.update(cancelled.toEntity()) == 1) {
                "An obsolete dose occurrence could not be cancelled."
            }
            enqueueSyncProjection(cancelled)
        }
        val insertResults = if (entities.isEmpty()) {
            emptyList()
        } else {
            occurrenceDao.insertIfAbsent(entities)
        }
        entities.zip(insertResults).forEach { (entity, insertResult) ->
            if (insertResult != INSERT_IGNORED) {
                enqueueSyncProjection(entity.toDomain())
            }
        }
        val scheduledOccurrences = if (plannedIds.isEmpty()) {
            emptyList()
        } else {
            occurrenceDao.getByIds(plannedIds)
                .asSequence()
                .filter { it.status == DoseOccurrenceStatus.SCHEDULED }
                .map(DoseOccurrenceEntity::toDomain)
                .sortedBy { it.scheduledAt }
                .toList()
        }
        val snoozedOccurrences = occurrenceDao
            .getByStatus(DoseOccurrenceStatus.SNOOZED)
            .map(DoseOccurrenceEntity::toDomain)
        val awaitingResponseOccurrences = occurrenceDao
            .getByStatus(DoseOccurrenceStatus.DUE)
            .map(DoseOccurrenceEntity::toDomain)
        val pendingAlarms = (scheduledOccurrences + snoozedOccurrences)
            .map { occurrence ->
                PendingReminderAlarm(
                    occurrenceId = occurrence.id,
                    triggerAt = checkNotNull(occurrence.nextReminderAt) {
                        "A pending dose occurrence has no reminder time."
                    },
                )
            }
            .sortedBy(PendingReminderAlarm::triggerAt)
        PersistedOccurrencePlan(
            plannedCount = entities.size,
            insertedCount = insertResults.count { it != INSERT_IGNORED },
            scheduledOccurrences = scheduledOccurrences,
            pendingAlarms = pendingAlarms,
            cancelledOccurrenceIds = obsoleteOccurrences.map { it.id },
            issues = plan.issues,
            awaitingResponseOccurrences = awaitingResponseOccurrences,
        )
    }

    override suspend fun applyEvent(
        occurrenceId: String,
        event: DoseOccurrenceEvent,
    ): DoseOccurrenceTransition = database.withTransaction {
        transition(occurrenceId, event)
    }

    override suspend fun markReminderDue(
        occurrenceId: String,
        firedAt: Instant,
    ): DoseOccurrenceTransition = database.withTransaction {
        val currentEntity = occurrenceDao.getById(occurrenceId)
            ?: error("The dose occurrence does not exist.")
        val current = currentEntity.toDomain()
        val event = if (
            current.status in PENDING_ALARM_STATUSES &&
            !isStillConfigured(
                occurrence = current,
                configurations = medicationDao.getActiveConfigurations().map { it.toDomain() },
            )
        ) {
            DoseOccurrenceEvent.Cancelled(firedAt)
        } else {
            DoseOccurrenceEvent.ReminderDue(firedAt)
        }
        transition(currentEntity, current, event)
    }

    private fun isStillConfigured(
        occurrence: DoseOccurrence,
        configurations: List<com.berkant.yaninda.domain.medication.MedicationConfiguration>,
    ): Boolean {
        val validationWindow = OccurrencePlanningWindow(
            startInclusive = occurrence.scheduledAt,
            endExclusive = occurrence.scheduledAt.plusNanos(1),
            zoneId = timeProvider.currentZoneId(),
        )
        return planner.plan(configurations, validationWindow).occurrences.any { planned ->
            planned.id == occurrence.id &&
                planned.medicationId == occurrence.medicationId &&
                planned.scheduleId == occurrence.scheduleId &&
                planned.scheduledAt == occurrence.scheduledAt
        }
    }

    private suspend fun transition(
        occurrenceId: String,
        event: DoseOccurrenceEvent,
    ): DoseOccurrenceTransition {
        val currentEntity = occurrenceDao.getById(occurrenceId)
            ?: error("The dose occurrence does not exist.")
        return transition(currentEntity, currentEntity.toDomain(), event)
    }

    private suspend fun transition(
        currentEntity: DoseOccurrenceEntity,
        current: DoseOccurrence,
        event: DoseOccurrenceEvent,
    ): DoseOccurrenceTransition {
        val updated = stateMachine.transition(current, event)
        if (updated != current) {
            check(occurrenceDao.update(updated.toEntity()) == 1) {
                "The dose occurrence state could not be persisted."
            }
            enqueueSyncProjection(updated)
        }
        return DoseOccurrenceTransition(
            occurrence = updated,
            stateChanged = updated != current,
        )
    }

    private suspend fun enqueueSyncProjection(occurrence: DoseOccurrence) {
        val eventType = occurrence.status.toSyncEventType()
        outboxDao.insert(
            SyncOutboxEntity(
                id = SyncEventIdFactory.create(
                    eventType = eventType,
                    aggregateId = occurrence.id,
                    aggregateVersion = occurrence.version,
                ),
                eventType = eventType,
                aggregateId = occurrence.id,
                aggregateVersion = occurrence.version,
                payloadVersion = OCCURRENCE_PAYLOAD_VERSION,
                createdAtEpochMillis = occurrence.updatedAt.toEpochMilli(),
                attemptCount = 0,
                lastAttemptAtEpochMillis = null,
                syncState = SyncState.PENDING,
            )
        )
    }

    private fun DoseOccurrenceStatus.toSyncEventType(): SyncEventType = when (this) {
        DoseOccurrenceStatus.SCHEDULED -> SyncEventType.DOSE_OCCURRENCE_SCHEDULED
        DoseOccurrenceStatus.DUE -> SyncEventType.DOSE_OCCURRENCE_DUE
        DoseOccurrenceStatus.SNOOZED -> SyncEventType.DOSE_OCCURRENCE_SNOOZED
        DoseOccurrenceStatus.ACKNOWLEDGED_TAKEN ->
            SyncEventType.DOSE_OCCURRENCE_ACKNOWLEDGED

        DoseOccurrenceStatus.NO_CONFIRMATION ->
            SyncEventType.DOSE_OCCURRENCE_NO_CONFIRMATION

        DoseOccurrenceStatus.CANCELLED -> SyncEventType.DOSE_OCCURRENCE_CANCELLED
    }

    private fun OccurrencePlan.toEntities(createdAt: Instant): List<DoseOccurrenceEntity> =
        occurrences.map { occurrence ->
            DoseOccurrenceEntity(
                id = occurrence.id,
                medicationId = occurrence.medicationId,
                scheduleId = occurrence.scheduleId,
                scheduledAtEpochMillis = occurrence.scheduledAt.toEpochMilli(),
                status = DoseOccurrenceStatus.SCHEDULED,
                acknowledgedAtEpochMillis = null,
                acknowledgementActor = null,
                snoozeCount = 0,
                lastAlertedAtEpochMillis = null,
                nextReminderAtEpochMillis = occurrence.scheduledAt.toEpochMilli(),
                createdAtEpochMillis = createdAt.toEpochMilli(),
                updatedAtEpochMillis = createdAt.toEpochMilli(),
                version = 1L,
            )
        }

    companion object {
        private const val INSERT_IGNORED = -1L
        private const val OCCURRENCE_PAYLOAD_VERSION = 1
        private val ACTIONABLE_STATUSES = setOf(
            DoseOccurrenceStatus.SCHEDULED,
            DoseOccurrenceStatus.DUE,
            DoseOccurrenceStatus.SNOOZED,
        )
        private val PENDING_ALARM_STATUSES = setOf(
            DoseOccurrenceStatus.SCHEDULED,
            DoseOccurrenceStatus.SNOOZED,
        )
    }
}

internal fun DoseOccurrenceEntity.toDomain(): DoseOccurrence = DoseOccurrence(
    id = id,
    medicationId = medicationId,
    scheduleId = scheduleId,
    scheduledAt = Instant.ofEpochMilli(scheduledAtEpochMillis),
    status = status,
    acknowledgedAt = acknowledgedAtEpochMillis?.let(Instant::ofEpochMilli),
    acknowledgementActor = acknowledgementActor,
    snoozeCount = snoozeCount,
    lastAlertedAt = lastAlertedAtEpochMillis?.let(Instant::ofEpochMilli),
    nextReminderAt = nextReminderAtEpochMillis?.let(Instant::ofEpochMilli),
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    version = version,
)

internal fun DoseOccurrence.toEntity(): DoseOccurrenceEntity = DoseOccurrenceEntity(
    id = id,
    medicationId = medicationId,
    scheduleId = scheduleId,
    scheduledAtEpochMillis = scheduledAt.toEpochMilli(),
    status = status,
    acknowledgedAtEpochMillis = acknowledgedAt?.toEpochMilli(),
    acknowledgementActor = acknowledgementActor,
    snoozeCount = snoozeCount,
    lastAlertedAtEpochMillis = lastAlertedAt?.toEpochMilli(),
    nextReminderAtEpochMillis = nextReminderAt?.toEpochMilli(),
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
    version = version,
)
