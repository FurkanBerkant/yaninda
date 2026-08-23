package com.berkant.yaninda.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

enum class SyncWorkRequestResult {
    ENQUEUED,
    PLATFORM_FAILURE,
}

fun interface SyncWorkScheduler {
    fun requestSync(): SyncWorkRequestResult
}

class WorkManagerSyncWorkScheduler(
    context: Context,
) : SyncWorkScheduler {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun requestSync(): SyncWorkRequestResult = try {
        val request = OneTimeWorkRequestBuilder<SyncOutboxWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                INITIAL_BACKOFF_SECONDS,
                TimeUnit.SECONDS,
            )
            .addTag(WORK_TAG)
            .build()
        workManager.enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            // Do not drop an acknowledgement queued while another sync worker is finishing.
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
        SyncWorkRequestResult.ENQUEUED
    } catch (_: RuntimeException) {
        Log.e(LOG_TAG, "Outbox sync work could not be enqueued.")
        SyncWorkRequestResult.PLATFORM_FAILURE
    }

    companion object {
        const val UNIQUE_WORK_NAME = "yaninda-local-outbox-sync"
        const val WORK_TAG = "yaninda-sync"
        private const val INITIAL_BACKOFF_SECONDS = 30L
        private const val LOG_TAG = "YanindaSync"
    }
}
