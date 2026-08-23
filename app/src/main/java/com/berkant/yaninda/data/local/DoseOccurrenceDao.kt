package com.berkant.yaninda.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.berkant.yaninda.domain.occurrence.DoseOccurrenceStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DoseOccurrenceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(occurrences: List<DoseOccurrenceEntity>): List<Long>

    @Query("SELECT * FROM dose_occurrences WHERE id = :occurrenceId")
    suspend fun getById(occurrenceId: String): DoseOccurrenceEntity?

    @Query(
        "SELECT * FROM dose_occurrences WHERE status = :status " +
            "ORDER BY scheduledAtEpochMillis, id"
    )
    suspend fun getByStatus(status: DoseOccurrenceStatus): List<DoseOccurrenceEntity>

    @Query(
        "SELECT * FROM dose_occurrences WHERE status IN (:statuses) " +
            "ORDER BY nextReminderAtEpochMillis, id"
    )
    suspend fun getByStatuses(statuses: Set<DoseOccurrenceStatus>): List<DoseOccurrenceEntity>

    @Query("SELECT * FROM dose_occurrences WHERE id IN (:occurrenceIds)")
    suspend fun getByIds(occurrenceIds: Set<String>): List<DoseOccurrenceEntity>

    @Query(
        "SELECT * FROM dose_occurrences " +
            "WHERE scheduledAtEpochMillis >= :fromEpochMillis AND status IN (:statuses) " +
            "ORDER BY scheduledAtEpochMillis, id"
    )
    fun observeFrom(
        fromEpochMillis: Long,
        statuses: Set<DoseOccurrenceStatus>,
    ): Flow<List<DoseOccurrenceEntity>>

    @Query("SELECT * FROM dose_occurrences ORDER BY scheduledAtEpochMillis, id")
    suspend fun getAll(): List<DoseOccurrenceEntity>

    @Update
    suspend fun update(occurrence: DoseOccurrenceEntity): Int
}
