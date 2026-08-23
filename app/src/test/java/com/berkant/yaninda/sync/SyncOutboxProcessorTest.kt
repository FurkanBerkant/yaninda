package com.berkant.yaninda.sync

import com.berkant.yaninda.core.time.TimeProvider
import com.berkant.yaninda.data.repository.SyncOutboxRepository
import com.berkant.yaninda.domain.sync.SyncEventIdFactory
import com.berkant.yaninda.domain.sync.SyncEventType
import com.berkant.yaninda.domain.sync.SyncOutboxEvent
import com.berkant.yaninda.domain.sync.SyncState
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncOutboxProcessorTest {
    private val now = Instant.parse("2026-08-21T12:00:00Z")
    private val timeProvider = FixedSyncTimeProvider(now)

    @Test
    fun processPending_keepsEventsPendingWhenRemoteIsNotConfigured() = runBlocking {
        val repository = FakeSyncOutboxRepository(listOf(pendingEvent()))
        val processor = SyncOutboxProcessor(
            outboxRepository = repository,
            remoteDataSource = UnavailableRemoteSyncDataSource,
            timeProvider = timeProvider,
        )

        val result = processor.processPending()

        assertEquals(
            SyncProcessResult.RemoteNotReady(RemoteSyncReadiness.UNAVAILABLE),
            result,
        )
        assertEquals(SyncState.PENDING, repository.singleEvent().syncState)
        assertEquals(0, repository.singleEvent().attemptCount)
    }

    @Test
    fun processPending_recordsRetryAndLaterCompletesWithoutDuplicatingDelivery() = runBlocking {
        val event = pendingEvent()
        val repository = FakeSyncOutboxRepository(listOf(event))
        val remote = FakeRemoteSyncDataSource(retryableFailuresBeforeSuccess = 1)
        val processor = SyncOutboxProcessor(
            outboxRepository = repository,
            remoteDataSource = remote,
            timeProvider = timeProvider,
        )

        val firstResult = processor.processPending()

        assertEquals(SyncProcessResult.RetryRequired(processedCount = 0), firstResult)
        assertEquals(SyncState.PENDING, repository.singleEvent().syncState)
        assertEquals(1, repository.singleEvent().attemptCount)

        val secondResult = processor.processPending()

        assertEquals(SyncProcessResult.Completed(processedCount = 1), secondResult)
        assertEquals(SyncState.SYNCED, repository.singleEvent().syncState)
        assertEquals(2, repository.singleEvent().attemptCount)
        assertEquals(listOf(event.id), remote.deliveredEvents().map { it.id })
        assertEquals(2, remote.attemptCount(event.id))
    }

    @Test
    fun fakeRemote_returnsAlreadyDeliveredForTheSameEventId() = runBlocking {
        val event = pendingEvent()
        val remote = FakeRemoteSyncDataSource()

        assertEquals(RemoteSyncDelivery.DELIVERED, remote.deliver(event))
        assertEquals(RemoteSyncDelivery.ALREADY_DELIVERED, remote.deliver(event))
        assertEquals(1, remote.deliveredEvents().size)
    }

    private fun pendingEvent(): SyncOutboxEvent = SyncOutboxEvent(
        id = SyncEventIdFactory.create(
            SyncEventType.DOSE_OCCURRENCE_ACKNOWLEDGED,
            "occurrence-1",
            3L,
        ),
        eventType = SyncEventType.DOSE_OCCURRENCE_ACKNOWLEDGED,
        aggregateId = "occurrence-1",
        aggregateVersion = 3L,
        payloadVersion = 1,
        createdAt = now.minusSeconds(60),
        attemptCount = 0,
        lastAttemptAt = null,
        syncState = SyncState.PENDING,
    )
}

private class FakeSyncOutboxRepository(
    initialEvents: List<SyncOutboxEvent>,
) : SyncOutboxRepository {
    private val events = initialEvents.associateByTo(linkedMapOf()) { it.id }
    private val mutablePendingCount = MutableStateFlow(
        events.values.count { it.syncState == SyncState.PENDING }
    )

    override val pendingCount: Flow<Int> = mutablePendingCount

    override suspend fun pendingBatch(limit: Int): List<SyncOutboxEvent> = events.values
        .filter { it.syncState == SyncState.PENDING }
        .sortedBy { it.createdAt }
        .take(limit)

    override suspend fun markSucceeded(eventId: String, attemptedAt: Instant) {
        val event = checkNotNull(events[eventId])
        events[eventId] = event.copy(
            attemptCount = event.attemptCount + 1,
            lastAttemptAt = attemptedAt,
            syncState = SyncState.SYNCED,
        )
        refreshPendingCount()
    }

    override suspend fun recordFailedAttempt(eventId: String, attemptedAt: Instant) {
        val event = checkNotNull(events[eventId])
        events[eventId] = event.copy(
            attemptCount = event.attemptCount + 1,
            lastAttemptAt = attemptedAt,
        )
    }

    fun singleEvent(): SyncOutboxEvent {
        assertTrue(events.isNotEmpty())
        return events.values.single()
    }

    private fun refreshPendingCount() {
        mutablePendingCount.value = events.values.count { it.syncState == SyncState.PENDING }
    }
}

private data class FixedSyncTimeProvider(
    private val instant: Instant,
) : TimeProvider {
    override fun now(): Instant = instant

    override fun currentZoneId(): ZoneId = ZoneId.of("Europe/Istanbul")
}
