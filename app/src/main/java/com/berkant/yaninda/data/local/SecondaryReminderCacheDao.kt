package com.berkant.yaninda.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SecondaryReminderCacheDao {
    @Query("SELECT * FROM secondary_reminder_cache ORDER BY scheduledAtEpochMillis")
    suspend fun getAll(): List<SecondaryReminderCacheEntity>

    @Query(
        "SELECT * FROM secondary_reminder_cache " +
            "WHERE scheduledAtEpochMillis >= :fromEpochMillis " +
            "ORDER BY scheduledAtEpochMillis"
    )
    suspend fun getUpcoming(fromEpochMillis: Long): List<SecondaryReminderCacheEntity>

    @Query("SELECT * FROM secondary_reminder_cache WHERE occurrenceId = :occurrenceId")
    suspend fun getById(occurrenceId: String): SecondaryReminderCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(occurrences: List<SecondaryReminderCacheEntity>)

    @Query("DELETE FROM secondary_reminder_cache WHERE occurrenceId = :occurrenceId")
    suspend fun deleteById(occurrenceId: String): Int

    @Query("DELETE FROM secondary_reminder_cache")
    suspend fun clear()
}
