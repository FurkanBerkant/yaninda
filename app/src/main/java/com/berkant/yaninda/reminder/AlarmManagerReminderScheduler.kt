package com.berkant.yaninda.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import com.berkant.yaninda.core.time.TimeProvider
import java.time.Instant

class AlarmManagerReminderScheduler(
    context: Context,
    private val timeProvider: TimeProvider,
) : ReminderScheduler {
    private val applicationContext = context.applicationContext
    private val alarmManager = applicationContext.getSystemService(AlarmManager::class.java)

    override fun exactAlarmCapability(): ExactAlarmCapability {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return ExactAlarmCapability.AVAILABLE
        }
        return try {
            if (alarmManager.canScheduleExactAlarms()) {
                ExactAlarmCapability.AVAILABLE
            } else {
                ExactAlarmCapability.USER_ACTION_REQUIRED
            }
        } catch (_: RuntimeException) {
            ExactAlarmCapability.CHECK_FAILED
        }
    }

    override fun scheduleOccurrence(
        occurrenceId: String,
        triggerAt: Instant,
    ): AlarmSchedulingResult {
        require(occurrenceId.isNotBlank()) { "Occurrence ID cannot be blank." }
        return schedule(
            triggerAt = triggerAt,
            operation = AlarmIntentFactory.occurrenceBroadcast(applicationContext, occurrenceId),
        )
    }

    override fun cancelOccurrence(occurrenceId: String): AlarmCancellationResult {
        require(occurrenceId.isNotBlank()) { "Occurrence ID cannot be blank." }
        val alarm = cancel(AlarmIntentFactory.occurrenceBroadcast(applicationContext, occurrenceId))
        val response =
            cancelResponseWindow(occurrenceId)
        return if (
            alarm == AlarmCancellationResult.Cancelled &&
            response == AlarmCancellationResult.Cancelled
        ) {
            AlarmCancellationResult.Cancelled
        } else {
            AlarmCancellationResult.PlatformFailure
        }
    }

    override fun scheduleResponseWindow(
        occurrenceId: String,
        triggerAt: Instant,
    ): AlarmSchedulingResult =
        scheduleResponseWindow(
            occurrenceId = occurrenceId,
            expectedAutomaticRetryCount = 0,
            triggerAt = triggerAt,
        )

    override fun scheduleResponseWindow(
        occurrenceId: String,
        expectedAutomaticRetryCount: Int,
        triggerAt: Instant,
    ): AlarmSchedulingResult {
        require(occurrenceId.isNotBlank()) { "Occurrence ID cannot be blank." }
        require(expectedAutomaticRetryCount >= 0) {
            "Expected automatic retry count cannot be negative."
        }

        return schedule(
            triggerAt = triggerAt,
            operation = AlarmIntentFactory.responseWindowBroadcast(
                context = applicationContext,
                occurrenceId = occurrenceId,
                expectedAutomaticRetryCount = expectedAutomaticRetryCount,
            ),
        )
    }

    override fun cancelResponseWindow(occurrenceId: String): AlarmCancellationResult {
        require(occurrenceId.isNotBlank()) { "Occurrence ID cannot be blank." }

        val operations =
            buildList {
                /*
                 * Cancel the pre-generation PendingIntent used by older app
                 * versions as well.
                 */
                add(
                    AlarmIntentFactory.responseWindowBroadcast(
                        applicationContext,
                        occurrenceId,
                    )
                )

                for (
                    retryCount in
                    0..MedicationAlarmPolicy.MAX_AUTOMATIC_RETRIES
                ) {
                    add(
                        AlarmIntentFactory.responseWindowBroadcast(
                            context = applicationContext,
                            occurrenceId = occurrenceId,
                            expectedAutomaticRetryCount = retryCount,
                        )
                    )
                }
            }

        val results =
            operations.map(::cancel)

        return if (
            results.all {
                it == AlarmCancellationResult.Cancelled
            }
        ) {
            AlarmCancellationResult.Cancelled
        } else {
            AlarmCancellationResult.PlatformFailure
        }
    }

    override fun scheduleTestAlarm(triggerAt: Instant): AlarmSchedulingResult = schedule(
        triggerAt = triggerAt,
        operation = AlarmIntentFactory.testBroadcast(applicationContext),
    )

    override fun cancelTestAlarm(): AlarmCancellationResult =
        cancel(AlarmIntentFactory.testBroadcast(applicationContext))

    private fun schedule(
        triggerAt: Instant,
        operation: PendingIntent,
    ): AlarmSchedulingResult {
        if (triggerAt <= timeProvider.now()) {
            return AlarmSchedulingResult.TriggerTimeNotFuture
        }
        when (exactAlarmCapability()) {
            ExactAlarmCapability.USER_ACTION_REQUIRED ->
                return AlarmSchedulingResult.ExactAlarmUnavailable

            ExactAlarmCapability.CHECK_FAILED,
            ExactAlarmCapability.NOT_CHECKED,
            -> return AlarmSchedulingResult.PlatformFailure

            ExactAlarmCapability.AVAILABLE -> Unit
        }

        return try {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(
                triggerAt.toEpochMilli(),
                AlarmIntentFactory.appContent(applicationContext),
            )
            alarmManager.setAlarmClock(alarmClockInfo, operation)
            AlarmSchedulingResult.Scheduled
        } catch (_: SecurityException) {
            AlarmSchedulingResult.ExactAlarmUnavailable
        } catch (_: RuntimeException) {
            AlarmSchedulingResult.PlatformFailure
        }
    }

    private fun cancel(operation: PendingIntent): AlarmCancellationResult = try {
        alarmManager.cancel(operation)
        AlarmCancellationResult.Cancelled
    } catch (_: RuntimeException) {
        AlarmCancellationResult.PlatformFailure
    }
}
