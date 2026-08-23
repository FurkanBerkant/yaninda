package com.berkant.yaninda.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.berkant.yaninda.domain.sync.SyncEventType
import com.berkant.yaninda.domain.sync.SyncState

@Entity(
    tableName = "sync_outbox",
    indices = [
        Index(value = ["eventType", "aggregateId", "aggregateVersion"], unique = true),
        Index(value = ["syncState", "createdAtEpochMillis"]),
        Index("aggregateId"),
    ],
)
data class SyncOutboxEntity(
    @PrimaryKey val id: String,
    val eventType: SyncEventType,
    val aggregateId: String,
    val aggregateVersion: Long,
    val payloadVersion: Int,
    val createdAtEpochMillis: Long,
    val attemptCount: Int,
    val lastAttemptAtEpochMillis: Long?,
    val syncState: SyncState,
)
