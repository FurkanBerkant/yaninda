package com.berkant.yaninda.sync

import com.berkant.yaninda.core.time.TimeProvider
import com.berkant.yaninda.data.repository.SyncOutboxRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

sealed interface SyncProcessResult {
    data class Completed(val processedCount: Int) : SyncProcessResult

    data class RetryRequired(val processedCount: Int) : SyncProcessResult

    data class RemoteNotReady(val readiness: RemoteSyncReadiness) : SyncProcessResult
}

class SyncOutboxProcessor(
    private val outboxRepository: SyncOutboxRepository,
    private val remoteDataSource: RemoteSyncDataSource,
    private val timeProvider: TimeProvider,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
) {
    init {
        require(batchSize in 1..MAX_BATCH_SIZE) { "Sync batch size is outside allowed bounds." }
    }

    suspend fun processPending(): SyncProcessResult {
        val readiness = remoteDataSource.readiness.first()
        if (readiness != RemoteSyncReadiness.READY) {
            return SyncProcessResult.RemoteNotReady(readiness)
        }

        val events = outboxRepository.pendingBatch(batchSize)
        var processedCount = 0
        for (event in events) {
            val attemptedAt = timeProvider.now()
            val delivery = try {
                remoteDataSource.deliver(event)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                RemoteSyncDelivery.RETRYABLE_FAILURE
            }
            when (delivery) {
                RemoteSyncDelivery.DELIVERED,
                RemoteSyncDelivery.ALREADY_DELIVERED,
                -> {
                    outboxRepository.markSucceeded(event.id, attemptedAt)
                    processedCount += 1
                }

                RemoteSyncDelivery.RETRYABLE_FAILURE -> {
                    outboxRepository.recordFailedAttempt(event.id, attemptedAt)
                    return SyncProcessResult.RetryRequired(processedCount)
                }
            }
        }

        return if (events.size == batchSize) {
            SyncProcessResult.RetryRequired(processedCount)
        } else {
            SyncProcessResult.Completed(processedCount)
        }
    }

    private companion object {
        const val DEFAULT_BATCH_SIZE = 50
        const val MAX_BATCH_SIZE = 500
    }
}
