package com.berkant.yaninda.ui.caregiver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import com.berkant.yaninda.R
import com.berkant.yaninda.data.diagnostics.AlarmDeliveryDiagnostic
import com.berkant.yaninda.data.diagnostics.AlarmDeliveryOutcome
import com.berkant.yaninda.notification.FullScreenIntentCapability
import com.berkant.yaninda.notification.NotificationCapability
import com.berkant.yaninda.reminder.ExactAlarmCapability
import com.berkant.yaninda.reminder.ReminderRuntimeStatus
import com.berkant.yaninda.ui.components.YanindaCard
import com.berkant.yaninda.ui.components.YanindaIconType
import com.berkant.yaninda.ui.components.YanindaInfoRow
import com.berkant.yaninda.ui.components.YanindaOutlinedButton
import com.berkant.yaninda.ui.components.YanindaPrimaryButton
import com.berkant.yaninda.ui.components.YanindaSectionTitle
import com.berkant.yaninda.ui.components.YanindaStatusTone
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun LocalReminderPanel(
    status: ReminderRuntimeStatus,
    feedback: ReminderFeedback?,
    isWorking: Boolean,
    onScheduleTest: () -> Unit,
    onCancelTest: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenFullScreenSettings: () -> Unit,
    onDismissFeedback: () -> Unit,
) {
    val canRunTest = status.exactAlarmCapability == ExactAlarmCapability.AVAILABLE &&
        status.notificationCapability == NotificationCapability.AVAILABLE &&
        !isWorking

    YanindaCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            YanindaSectionTitle(
                title = stringResource(R.string.local_reminder_panel_title),
                icon = YanindaIconType.ALARM,
            )
            Text(
                text = stringResource(R.string.local_reminder_panel_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            YanindaInfoRow(
                label = stringResource(R.string.exact_alarm_status_label),
                value = exactAlarmStatusText(status.exactAlarmCapability),
                icon = YanindaIconType.CLOCK,
                tone = status.exactAlarmCapability.statusTone(),
            )
            YanindaInfoRow(
                label = stringResource(R.string.notification_status_label),
                value = notificationStatusText(status.notificationCapability),
                icon = YanindaIconType.ALARM,
                tone = status.notificationCapability.statusTone(),
            )
            YanindaInfoRow(
                label = stringResource(R.string.full_screen_status_label),
                value = fullScreenStatusText(status.fullScreenIntentCapability),
                icon = YanindaIconType.DEVICE,
                tone = status.fullScreenIntentCapability.statusTone(),
            )
            YanindaInfoRow(
                label = stringResource(R.string.next_local_alarm_label),
                value = status.nextAlarmAt?.toLocalDateTimeText()
                    ?: stringResource(
                        if (status.plannedOccurrenceCount == 0) {
                            R.string.next_local_alarm_none
                        } else {
                            R.string.next_local_alarm_not_scheduled
                        }
                    ),
                icon = YanindaIconType.CALENDAR,
                tone = if (status.nextAlarmAt != null) {
                    YanindaStatusTone.SUCCESS
                } else {
                    YanindaStatusTone.NEUTRAL
                },
            )
            YanindaInfoRow(
                label = stringResource(R.string.last_medication_alarm_label),
                value = alarmDiagnosticText(status.diagnostics.lastMedicationAlarm),
                icon = YanindaIconType.MEDICATION,
                tone = status.diagnostics.lastMedicationAlarm.statusTone(),
            )
            YanindaInfoRow(
                label = stringResource(R.string.last_test_alarm_label),
                value = alarmDiagnosticText(status.diagnostics.lastTestAlarm),
                icon = YanindaIconType.ALARM,
                tone = status.diagnostics.lastTestAlarm.statusTone(),
            )

            if (status.diagnosticsStorageIssue) {
                Text(
                    text = stringResource(R.string.reminder_diagnostics_storage_issue),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (status.planningIssueCount > 0) {
                Text(
                    text = pluralStringResource(
                        R.plurals.reminder_planning_issue_count,
                        status.planningIssueCount,
                        status.planningIssueCount,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (status.failedOperationCount > 0) {
                Text(
                    text = pluralStringResource(
                        R.plurals.reminder_operation_failure_count,
                        status.failedOperationCount,
                        status.failedOperationCount,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (status.exactAlarmCapability == ExactAlarmCapability.USER_ACTION_REQUIRED) {
                YanindaOutlinedButton(
                    text = stringResource(R.string.open_exact_alarm_settings),
                    onClick = onOpenExactAlarmSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    enabled = !isWorking,
                    icon = YanindaIconType.SETTINGS,
                )
            }

            if (
                status.fullScreenIntentCapability ==
                FullScreenIntentCapability.USER_ACTION_REQUIRED
            ) {
                YanindaOutlinedButton(
                    text = stringResource(R.string.open_full_screen_settings),
                    onClick = onOpenFullScreenSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    enabled = !isWorking,
                    icon = YanindaIconType.DEVICE,
                )
                Text(
                    text = stringResource(R.string.full_screen_fallback_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when (status.notificationCapability) {
                NotificationCapability.RUNTIME_PERMISSION_REQUIRED -> YanindaOutlinedButton(
                    text = stringResource(R.string.allow_notifications),
                    onClick = onRequestNotificationPermission,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    enabled = !isWorking,
                    icon = YanindaIconType.ALARM,
                )

                NotificationCapability.APP_NOTIFICATIONS_DISABLED,
                NotificationCapability.CHANNEL_DISABLED,
                NotificationCapability.CHANNEL_ATTENTION_REQUIRED,
                -> YanindaOutlinedButton(
                    text = stringResource(R.string.open_notification_settings),
                    onClick = onOpenNotificationSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    enabled = !isWorking,
                    icon = YanindaIconType.SETTINGS,
                )

                NotificationCapability.AVAILABLE,
                NotificationCapability.CHECK_FAILED,
                NotificationCapability.NOT_CHECKED,
                -> Unit
            }

            YanindaPrimaryButton(
                text = stringResource(R.string.schedule_one_minute_test),
                onClick = onScheduleTest,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp),
                enabled = canRunTest,
                icon = YanindaIconType.ALARM,
            )
            YanindaOutlinedButton(
                text = stringResource(R.string.cancel_one_minute_test),
                onClick = onCancelTest,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                enabled = !isWorking,
                icon = YanindaIconType.WARNING,
            )

            status.testAlarmScheduledAt?.let { triggerAt ->
                Text(
                    text = stringResource(
                        R.string.test_alarm_scheduled_for,
                        triggerAt.toLocalDateTimeText(),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            feedback?.let {
                CaregiverNotice(
                    title = stringResource(R.string.reminder_feedback_title),
                    body = reminderFeedbackText(it),
                    isError = it != ReminderFeedback.TEST_SCHEDULED &&
                        it != ReminderFeedback.TEST_CANCELLED,
                )
                YanindaOutlinedButton(
                    text = stringResource(R.string.reminder_feedback_dismiss),
                    onClick = onDismissFeedback,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                )
            }
        }
    }
}

@Composable
private fun exactAlarmStatusText(capability: ExactAlarmCapability): String = stringResource(
    when (capability) {
        ExactAlarmCapability.NOT_CHECKED -> R.string.reminder_status_checking
        ExactAlarmCapability.AVAILABLE -> R.string.reminder_status_ready
        ExactAlarmCapability.USER_ACTION_REQUIRED -> R.string.reminder_status_permission_required
        ExactAlarmCapability.CHECK_FAILED -> R.string.reminder_status_check_failed
    }
)

@Composable
private fun notificationStatusText(capability: NotificationCapability): String = stringResource(
    when (capability) {
        NotificationCapability.NOT_CHECKED -> R.string.reminder_status_checking
        NotificationCapability.AVAILABLE -> R.string.reminder_status_ready
        NotificationCapability.RUNTIME_PERMISSION_REQUIRED ->
            R.string.reminder_status_permission_required

        NotificationCapability.APP_NOTIFICATIONS_DISABLED ->
            R.string.reminder_status_app_notifications_disabled

        NotificationCapability.CHANNEL_DISABLED -> R.string.reminder_status_channel_disabled
        NotificationCapability.CHANNEL_ATTENTION_REQUIRED ->
            R.string.reminder_status_channel_attention_required

        NotificationCapability.CHECK_FAILED -> R.string.reminder_status_check_failed
    }
)

@Composable
private fun fullScreenStatusText(capability: FullScreenIntentCapability): String = stringResource(
    when (capability) {
        FullScreenIntentCapability.NOT_CHECKED -> R.string.reminder_status_checking
        FullScreenIntentCapability.AVAILABLE -> R.string.reminder_status_ready
        FullScreenIntentCapability.USER_ACTION_REQUIRED ->
            R.string.reminder_status_permission_required

        FullScreenIntentCapability.CHECK_FAILED -> R.string.reminder_status_check_failed
    }
)

private fun ExactAlarmCapability.statusTone(): YanindaStatusTone = when (this) {
    ExactAlarmCapability.AVAILABLE -> YanindaStatusTone.SUCCESS
    ExactAlarmCapability.USER_ACTION_REQUIRED -> YanindaStatusTone.WARNING
    ExactAlarmCapability.NOT_CHECKED -> YanindaStatusTone.INFO
    ExactAlarmCapability.CHECK_FAILED -> YanindaStatusTone.ERROR
}

private fun NotificationCapability.statusTone(): YanindaStatusTone = when (this) {
    NotificationCapability.AVAILABLE -> YanindaStatusTone.SUCCESS
    NotificationCapability.RUNTIME_PERMISSION_REQUIRED,
    NotificationCapability.APP_NOTIFICATIONS_DISABLED,
    NotificationCapability.CHANNEL_DISABLED,
    NotificationCapability.CHANNEL_ATTENTION_REQUIRED,
    -> YanindaStatusTone.WARNING

    NotificationCapability.NOT_CHECKED -> YanindaStatusTone.INFO
    NotificationCapability.CHECK_FAILED -> YanindaStatusTone.ERROR
}

private fun FullScreenIntentCapability.statusTone(): YanindaStatusTone = when (this) {
    FullScreenIntentCapability.AVAILABLE -> YanindaStatusTone.SUCCESS
    FullScreenIntentCapability.USER_ACTION_REQUIRED -> YanindaStatusTone.WARNING
    FullScreenIntentCapability.NOT_CHECKED -> YanindaStatusTone.INFO
    FullScreenIntentCapability.CHECK_FAILED -> YanindaStatusTone.ERROR
}

private fun AlarmDeliveryDiagnostic?.statusTone(): YanindaStatusTone = when (this?.outcome) {
    AlarmDeliveryOutcome.DELIVERED -> YanindaStatusTone.SUCCESS
    AlarmDeliveryOutcome.BLOCKED,
    AlarmDeliveryOutcome.PLATFORM_FAILURE,
    -> YanindaStatusTone.ERROR

    null -> YanindaStatusTone.NEUTRAL
}

@Composable
private fun alarmDiagnosticText(diagnostic: AlarmDeliveryDiagnostic?): String {
    if (diagnostic == null) return stringResource(R.string.last_alarm_none)
    val outcome = stringResource(
        when (diagnostic.outcome) {
            AlarmDeliveryOutcome.DELIVERED -> R.string.last_alarm_delivery_delivered
            AlarmDeliveryOutcome.BLOCKED -> R.string.last_alarm_delivery_blocked
            AlarmDeliveryOutcome.PLATFORM_FAILURE -> R.string.last_alarm_delivery_failed
        }
    )
    return stringResource(
        R.string.last_alarm_value,
        diagnostic.firedAt.toLocalDateTimeText(),
        outcome,
    )
}

@Composable
private fun reminderFeedbackText(feedback: ReminderFeedback): String = stringResource(
    when (feedback) {
        ReminderFeedback.TEST_SCHEDULED -> R.string.reminder_feedback_test_scheduled
        ReminderFeedback.TEST_CANCELLED -> R.string.reminder_feedback_test_cancelled
        ReminderFeedback.EXACT_ALARM_ACCESS_REQUIRED ->
            R.string.reminder_feedback_exact_alarm_required

        ReminderFeedback.NOTIFICATION_ACCESS_REQUIRED ->
            R.string.reminder_feedback_notification_required

        ReminderFeedback.REMINDER_SETUP_NEEDS_ATTENTION ->
            R.string.reminder_feedback_setup_attention

        ReminderFeedback.OPERATION_FAILED -> R.string.reminder_feedback_operation_failed
    }
)

private fun Instant.toLocalDateTimeText(): String = REMINDER_DATE_TIME_FORMATTER.format(
    atZone(ZoneId.systemDefault())
)

private val REMINDER_DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern(
    "d MMM HH:mm",
    Locale.forLanguageTag("tr-TR"),
)
