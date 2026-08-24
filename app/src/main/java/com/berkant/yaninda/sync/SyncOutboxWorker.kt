package com.berkant.yaninda.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.berkant.yaninda.YanindaApplication
import kotlinx.coroutines.CancellationException

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

                    /*
                     * Şimdilik davranışı değiştirmiyoruz.
                     *
                     * Önce Dede cihazında neden READY
                     * olmadığını logdan göreceğiz.
                     */
                    Log.w(
                        LOG_TAG,
                        "Worker finished without remote delivery. readiness=${result.readiness}"
                    )

                    Result.success()
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