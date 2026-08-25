package com.berkant.yaninda.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.berkant.yaninda.YanindaApplication
import com.berkant.yaninda.domain.occurrence.DoseOccurrenceStatus
import com.berkant.yaninda.notification.NotificationDeliveryResult
import com.berkant.yaninda.reminder.AlarmIntentFactory
import com.berkant.yaninda.reminder.MedicationAlarmAttentionService
import com.berkant.yaninda.reminder.ReminderResponseWindowResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MedicationAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {

        val application =
            context.applicationContext
                    as? YanindaApplication

        if (application == null) {
            Log.e(
                TAG,
                "Medication alarm could not access the application container.",
            )
            return
        }

        when (intent.action) {

            AlarmIntentFactory.ACTION_TEST_ALARM -> {

                val pendingResult =
                    goAsync()

                application
                    .applicationScope
                    .launch {

                        try {

                            val firedAt =
                                application
                                    .timeProvider
                                    .now()

                            val result =
                                application
                                    .reminderNotifier
                                    .showTestReminder()

                            application
                                .reminderCoordinator
                                .recordTestAlarmDelivery(
                                    firedAt,
                                    result,
                                )

                        } catch (
                            error: CancellationException
                        ) {
                            throw error

                        } catch (_: Exception) {

                            Log.e(
                                TAG,
                                "Test alarm diagnostics could not be recorded.",
                            )

                        } finally {
                            pendingResult.finish()
                        }
                    }
            }

            AlarmIntentFactory.ACTION_MEDICATION_ALARM -> {

                val occurrenceId =
                    AlarmIntentFactory
                        .occurrenceId(intent)

                if (occurrenceId == null) {

                    Log.e(
                        TAG,
                        "Medication alarm intent did not contain a valid occurrence identity.",
                    )

                    return
                }

                val pendingResult =
                    goAsync()

                application
                    .applicationScope
                    .launch {

                        try {

                            val firedAt =
                                application
                                    .timeProvider
                                    .now()

                            val transitions =
                                application
                                    .doseOccurrenceRepository
                                    .markDoseGroupReminderDue(
                                        occurrenceId =
                                            occurrenceId,
                                        firedAt =
                                            firedAt,
                                    )

                            if (
                                transitions.any {
                                    it.stateChanged
                                }
                            ) {
                                application
                                    .syncWorkScheduler
                                    .requestSync()
                            }

                            val hasDueOccurrence =
                                transitions.any {

                                    it.stateChanged &&
                                            it.occurrence.status ==
                                            DoseOccurrenceStatus.DUE
                                }

                            if (hasDueOccurrence) {
                                val notificationResult =
                                    application
                                        .reminderNotifier
                                        .showMedicationReminder(
                                            occurrenceId
                                        )

                                application
                                    .reminderCoordinator
                                    .recordMedicationAlarmDelivery(
                                        firedAt =
                                            firedAt,
                                        result =
                                            notificationResult,
                                    )

                                /*
                                 * Medication attention is intentionally NOT
                                 * gated by notification permission anymore.
                                 *
                                 * The attention service has a strict 40-second
                                 * hard timeout, so even if Android cannot show
                                 * the notification/full-screen UI we prefer a
                                 * bounded audible/vibration alarm over silently
                                 * missing the medication reminder.
                                 *
                                 * The readiness gate still warns prominently
                                 * when notification/full-screen capability is
                                 * unavailable.
                                 */
                                if (
                                    shouldStartMedicationAttention(
                                        notificationResult
                                    )
                                ) {
                                    try {
                                        MedicationAlarmAttentionService
                                            .start(
                                                context = context,
                                                occurrenceId = occurrenceId,
                                            )
                                    } catch (error: Exception) {
                                        Log.e(
                                            TAG,
                                            "Medication attention service could not be started.",
                                            error,
                                        )
                                    }
                                }
                            }

                            application
                                .reminderCoordinator
                                .refreshUpcoming()

                        } catch (
                            error: CancellationException
                        ) {
                            throw error

                        } catch (
                            error: Exception
                        ) {

                            Log.e(
                                TAG,
                                "Medication alarm delivery failed before completion.",
                                error,
                            )

                        } finally {
                            pendingResult.finish()
                        }
                    }
            }

            AlarmIntentFactory.ACTION_RESPONSE_WINDOW_ELAPSED -> {

                val launch =
                    AlarmIntentFactory
                        .responseWindowLaunch(
                            intent
                        )

                if (launch == null) {

                    Log.e(
                        TAG,
                        "Response-window intent did not contain a valid identity/generation.",
                    )

                    return
                }

                val pendingResult =
                    goAsync()

                application
                    .applicationScope
                    .launch {

                        try {

                            val result =
                                application
                                    .reminderCoordinator
                                    .handleResponseWindow(
                                        occurrenceId =
                                            launch.occurrenceId,
                                        expectedAutomaticRetryCount =
                                            launch
                                                .expectedAutomaticRetryCount,
                                    )

                            /*
                             * Ignored means this was a stale generation.
                             * In that case we MUST NOT stop the currently
                             * active (newer) alarm service.
                             */
                            if (
                                result !=
                                ReminderResponseWindowResult.Ignored
                            ) {
                                MedicationAlarmAttentionService
                                    .stop(context)
                            }

                            application
                                .reminderCoordinator
                                .refreshUpcoming()

                        } catch (
                            error: CancellationException
                        ) {
                            throw error

                        } catch (
                            error: Exception
                        ) {

                            Log.e(
                                TAG,
                                "Response-window transition could not be completed.",
                                error,
                            )

                        } finally {
                            pendingResult.finish()
                        }
                    }
            }
        }
    }

    companion object {

        private const val TAG =
            "MedicationAlarm"
    }
}

internal fun shouldStartMedicationAttention(
    notificationResult: NotificationDeliveryResult,
): Boolean =
    when (notificationResult) {
        NotificationDeliveryResult.Delivered,
        is NotificationDeliveryResult.Blocked,
        NotificationDeliveryResult.PlatformFailure,
        -> true
    }
