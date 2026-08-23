package com.berkant.yaninda.domain.sync

import java.time.Instant

enum class SyncEventType {
    DOSE_OCCURRENCE_SCHEDULED,
    DOSE_OCCURRENCE_DUE,
    DOSE_OCCURRENCE_SNOOZED,
    DOSE_OCCURRENCE_ACKNOWLEDGED,
    DOSE_OCCURRENCE_NO_CONFIRMATION,
    DOSE_OCCURRENCE_CANCELLED,
}

enum class SyncState {
    PENDING,
    SYNCED,
}

data class SyncOutboxEvent(
    val id: String,
    val eventType: SyncEventType,
    val aggregateId: String,
    val aggregateVersion: Long,
    val payloadVersion: Int,
    val createdAt: Instant,
    val attemptCount: Int,
    val lastAttemptAt: Instant?,
    val syncState: SyncState,
)

object SyncEventIdFactory {
    const val VERSION_SEPARATOR = ":v"

    fun create(
        eventType: SyncEventType,
        aggregateId: String,
        aggregateVersion: Long,
    ): String {
        require(aggregateId.isNotBlank()) { "Aggregate ID cannot be blank." }
        require(aggregateVersion > 0) { "Aggregate version must be positive." }
        return "${eventType.name}:$aggregateId$VERSION_SEPARATOR$aggregateVersion"
    }
}
