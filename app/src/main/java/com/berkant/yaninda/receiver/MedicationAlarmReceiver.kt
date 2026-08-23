package com.berkant.yaninda.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.berkant.yaninda.YanindaApplication
import com.berkant.yaninda.domain.occurrence.DoseOccurrenceStatus
import com.berkant.yaninda.domain.occurrence.DoseOccurrenceEvent
import com.berkant.yaninda.reminder.AlarmIntentFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MedicationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val application = context.applicationContext as? YanindaApplication
        if (application == null) {
            Log.e(TAG, "Medication alarm could not access the application container.")
            return
        }

        when (intent.action) {
            AlarmIntentFactory.ACTION_TEST_ALARM -> {
                val pendingResult = goAsync()
                application.applicationScope.launch {
                    try {
                        val firedAt = application.timeProvider.now()
                        val result = application.reminderNotifier.showTestReminder()
                        application.reminderCoordinator.recordTestAlarmDelivery(firedAt, result)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        Log.e(TAG, "Test alarm diagnostics could not be recorded.")
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            AlarmIntentFactory.ACTION_MEDICATION_ALARM -> {
                val occurrenceId = AlarmIntentFactory.occurrenceId(intent)
                if (occurrenceId == null) {
                    Log.e(TAG, "Medication alarm intent did not contain a valid occurrence identity.")
                    return
                }
                val pendingResult = goAsync()
                application.applicationScope.launch {
                    try {
                        val firedAt = application.timeProvider.now()
                        val transition = application.doseOccurrenceRepository.markReminderDue(
                            occurrenceId = occurrenceId,
                            firedAt = firedAt,
                        )
                        if (transition.stateChanged) {
                            application.syncWorkScheduler.requestSync()
                        }
                        if (
                            transition.stateChanged &&
                            transition.occurrence.status == DoseOccurrenceStatus.DUE
                        ) {
                            val notificationResult =
                                application.reminderNotifier.showMedicationReminder(occurrenceId)
                            application.reminderCoordinator.recordMedicationAlarmDelivery(
                                firedAt = firedAt,
                                result = notificationResult,
                            )
                        }
                        application.reminderCoordinator.refreshUpcoming()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        Log.e(TAG, "Medication alarm delivery failed before completion.")
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            AlarmIntentFactory.ACTION_RESPONSE_WINDOW_ELAPSED -> {
                val occurrenceId = AlarmIntentFactory.responseWindowOccurrenceId(intent)
                if (occurrenceId == null) {
                    Log.e(TAG, "Response-window intent did not contain a valid occurrence identity.")
                    return
                }
                val pendingResult = goAsync()
                application.applicationScope.launch {
                    try {
                        val transition = application.doseOccurrenceRepository.applyEvent(
                            occurrenceId = occurrenceId,
                            event = DoseOccurrenceEvent.ResponseWindowElapsed(
                                application.timeProvider.now()
                            ),
                        )
                        if (
                            transition.stateChanged &&
                            transition.occurrence.status == DoseOccurrenceStatus.NO_CONFIRMATION
                        ) {
                            application.syncWorkScheduler.requestSync()
                        }
                        application.reminderCoordinator.refreshUpcoming()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        Log.e(TAG, "Response-window transition could not be completed.")
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "MedicationAlarm"
    }
}
