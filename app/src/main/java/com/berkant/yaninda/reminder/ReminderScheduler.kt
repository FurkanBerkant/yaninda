package com.berkant.yaninda.reminder

import java.time.Instant

enum class ExactAlarmCapability {
    NOT_CHECKED,
    AVAILABLE,
    USER_ACTION_REQUIRED,
    CHECK_FAILED,
}

sealed interface AlarmSchedulingResult {
    data object Scheduled : AlarmSchedulingResult

    data object ExactAlarmUnavailable : AlarmSchedulingResult

    data object TriggerTimeNotFuture : AlarmSchedulingResult

    data object PlatformFailure : AlarmSchedulingResult
}

sealed interface AlarmCancellationResult {
    data object Cancelled : AlarmCancellationResult

    data object PlatformFailure : AlarmCancellationResult
}

interface ReminderScheduler {
    fun exactAlarmCapability(): ExactAlarmCapability

    fun scheduleOccurrence(
        occurrenceId: String,
        triggerAt: Instant,
    ): AlarmSchedulingResult

    fun cancelOccurrence(occurrenceId: String): AlarmCancellationResult

    fun scheduleResponseWindow(
        occurrenceId: String,
        triggerAt: Instant,
    ): AlarmSchedulingResult

    fun scheduleResponseWindow(
        occurrenceId: String,
        expectedAutomaticRetryCount: Int,
        triggerAt: Instant,
    ): AlarmSchedulingResult =
        scheduleResponseWindow(
            occurrenceId = occurrenceId,
            triggerAt = triggerAt,
        )

    fun cancelResponseWindow(occurrenceId: String): AlarmCancellationResult

    fun scheduleTestAlarm(triggerAt: Instant): AlarmSchedulingResult

    fun cancelTestAlarm(): AlarmCancellationResult
}
