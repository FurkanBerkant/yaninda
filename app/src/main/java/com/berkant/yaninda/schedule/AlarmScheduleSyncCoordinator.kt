package com.berkant.yaninda.schedule

import com.berkant.yaninda.data.device.DeviceIdentityRepository
import com.berkant.yaninda.data.schedule.AlarmScheduleStateRepository
import com.berkant.yaninda.reminder.ReminderCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AlarmScheduleSyncStatus(
    val desiredVersion: Long = 0L,
    val appliedVersion: Long = 0L,
    val hasError: Boolean = false,
) {
    val isCurrent: Boolean
        get() =
            desiredVersion > 0L &&
                    appliedVersion >= desiredVersion
}

class AlarmScheduleSyncCoordinator(
    private val remoteRepository:
    AlarmScheduleRemoteRepository,
    private val localApplier:
    AlarmScheduleLocalApplier,
    private val stateRepository:
    AlarmScheduleStateRepository,
    private val deviceIdentityRepository:
    DeviceIdentityRepository,
    private val reminderCoordinator:
    ReminderCoordinator,
) {

    /*
     * Activity'deki sürekli listener ile ilerideki
     * WorkManager aynı anda aynı schedule'ı
     * uygulamaya çalışmasın.
     */
    private val syncMutex =
        Mutex()

    private val mutableStatus =
        MutableStateFlow(
            AlarmScheduleSyncStatus()
        )

    val status: StateFlow<AlarmScheduleSyncStatus> =
        mutableStatus.asStateFlow()

    /*
     * Uygulama açıkken kullanılan sürekli sync.
     *
     * Firestore değişikliğini dinlemeye devam eder.
     */
    suspend fun run(
        familyId: String,
        onScheduleAccessReady:
        suspend () -> Unit = {},
    ) {
        val deviceId =
            deviceIdentityRepository
                .getOrCreateDeviceId()

        while (
            currentCoroutineContext().isActive
        ) {
            try {
                remoteRepository
                    .ensureScheduleAccess(
                        familyId = familyId,
                        deviceId = deviceId,
                    )

                onScheduleAccessReady()

                remoteRepository
                    .observeDesiredSchedule(
                        familyId
                    )
                    .collect { remoteSchedule ->

                        applyRemoteSchedule(
                            familyId =
                                familyId,
                            deviceId =
                                deviceId,
                            remoteSchedule =
                                remoteSchedule,
                        )
                    }

            } catch (
                error: CancellationException
            ) {
                throw error

            } catch (
                error: Exception
            ) {
                /*
                 * Cloud hatası lokal çalışan
                 * programı asla silmez.
                 */
                mutableStatus.update {
                    it.copy(
                        hasError = true,
                    )
                }

                delay(
                    RETRY_DELAY_MILLIS
                )
            }
        }
    }

    /*
     * WorkManager / FCM / network recovery gibi
     * kısa ömürlü tetikleyiciler için.
     *
     * Listener açmaz.
     * Firestore'dan mevcut desired version'ı
     * bir kere okur, gerekiyorsa uygular ve döner.
     */
    suspend fun syncOnce(
        familyId: String,
    ) {
        val deviceId =
            deviceIdentityRepository
                .getOrCreateDeviceId()

        try {
            remoteRepository
                .ensureScheduleAccess(
                    familyId = familyId,
                    deviceId = deviceId,
                )

            val remoteSchedule =
                remoteRepository
                    .fetchDesiredSchedule(
                        familyId
                    )

            applyRemoteSchedule(
                familyId =
                    familyId,
                deviceId =
                    deviceId,
                remoteSchedule =
                    remoteSchedule,
            )

        } catch (
            error: CancellationException
        ) {
            throw error

        } catch (
            error: Exception
        ) {
            mutableStatus.update {
                it.copy(
                    hasError = true,
                )
            }

            /*
             * Burada exception'ı yutmuyoruz.
             *
             * Sonraki adımda WorkManager bunu
             * görüp Result.retry() dönecek.
             */
            throw error
        }
    }

    private suspend fun applyRemoteSchedule(
        familyId: String,
        deviceId: String,
        remoteSchedule:
        PublishedScheduleVersion,
    ) {
        syncMutex.withLock {

            val appliedVersion =
                stateRepository
                    .getAppliedVersion(
                        familyId
                    )

            mutableStatus.value =
                AlarmScheduleSyncStatus(
                    desiredVersion =
                        remoteSchedule.version,
                    appliedVersion =
                        appliedVersion,
                    hasError = false,
                )

            if (
                remoteSchedule.version <= 0L
            ) {
                return@withLock
            }

            if (
                remoteSchedule.version >
                appliedVersion
            ) {
                /*
                 * Önce tüm remote snapshot
                 * validate + persist edilir.
                 *
                 * Başarısız olursa eski çalışan
                 * Room programı korunur.
                 */
                localApplier.apply(
                    remoteSchedule
                )

                /*
                 * Room işlemi başarılı olduktan
                 * sonra version applied kabul edilir.
                 */
                stateRepository
                    .recordAppliedVersion(
                        familyId =
                            familyId,
                        version =
                            remoteSchedule.version,
                    )

                mutableStatus.update {
                    it.copy(
                        appliedVersion =
                            remoteSchedule.version,
                        hasError = false,
                    )
                }

                /*
                 * Yeni Room programından gerçek
                 * AlarmManager alarmlarını üret.
                 */
                reminderCoordinator
                    .refreshUpcoming()
            }

            /*
             * Cloud device status yalnızca
             * gözlem bilgisidir.
             *
             * Buradaki hata lokal alarm
             * programını geri alamaz.
             */
            runCatching {
                remoteRepository
                    .markScheduleApplied(
                        familyId =
                            familyId,
                        deviceId =
                            deviceId,
                        scheduleVersion =
                            remoteSchedule.version,
                    )
            }
        }
    }

    private companion object {
        const val RETRY_DELAY_MILLIS =
            15_000L
    }
}