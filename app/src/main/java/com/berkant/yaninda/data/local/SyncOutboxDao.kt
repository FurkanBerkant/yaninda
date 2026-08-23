package com.berkant.yaninda.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.berkant.yaninda.domain.sync.SyncState
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncOutboxDao {
    @Insert
    suspend fun insert(event: SyncOutboxEntity)

    @Query(
        "SELECT * FROM sync_outbox WHERE syncState = :pendingState " +
            "ORDER BY createdAtEpochMillis, id LIMIT :limit"
    )
    suspend fun getPending(
        limit: Int,
        pendingState: SyncState = SyncState.PENDING,
    ): List<SyncOutboxEntity>

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE syncState = :pendingState")
    fun observePendingCount(
        pendingState: SyncState = SyncState.PENDING,
    ): Flow<Int>

    @Query("SELECT * FROM sync_outbox WHERE id = :eventId")
    suspend fun getById(eventId: String): SyncOutboxEntity?

    @Query("SELECT * FROM sync_outbox ORDER BY createdAtEpochMillis, id")
    suspend fun getAll(): List<SyncOutboxEntity>

    @Query(
        "UPDATE sync_outbox SET " +
            "attemptCount = attemptCount + 1, " +
            "lastAttemptAtEpochMillis = :attemptedAtEpochMillis, " +
            "syncState = :syncedState " +
            "WHERE id = :eventId AND syncState = :pendingState"
    )
    suspend fun markSucceeded(
        eventId: String,
        attemptedAtEpochMillis: Long,
        pendingState: SyncState = SyncState.PENDING,
        syncedState: SyncState = SyncState.SYNCED,
    ): Int

    @Query(
        "UPDATE sync_outbox SET " +
            "attemptCount = attemptCount + 1, " +
            "lastAttemptAtEpochMillis = :attemptedAtEpochMillis " +
            "WHERE id = :eventId AND syncState = :pendingState"
    )
    suspend fun recordFailedAttempt(
        eventId: String,
        attemptedAtEpochMillis: Long,
        pendingState: SyncState = SyncState.PENDING,
    ): Int
}
