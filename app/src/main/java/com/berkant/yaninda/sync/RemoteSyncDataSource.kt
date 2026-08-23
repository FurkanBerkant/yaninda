package com.berkant.yaninda.sync

import com.berkant.yaninda.domain.sync.SyncOutboxEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class RemoteSyncDelivery {
    DELIVERED,
    ALREADY_DELIVERED,
    RETRYABLE_FAILURE,
}

enum class RemoteSyncReadiness {
    UNAVAILABLE,
    AUTHENTICATION_REQUIRED,
    PAIRING_REQUIRED,
    PRIMARY_DEVICE_REQUIRED,
    READY,
}

interface RemoteSyncDataSource {
    val readiness: Flow<RemoteSyncReadiness>

    suspend fun deliver(event: SyncOutboxEvent): RemoteSyncDelivery
}

object UnavailableRemoteSyncDataSource : RemoteSyncDataSource {
    override val readiness: Flow<RemoteSyncReadiness> = flowOf(RemoteSyncReadiness.UNAVAILABLE)

    override suspend fun deliver(event: SyncOutboxEvent): RemoteSyncDelivery =
        error("Remote synchronization is not configured yet.")
}

class FakeRemoteSyncDataSource(
    private val retryableFailuresBeforeSuccess: Int = 0,
) : RemoteSyncDataSource {
    private val mutex = Mutex()
    private val deliveredById = linkedMapOf<String, SyncOutboxEvent>()
    private val attemptsById = mutableMapOf<String, Int>()

    init {
        require(retryableFailuresBeforeSuccess >= 0) {
            "Retryable failure count cannot be negative."
        }
    }

    override val readiness: Flow<RemoteSyncReadiness> = flowOf(RemoteSyncReadiness.READY)

    override suspend fun deliver(event: SyncOutboxEvent): RemoteSyncDelivery = mutex.withLock {
        if (event.id in deliveredById) return@withLock RemoteSyncDelivery.ALREADY_DELIVERED

        val attempt = attemptsById.getOrDefault(event.id, 0) + 1
        attemptsById[event.id] = attempt
        if (attempt <= retryableFailuresBeforeSuccess) {
            return@withLock RemoteSyncDelivery.RETRYABLE_FAILURE
        }

        deliveredById[event.id] = event
        RemoteSyncDelivery.DELIVERED
    }

    suspend fun deliveredEvents(): List<SyncOutboxEvent> = mutex.withLock {
        deliveredById.values.toList()
    }

    suspend fun attemptCount(eventId: String): Int = mutex.withLock {
        attemptsById.getOrDefault(eventId, 0)
    }
}
