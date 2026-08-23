package com.berkant.yaninda.schedule

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.berkant.yaninda.YanindaApplication
import com.berkant.yaninda.domain.family.DeviceRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class AlarmScheduleSyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(
    appContext,
    workerParameters,
) {

    override suspend fun doWork(): Result {
        val application =
            applicationContext as? YanindaApplication
                ?: return Result.failure()

        return try {

            /*
             * Work bütün uygulama kurulumlarında
             * kayıtlı olabilir.
             *
             * Fakat schedule yalnızca gerçekten
             * eşleştirilmiş ALARM_DEVICE tarafından
             * uygulanabilir.
             */
            val selectedRole =
                application
                    .deviceIdentityRepository
                    .selectedRole
                    .first()

            val pairing =
                application
                    .deviceIdentityRepository
                    .pairing
                    .first()

            if (
                selectedRole != DeviceRole.ALARM_DEVICE ||
                pairing?.deviceRole !=
                DeviceRole.ALARM_DEVICE
            ) {
                return Result.success()
            }

            val coordinator =
                application
                    .alarmScheduleSyncCoordinator
                    ?: return Result.success()

            /*
             * Firestore listener açmaz.
             *
             * Desired schedule'ı bir kere kontrol eder,
             * gerekiyorsa Room + AlarmManager tarafına
             * uygular ve tamamlanır.
             */
            coordinator.syncOnce(
                familyId = pairing.familyId,
            )

            Result.success()

        } catch (
            error: CancellationException
        ) {
            throw error

        } catch (
            error: Exception
        ) {
            Log.w(
                LOG_TAG,
                "Schedule sync failed; WorkManager will retry.",
                error,
            )

            /*
             * Network kesilmiş olabilir.
             * Mevcut Room programına dokunulmadığı
             * için retry sırasında alarmlar çalışmaya
             * devam eder.
             */
            Result.retry()
        }
    }

    private companion object {
        const val LOG_TAG =
            "YanindaScheduleSync"
    }
}