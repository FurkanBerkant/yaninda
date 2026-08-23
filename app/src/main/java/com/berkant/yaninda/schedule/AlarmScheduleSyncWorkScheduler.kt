package com.berkant.yaninda.schedule

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class AlarmScheduleSyncWorkScheduler(
    context: Context,
) {

    private val workManager =
        WorkManager.getInstance(
            context.applicationContext
        )

    /*
     * FCM hint, app startup veya başka bir
     * opportunistic trigger bunu çağırabilir.
     */
    fun requestImmediateSync() {
        try {
            val request =
                OneTimeWorkRequestBuilder<
                        AlarmScheduleSyncWorker
                        >()
                    .setConstraints(
                        networkConstraints()
                    )
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        INITIAL_BACKOFF_SECONDS,
                        TimeUnit.SECONDS,
                    )
                    .addTag(WORK_TAG)
                    .build()

            workManager.enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,

                /*
                 * Worker zaten çalışıyorsa yeni
                 * tetiklemeyi tamamen kaybetme.
                 *
                 * Mevcut işin arkasından yeni bir
                 * kontrol daha yapılabilir.
                 */
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request,
            )

        } catch (
            error: RuntimeException
        ) {
            Log.e(
                LOG_TAG,
                "Immediate schedule sync could not be enqueued.",
                error,
            )
        }
    }

    /*
     * FCM hiçbir zaman gelmese bile schedule'ın
     * sonunda kontrol edilmesini sağlayan
     * safety-net.
     *
     * Android WorkManager'ın minimum periodic
     * interval'ı 15 dakikadır.
     *
     * Bu kesin zamanlı çalışma değildir ve
     * olması da gerekmiyor; ilaç alarmını
     * AlarmManager yönetiyor.
     */
    fun ensurePeriodicSync() {
        try {
            val request =
                PeriodicWorkRequestBuilder<
                        AlarmScheduleSyncWorker
                        >(
                    PERIODIC_INTERVAL_MINUTES,
                    TimeUnit.MINUTES,
                )
                    .setConstraints(
                        networkConstraints()
                    )
                    .setInitialDelay(
                        PERIODIC_INTERVAL_MINUTES,
                        TimeUnit.MINUTES,
                    )
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        INITIAL_BACKOFF_SECONDS,
                        TimeUnit.SECONDS,
                    )
                    .addTag(WORK_TAG)
                    .build()

            workManager.enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )

        } catch (
            error: RuntimeException
        ) {
            Log.e(
                LOG_TAG,
                "Periodic schedule sync could not be enqueued.",
                error,
            )
        }
    }

    private fun networkConstraints():
            Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(
                NetworkType.CONNECTED
            )
            .build()

    companion object {
        const val IMMEDIATE_WORK_NAME =
            "yaninda-alarm-schedule-sync-now"

        const val PERIODIC_WORK_NAME =
            "yaninda-alarm-schedule-sync-periodic"

        const val WORK_TAG =
            "yaninda-alarm-schedule-sync"

        private const val
                PERIODIC_INTERVAL_MINUTES =
            15L

        private const val
                INITIAL_BACKOFF_SECONDS =
            30L

        private const val LOG_TAG =
            "YanindaScheduleSync"
    }
}