package com.berkant.yaninda.schedule

import com.berkant.yaninda.data.device.DeviceIdentityRepository
import com.berkant.yaninda.data.schedule.AlarmScheduleStateRepository
import com.berkant.yaninda.reminder.ReminderCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive

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

    suspend fun run(
        familyId: String,
        onScheduleAccessReady: suspend () -> Unit = {},
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

                        if (
                            remoteSchedule.version <= 0L
                        ) {
                            return@collect
                        }

                        val appliedVersion =
                            stateRepository
                                .getAppliedVersion(
                                    familyId
                                )

                        if (
                            remoteSchedule.version >
                            appliedVersion
                        ) {
                            /*
                             * Validate and persist the complete
                             * snapshot BEFORE replacing the
                             * known-good local schedule.
                             */
                            localApplier.apply(
                                remoteSchedule
                            )

                            /*
                             * The Room transaction succeeded.
                             * Only now may this version be
                             * considered locally applied.
                             */
                            stateRepository
                                .recordAppliedVersion(
                                    familyId =
                                        familyId,
                                    version =
                                        remoteSchedule
                                            .version,
                                )

                            /*
                             * Re-plan exact local AlarmManager
                             * alarms from the newly applied
                             * Room configuration.
                             */
                            reminderCoordinator
                                .refreshUpcoming()
                        }

                        /*
                         * Cloud status is best-effort.
                         *
                         * Failure here must NEVER undo or
                         * disable the already applied local
                         * medication schedule.
                         */
                        runCatching {
                            remoteRepository
                                .markScheduleApplied(
                                    familyId =
                                        familyId,
                                    deviceId =
                                        deviceId,
                                    scheduleVersion =
                                        remoteSchedule
                                            .version,
                                )
                        }
                    }

            } catch (
                error: CancellationException
            ) {
                throw error

            } catch (_: Exception) {
                /*
                 * Network, Firestore or validation errors
                 * leave the last known-good local schedule
                 * untouched.
                 */
                delay(RETRY_DELAY_MILLIS)
            }
        }
    }

    private companion object {
        const val RETRY_DELAY_MILLIS =
            15_000L
    }
}