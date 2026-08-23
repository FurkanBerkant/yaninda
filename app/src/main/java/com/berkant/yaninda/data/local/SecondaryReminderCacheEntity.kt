package com.berkant.yaninda.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "secondary_reminder_cache",
    indices = [
        Index(value = ["familyId"]),
        Index(value = ["scheduledAtEpochMillis"]),
    ],
)
data class SecondaryReminderCacheEntity(
    @PrimaryKey
    val occurrenceId: String,
    val familyId: String,
    val scheduledAtEpochMillis: Long,
    val syncedAtEpochMillis: Long,
    val sourceDeviceId: String,
    val version: Long,
)
