package com.berkant.yaninda.secondary

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import java.time.Instant

enum class SecondaryReminderSchedulingResult {
    EXACT,
    APPROXIMATE,
    FAILED,
}

interface SecondaryReminderScheduler {
    fun schedule(occurrenceId: String, triggerAt: Instant): SecondaryReminderSchedulingResult

    fun cancel(occurrenceId: String)
}

class AlarmManagerSecondaryReminderScheduler(
    context: Context,
) : SecondaryReminderScheduler {
    private val applicationContext = context.applicationContext
    private val alarmManager = applicationContext.getSystemService(AlarmManager::class.java)

    override fun schedule(
        occurrenceId: String,
        triggerAt: Instant,
    ): SecondaryReminderSchedulingResult {
        require(occurrenceId.isNotBlank()) { "Occurrence ID cannot be blank." }
        val operation = SecondaryReminderIntentFactory.broadcast(
            applicationContext,
            occurrenceId,
        )
        return try {
            if (canScheduleExact()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt.toEpochMilli(),
                    operation,
                )
                SecondaryReminderSchedulingResult.EXACT
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt.toEpochMilli(),
                    operation,
                )
                SecondaryReminderSchedulingResult.APPROXIMATE
            }
        } catch (_: SecurityException) {
            scheduleApproximate(triggerAt, operation)
        } catch (_: RuntimeException) {
            SecondaryReminderSchedulingResult.FAILED
        }
    }

    override fun cancel(occurrenceId: String) {
        if (occurrenceId.isBlank()) return
        try {
            alarmManager.cancel(
                SecondaryReminderIntentFactory.broadcast(applicationContext, occurrenceId)
            )
        } catch (_: RuntimeException) {
            // Refreshing the cache will try again; the receiver also revalidates every alarm.
        }
    }

    private fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun scheduleApproximate(
        triggerAt: Instant,
        operation: PendingIntent,
    ): SecondaryReminderSchedulingResult = try {
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt.toEpochMilli(),
            operation,
        )
        SecondaryReminderSchedulingResult.APPROXIMATE
    } catch (_: RuntimeException) {
        SecondaryReminderSchedulingResult.FAILED
    }
}
