package com.berkant.yaninda.sync

import android.util.Log
import com.berkant.yaninda.core.time.TimeProvider
import com.berkant.yaninda.data.repository.SyncOutboxRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

sealed interface SyncProcessResult {

    data class Completed(
        val processedCount: Int,
    ) : SyncProcessResult

    data class RetryRequired(
        val processedCount: Int,
    ) : SyncProcessResult

    data class RemoteNotReady(
        val readiness: RemoteSyncReadiness,
    ) : SyncProcessResult
}

class SyncOutboxProcessor(
    private val outboxRepository: SyncOutboxRepository,
    private val remoteDataSource: RemoteSyncDataSource,
    private val timeProvider: TimeProvider,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
) {

    init {
        require(
            batchSize in 1..MAX_BATCH_SIZE
        ) {
            "Sync batch size is outside allowed bounds."
        }
    }

    suspend fun processPending(): SyncProcessResult {

        val readiness =
            remoteDataSource
                .readiness
                .first()

        Log.d(
            TAG,
            "Remote readiness=$readiness"
        )

        if (
            readiness !=
            RemoteSyncReadiness.READY
        ) {

            Log.w(
                TAG,
                "Remote sync not ready. readiness=$readiness"
            )

            return SyncProcessResult
                .RemoteNotReady(
                    readiness
                )
        }

        val events =
            outboxRepository
                .pendingBatch(
                    batchSize
                )

        Log.d(
            TAG,
            "Pending outbox event count=${events.size}"
        )

        var processedCount = 0

        for (event in events) {

            val attemptedAt =
                timeProvider.now()

            Log.d(
                TAG,
                buildString {
                    append("Delivering event")
                    append(" id=")
                    append(event.id)
                    append(" type=")
                    append(event.eventType)
                    append(" aggregateId=")
                    append(event.aggregateId)
                    append(" aggregateVersion=")
                    append(event.aggregateVersion)
                }
            )

            val delivery =
                try {

                    remoteDataSource
                        .deliver(event)

                } catch (
                    error: CancellationException
                ) {

                    throw error

                } catch (
                    error: Exception
                ) {

                    Log.e(
                        TAG,
                        "Unexpected exception while delivering event ${event.id}",
                        error,
                    )

                    RemoteSyncDelivery
                        .RETRYABLE_FAILURE
                }

            Log.d(
                TAG,
                "Delivery result event=${event.id} result=$delivery"
            )

            when (delivery) {

                RemoteSyncDelivery.DELIVERED,
                RemoteSyncDelivery.ALREADY_DELIVERED,
                    -> {

                    outboxRepository
                        .markSucceeded(
                            event.id,
                            attemptedAt,
                        )

                    processedCount += 1

                    Log.d(
                        TAG,
                        "Event marked succeeded id=${event.id}"
                    )
                }

                RemoteSyncDelivery.RETRYABLE_FAILURE -> {

                    outboxRepository
                        .recordFailedAttempt(
                            event.id,
                            attemptedAt,
                        )

                    Log.w(
                        TAG,
                        "Event delivery failed and remains pending id=${event.id}"
                    )

                    return SyncProcessResult
                        .RetryRequired(
                            processedCount
                        )
                }
            }
        }

        val result =
            if (
                events.size ==
                batchSize
            ) {

                SyncProcessResult
                    .RetryRequired(
                        processedCount
                    )

            } else {

                SyncProcessResult
                    .Completed(
                        processedCount
                    )
            }

        Log.d(
            TAG,
            "Outbox processing finished. result=$result"
        )

        return result
    }

    private companion object {

        const val DEFAULT_BATCH_SIZE =
            50

        const val MAX_BATCH_SIZE =
            500

        const val TAG =
            "YanindaSync"
    }
}