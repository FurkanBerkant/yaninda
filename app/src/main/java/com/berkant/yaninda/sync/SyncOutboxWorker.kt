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
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val application = applicationContext as? YanindaApplication ?: return Result.failure()
        return try {
            when (application.syncOutboxProcessor.processPending()) {
                is SyncProcessResult.Completed,
                is SyncProcessResult.RemoteNotReady,
                -> Result.success()

                is SyncProcessResult.RetryRequired -> Result.retry()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            Log.e(LOG_TAG, "Outbox sync failed; WorkManager will retry.")
            Result.retry()
        }
    }

    private companion object {
        const val LOG_TAG = "YanindaSync"
    }
}
