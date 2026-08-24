package com.berkant.yaninda.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.berkant.yaninda.YanindaApplication
import kotlinx.coroutines.CancellationException

internal enum class SyncWorkerDecision {
    SUCCESS,
    RETRY,
}

internal fun resolveSyncWorkerDecision(
    result: SyncProcessResult,
): SyncWorkerDecision = when (result) {
    is SyncProcessResult.Completed -> SyncWorkerDecision.SUCCESS
    is SyncProcessResult.RetryRequired -> SyncWorkerDecision.RETRY
    is SyncProcessResult.RemoteNotReady -> when (result.readiness) {
        RemoteSyncReadiness.AUTHENTICATION_REQUIRED,
        RemoteSyncReadiness.PAIRING_REQUIRED,
        RemoteSyncReadiness.READY,
        -> SyncWorkerDecision.RETRY

        RemoteSyncReadiness.ALARM_DEVICE_REQUIRED,
        RemoteSyncReadiness.UNAVAILABLE,
        -> SyncWorkerDecision.SUCCESS
    }
}

class SyncOutboxWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(
    appContext,
    workerParameters,
) {

    override suspend fun doWork(): Result {

        val application =
            applicationContext
                    as? YanindaApplication

        if (application == null) {

            Log.e(
                LOG_TAG,
                "YanindaApplication unavailable."
            )

            return Result.failure()
        }

        return try {

            when (
                val result =
                    application
                        .syncOutboxProcessor
                        .processPending()
            ) {

                is SyncProcessResult.Completed -> {

                    Log.d(
                        LOG_TAG,
                        "Worker completed. processed=${result.processedCount}"
                    )

                    Result.success()
                }

                is SyncProcessResult.RemoteNotReady -> {
                    Log.w(
                        LOG_TAG,
                        "Worker finished without remote delivery. readiness=${result.readiness}"
                    )

                    when (resolveSyncWorkerDecision(result)) {
                        SyncWorkerDecision.SUCCESS -> Result.success()
                        SyncWorkerDecision.RETRY -> Result.retry()
                    }
                }

                is SyncProcessResult.RetryRequired -> {

                    Log.w(
                        LOG_TAG,
                        "Worker requests retry. processed=${result.processedCount}"
                    )

                    Result.retry()
                }
            }

        } catch (
            error: CancellationException
        ) {

            throw error

        } catch (
            error: Exception
        ) {

            Log.e(
                LOG_TAG,
                "Outbox sync failed; WorkManager will retry.",
                error,
            )

            Result.retry()
        }
    }

    private companion object {

        const val LOG_TAG =
            "YanindaSync"
    }
}
