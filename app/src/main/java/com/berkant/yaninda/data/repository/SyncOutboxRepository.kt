
package com.berkant.yaninda.data.repository

import com.berkant.yaninda.data.local.SyncOutboxDao
import com.berkant.yaninda.data.local.SyncOutboxEntity
import com.berkant.yaninda.domain.sync.SyncOutboxEvent
import com.berkant.yaninda.domain.sync.SyncState
import java.time.Instant
import kotlinx.coroutines.flow.Flow

interface SyncOutboxRepository {
    val pendingCount: Flow<Int>

    suspend fun pendingBatch(limit: Int): List<SyncOutboxEvent>

    suspend fun markSucceeded(eventId: String, attemptedAt: Instant)

    suspend fun recordFailedAttempt(eventId: String, attemptedAt: Instant)
}

class RoomSyncOutboxRepository(
    private val outboxDao: SyncOutboxDao,
) : SyncOutboxRepository {
    override val pendingCount: Flow<Int> = outboxDao.observePendingCount()

    override suspend fun pendingBatch(limit: Int): List<SyncOutboxEvent> {
        require(limit in 1..MAX_BATCH_SIZE) { "Outbox batch size is outside allowed bounds." }
        return outboxDao.getPending(limit).map(SyncOutboxEntity::toDomain)
    }

    override suspend fun markSucceeded(eventId: String, attemptedAt: Instant) {
        require(eventId.isNotBlank()) { "Event ID cannot be blank." }
        val updatedRows = outboxDao.markSucceeded(eventId, attemptedAt.toEpochMilli())
        check(updatedRows == 1 || outboxDao.getById(eventId)?.syncState == SyncState.SYNCED) {
            "The outbox event could not be marked as synchronized."
        }
    }

    override suspend fun recordFailedAttempt(eventId: String, attemptedAt: Instant) {
        require(eventId.isNotBlank()) { "Event ID cannot be blank." }
        val updatedRows = outboxDao.recordFailedAttempt(eventId, attemptedAt.toEpochMilli())
        check(updatedRows == 1 || outboxDao.getById(eventId)?.syncState == SyncState.SYNCED) {
            "The failed outbox attempt could not be recorded."
        }
    }

    private companion object {
        const val MAX_BATCH_SIZE = 500
    }
}

internal fun SyncOutboxEntity.toDomain(): SyncOutboxEvent = SyncOutboxEvent(
    id = id,
    eventType = eventType,
    aggregateId = aggregateId,
    aggregateVersion = aggregateVersion,
    payloadVersion = payloadVersion,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    attemptCount = attemptCount,
    lastAttemptAt = lastAttemptAtEpochMillis?.let(Instant::ofEpochMilli),
    syncState = syncState,
)
