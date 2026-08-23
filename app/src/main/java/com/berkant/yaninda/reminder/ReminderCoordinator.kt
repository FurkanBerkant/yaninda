package com.berkant.yaninda.reminder

import com.berkant.yaninda.core.time.TimeProvider
import com.berkant.yaninda.data.diagnostics.AlarmDeliveryDiagnostic
import com.berkant.yaninda.data.diagnostics.AlarmDeliveryOutcome
import com.berkant.yaninda.data.diagnostics.ReminderDiagnosticsRepository
import com.berkant.yaninda.data.diagnostics.ReminderDiagnosticsSnapshot
import com.berkant.yaninda.data.repository.DoseOccurrenceRepository
import com.berkant.yaninda.data.repository.DoseOccurrenceTransition
import com.berkant.yaninda.domain.occurrence.AcknowledgementActor
import com.berkant.yaninda.domain.occurrence.DoseOccurrenceEvent
import com.berkant.yaninda.domain.occurrence.OccurrencePlanningWindow
import com.berkant.yaninda.notification.FullScreenIntentCapability
import com.berkant.yaninda.notification.NotificationCapability
import com.berkant.yaninda.notification.NotificationCancellationResult
import com.berkant.yaninda.notification.NotificationDeliveryResult
import com.berkant.yaninda.notification.ReminderNotifier
import com.berkant.yaninda.sync.SyncWorkScheduler
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ReminderRefreshFailure {
    OCCURRENCE_PERSISTENCE,
    ALARM_CANCELLATION,
    ALARM_SCHEDULING,
    RESPONSE_WINDOW_SCHEDULING,
}

data class ReminderRuntimeStatus(
    val exactAlarmCapability: ExactAlarmCapability = ExactAlarmCapability.NOT_CHECKED,
    val notificationCapability: NotificationCapability = NotificationCapability.NOT_CHECKED,
    val fullScreenIntentCapability: FullScreenIntentCapability =
        FullScreenIntentCapability.NOT_CHECKED,
    val lastRefreshAt: Instant? = null,
    val plannedOccurrenceCount: Int = 0,
    val scheduledAlarmCount: Int = 0,
    val cancelledAlarmCount: Int = 0,
    val nextAlarmAt: Instant? = null,
    val planningIssueCount: Int = 0,
    val failedOperationCount: Int = 0,
    val refreshFailure: ReminderRefreshFailure? = null,
    val testAlarmScheduledAt: Instant? = null,
    val lastAlarmFiredAt: Instant? = null,
    val lastTestAlarmFiredAt: Instant? = null,
    val lastNotificationDelivery: NotificationDeliveryResult? = null,
    val diagnostics: ReminderDiagnosticsSnapshot = ReminderDiagnosticsSnapshot(),
    val diagnosticsStorageIssue: Boolean = false,
)

sealed interface ReminderTestResult {
    data class Scheduled(
        val triggerAt: Instant,
    ) : ReminderTestResult

    data object Cancelled : ReminderTestResult

    data class ExactAlarmUnavailable(
        val capability: ExactAlarmCapability,
    ) : ReminderTestResult

    data class NotificationUnavailable(
        val capability: NotificationCapability,
    ) : ReminderTestResult

    data object TriggerTimeNotFuture : ReminderTestResult

    data object PlatformFailure : ReminderTestResult
}

sealed interface ReminderSnoozeResult {
    data class Scheduled(
        val triggerAt: Instant,
    ) : ReminderSnoozeResult

    data class ExactAlarmUnavailable(
        val capability: ExactAlarmCapability,
    ) : ReminderSnoozeResult

    data object NotActionable : ReminderSnoozeResult

    data object PlatformFailure : ReminderSnoozeResult
}

class ReminderCoordinator(
    private val occurrenceRepository: DoseOccurrenceRepository,
    private val scheduler: ReminderScheduler,
    private val notifier: ReminderNotifier,
    private val diagnosticsRepository: ReminderDiagnosticsRepository,
    private val timeProvider: TimeProvider,
    private val syncWorkScheduler: SyncWorkScheduler,
    private val planningHorizon: Duration = DEFAULT_PLANNING_HORIZON,
) {
    private val operationMutex = Mutex()
    private val mutableStatus = MutableStateFlow(ReminderRuntimeStatus())
    val status: StateFlow<ReminderRuntimeStatus> = mutableStatus.asStateFlow()

    init {
        require(!planningHorizon.isNegative && !planningHorizon.isZero) {
            "Reminder planning horizon must be positive."
        }
    }

    suspend fun refreshUpcoming(): ReminderRuntimeStatus = operationMutex.withLock {
        val refreshTime = timeProvider.now()
        val exactCapability = scheduler.exactAlarmCapability()
        val notificationCapability = notifier.capability()
        val fullScreenIntentCapability = notifier.fullScreenIntentCapability()
        var diagnosticsStorageIssue = false
        val diagnostics = try {
            diagnosticsRepository.read()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            diagnosticsStorageIssue = true
            mutableStatus.value.diagnostics
        }
        val plan = try {
            occurrenceRepository.persistPlan(
                OccurrencePlanningWindow(
                    startInclusive = refreshTime,
                    endExclusive = refreshTime.plus(planningHorizon),
                    zoneId = timeProvider.currentZoneId(),
                )
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return@withLock publishStatus(
                ReminderRuntimeStatus(
                    exactAlarmCapability = exactCapability,
                    notificationCapability = notificationCapability,
                    fullScreenIntentCapability = fullScreenIntentCapability,
                    lastRefreshAt = refreshTime,
                    failedOperationCount = 1,
                    refreshFailure = ReminderRefreshFailure.OCCURRENCE_PERSISTENCE,
                    lastAlarmFiredAt = diagnostics.lastMedicationAlarm?.firedAt,
                    lastTestAlarmFiredAt = diagnostics.lastTestAlarm?.firedAt,
                    diagnostics = diagnostics,
                    diagnosticsStorageIssue = diagnosticsStorageIssue,
                )
            )
        }

        if (plan.insertedCount > 0 || plan.cancelledOccurrenceIds.isNotEmpty()) {
            syncWorkScheduler.requestSync()
        }

        var cancellationFailures = 0
        plan.cancelledOccurrenceIds.forEach { occurrenceId ->
            if (scheduler.cancelOccurrence(occurrenceId) != AlarmCancellationResult.Cancelled) {
                cancellationFailures += 1
            }
        }

        var resolvedExactCapability = exactCapability
        var schedulingFailures = 0
        var responseWindowFailures = 0
        val scheduledInstants = mutableListOf<Instant>()
        if (exactCapability == ExactAlarmCapability.AVAILABLE) {
            plan.pendingAlarms.forEach { alarm ->
                when (
                    scheduler.scheduleOccurrence(
                        occurrenceId = alarm.occurrenceId,
                        triggerAt = alarm.triggerAt,
                    )
                ) {
                    AlarmSchedulingResult.Scheduled -> scheduledInstants += alarm.triggerAt
                    AlarmSchedulingResult.ExactAlarmUnavailable -> {
                        resolvedExactCapability = ExactAlarmCapability.USER_ACTION_REQUIRED
                        schedulingFailures += 1
                    }

                    AlarmSchedulingResult.PlatformFailure,
                    AlarmSchedulingResult.TriggerTimeNotFuture,
                    -> schedulingFailures += 1
                }
            }
            plan.awaitingResponseOccurrences
                .groupBy {
                    it.scheduledAt
                }
                .values
                .forEach { group ->

                    val occurrence =
                        group.minBy { it.id }

                    val lastAlertedAt =
                        group
                            .mapNotNull {
                                it.lastAlertedAt
                            }
                            .maxOrNull()

                    if (lastAlertedAt == null) {

                        responseWindowFailures += 1

                    } else {

                        val expectedTrigger =
                            lastAlertedAt.plus(
                                RESPONSE_WINDOW
                            )

                        val triggerAt =
                            if (expectedTrigger > refreshTime) {
                                expectedTrigger
                            } else {
                                refreshTime.plus(
                                    RESPONSE_WINDOW_RECOVERY_DELAY
                                )
                            }

                        when (
                            scheduler.scheduleResponseWindow(
                                occurrence.id,
                                triggerAt,
                            )
                        ) {

                            AlarmSchedulingResult.Scheduled ->
                                Unit

                            AlarmSchedulingResult.ExactAlarmUnavailable -> {
                                resolvedExactCapability =
                                    ExactAlarmCapability
                                        .USER_ACTION_REQUIRED

                                responseWindowFailures += 1
                            }

                            AlarmSchedulingResult.PlatformFailure,
                            AlarmSchedulingResult.TriggerTimeNotFuture,
                                ->
                                responseWindowFailures += 1
                        }
                    }
                }
        }

        val failure = when {
            cancellationFailures > 0 -> ReminderRefreshFailure.ALARM_CANCELLATION
            schedulingFailures > 0 -> ReminderRefreshFailure.ALARM_SCHEDULING
            responseWindowFailures > 0 ->
                ReminderRefreshFailure.RESPONSE_WINDOW_SCHEDULING
            else -> null
        }
        publishStatus(
            ReminderRuntimeStatus(
                exactAlarmCapability = resolvedExactCapability,
                notificationCapability = notificationCapability,
                fullScreenIntentCapability = fullScreenIntentCapability,
                lastRefreshAt = refreshTime,
                plannedOccurrenceCount = plan.plannedCount,
                scheduledAlarmCount = scheduledInstants.size,
                cancelledAlarmCount = plan.cancelledOccurrenceIds.size - cancellationFailures,
                nextAlarmAt = scheduledInstants.minOrNull(),
                planningIssueCount = plan.issues.size,
                failedOperationCount = cancellationFailures + schedulingFailures +
                    responseWindowFailures,
                refreshFailure = failure,
                testAlarmScheduledAt = mutableStatus.value.testAlarmScheduledAt,
                lastAlarmFiredAt = diagnostics.lastMedicationAlarm?.firedAt,
                lastTestAlarmFiredAt = diagnostics.lastTestAlarm?.firedAt,
                lastNotificationDelivery = mutableStatus.value.lastNotificationDelivery,
                diagnostics = diagnostics,
                diagnosticsStorageIssue = diagnosticsStorageIssue,
            )
        )
    }

    suspend fun scheduleOneMinuteTest(): ReminderTestResult = operationMutex.withLock {
        val notificationCapability = notifier.capability()
        if (notificationCapability != NotificationCapability.AVAILABLE) {
            updateCapabilities(notificationCapability = notificationCapability)
            return@withLock ReminderTestResult.NotificationUnavailable(notificationCapability)
        }

        val exactCapability = scheduler.exactAlarmCapability()
        if (exactCapability != ExactAlarmCapability.AVAILABLE) {
            updateCapabilities(exactAlarmCapability = exactCapability)
            return@withLock ReminderTestResult.ExactAlarmUnavailable(exactCapability)
        }

        if (notifier.cancelTestReminder() != NotificationCancellationResult.CANCELLED) {
            return@withLock ReminderTestResult.PlatformFailure
        }

        val triggerAt = timeProvider.now().plus(TEST_ALARM_DELAY)
        when (scheduler.scheduleTestAlarm(triggerAt)) {
            AlarmSchedulingResult.Scheduled -> {
                mutableStatus.update {
                    it.copy(
                        exactAlarmCapability = exactCapability,
                        notificationCapability = notificationCapability,
                        testAlarmScheduledAt = triggerAt,
                    )
                }
                ReminderTestResult.Scheduled(triggerAt)
            }

            AlarmSchedulingResult.ExactAlarmUnavailable -> {
                updateCapabilities(
                    exactAlarmCapability = ExactAlarmCapability.USER_ACTION_REQUIRED,
                    notificationCapability = notificationCapability,
                )
                ReminderTestResult.ExactAlarmUnavailable(
                    ExactAlarmCapability.USER_ACTION_REQUIRED
                )
            }

            AlarmSchedulingResult.TriggerTimeNotFuture -> ReminderTestResult.TriggerTimeNotFuture
            AlarmSchedulingResult.PlatformFailure -> ReminderTestResult.PlatformFailure
        }
    }

    suspend fun cancelOneMinuteTest(): ReminderTestResult = operationMutex.withLock {
        val alarmCancellation = scheduler.cancelTestAlarm()
        val notificationCancellation = notifier.cancelTestReminder()
        when {
            alarmCancellation == AlarmCancellationResult.Cancelled &&
                notificationCancellation == NotificationCancellationResult.CANCELLED -> {
                mutableStatus.update { it.copy(testAlarmScheduledAt = null) }
                ReminderTestResult.Cancelled
            }

            else -> ReminderTestResult.PlatformFailure
        }
    }

    suspend fun acknowledgeTaken(
        occurrenceId: String,
    ): DoseOccurrenceTransition =
        operationMutex.withLock {

            require(occurrenceId.isNotBlank()) {
                "Occurrence ID cannot be blank."
            }

            val transitions =
                occurrenceRepository.applyEventToDoseGroup(
                    occurrenceId = occurrenceId,
                    event =
                        DoseOccurrenceEvent.TakenAcknowledged(
                            occurredAt = timeProvider.now(),
                            actor = AcknowledgementActor.GRANDFATHER,
                        ),
                )

            val representative =
                transitions.firstOrNull {
                    it.occurrence.id == occurrenceId
                } ?: transitions.firstOrNull()
                ?: error("The dose group contains no occurrences.")

            if (transitions.any { it.stateChanged }) {
                syncWorkScheduler.requestSync()
            }

            transitions.forEach { transition ->
                scheduler.cancelOccurrence(
                    transition.occurrence.id
                )

                scheduler.cancelResponseWindow(
                    transition.occurrence.id
                )

                notifier.cancelMedicationReminder(
                    transition.occurrence.id
                )
            }

            representative
        }

    suspend fun snoozeOccurrence(
        occurrenceId: String,
        snoozeMinutes: Int,
        maxSnoozes: Int,
    ): ReminderSnoozeResult =
        operationMutex.withLock {

            require(occurrenceId.isNotBlank()) {
                "Occurrence ID cannot be blank."
            }

            require(
                snoozeMinutes in
                        MIN_SNOOZE_MINUTES..MAX_SNOOZE_MINUTES
            ) {
                "Snooze duration is outside the configured safety bounds."
            }

            require(
                maxSnoozes in
                        MIN_SNOOZE_COUNT..MAX_SNOOZE_COUNT
            ) {
                "Snooze count is outside the configured safety bounds."
            }

            val requestedAt =
                timeProvider.now()

            val requestedTriggerAt =
                requestedAt.plus(
                    Duration.ofMinutes(
                        snoozeMinutes.toLong()
                    )
                )

            val transitions =
                occurrenceRepository
                    .applyEventToDoseGroup(
                        occurrenceId = occurrenceId,
                        event =
                            DoseOccurrenceEvent.SnoozeRequested(
                                occurredAt = requestedAt,
                                remindAt = requestedTriggerAt,
                                maxSnoozes = maxSnoozes,
                            ),
                    )

            if (transitions.isEmpty()) {
                return@withLock ReminderSnoozeResult.NotActionable
            }

            if (transitions.any { it.stateChanged }) {
                syncWorkScheduler.requestSync()
            }

            val representative =
                transitions.firstOrNull {
                    it.occurrence.id == occurrenceId
                } ?: transitions.first()

            transitions.forEach { transition ->
                scheduler.cancelResponseWindow(
                    transition.occurrence.id
                )
            }

            val triggerAt =
                representative
                    .occurrence
                    .nextReminderAt
                    ?: return@withLock ReminderSnoozeResult.NotActionable

            when (
                scheduler.scheduleOccurrence(
                    occurrenceId = occurrenceId,
                    triggerAt = triggerAt,
                )
            ) {

                AlarmSchedulingResult.Scheduled -> {

                    transitions.forEach { transition ->
                        notifier.cancelMedicationReminder(
                            transition.occurrence.id
                        )
                    }

                    ReminderSnoozeResult.Scheduled(
                        triggerAt
                    )
                }

                AlarmSchedulingResult.ExactAlarmUnavailable -> {

                    updateCapabilities(
                        exactAlarmCapability =
                            ExactAlarmCapability
                                .USER_ACTION_REQUIRED,
                    )

                    ReminderSnoozeResult.ExactAlarmUnavailable(
                        ExactAlarmCapability
                            .USER_ACTION_REQUIRED
                    )
                }

                AlarmSchedulingResult.PlatformFailure,
                AlarmSchedulingResult.TriggerTimeNotFuture,
                    ->
                    ReminderSnoozeResult.PlatformFailure
            }
        }

    suspend fun recordMedicationAlarmDelivery(
        firedAt: Instant,
        result: NotificationDeliveryResult,
    ) {
        val diagnostic = AlarmDeliveryDiagnostic(
            firedAt = firedAt,
            outcome = result.toDiagnosticOutcome(),
        )
        mutableStatus.update {
            it.copy(
                notificationCapability = result.toCapabilityOr(it.notificationCapability),
                lastAlarmFiredAt = firedAt,
                lastNotificationDelivery = result,
                diagnostics = it.diagnostics.copy(lastMedicationAlarm = diagnostic),
            )
        }
        persistDiagnostic { diagnosticsRepository.recordMedicationAlarm(diagnostic) }
    }

    suspend fun recordTestAlarmDelivery(
        firedAt: Instant,
        result: NotificationDeliveryResult,
    ) {
        val diagnostic = AlarmDeliveryDiagnostic(
            firedAt = firedAt,
            outcome = result.toDiagnosticOutcome(),
        )
        mutableStatus.update {
            it.copy(
                notificationCapability = result.toCapabilityOr(it.notificationCapability),
                testAlarmScheduledAt = null,
                lastTestAlarmFiredAt = firedAt,
                lastNotificationDelivery = result,
                diagnostics = it.diagnostics.copy(lastTestAlarm = diagnostic),
            )
        }
        persistDiagnostic { diagnosticsRepository.recordTestAlarm(diagnostic) }
    }

    private suspend fun persistDiagnostic(write: suspend () -> Unit) {
        try {
            write()
            mutableStatus.update { it.copy(diagnosticsStorageIssue = false) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            mutableStatus.update { it.copy(diagnosticsStorageIssue = true) }
        }
    }

    private fun publishStatus(newStatus: ReminderRuntimeStatus): ReminderRuntimeStatus {
        mutableStatus.value = newStatus
        return newStatus
    }

    private fun updateCapabilities(
        exactAlarmCapability: ExactAlarmCapability = mutableStatus.value.exactAlarmCapability,
        notificationCapability: NotificationCapability = mutableStatus.value.notificationCapability,
        fullScreenIntentCapability: FullScreenIntentCapability =
            mutableStatus.value.fullScreenIntentCapability,
    ) {
        mutableStatus.update {
            it.copy(
                exactAlarmCapability = exactAlarmCapability,
                notificationCapability = notificationCapability,
                fullScreenIntentCapability = fullScreenIntentCapability,
            )
        }
    }

    private fun NotificationDeliveryResult.toCapabilityOr(
        fallback: NotificationCapability,
    ): NotificationCapability = when (this) {
        NotificationDeliveryResult.Delivered -> if (
            fallback == NotificationCapability.NOT_CHECKED
        ) {
            NotificationCapability.AVAILABLE
        } else {
            fallback
        }
        is NotificationDeliveryResult.Blocked -> capability
        NotificationDeliveryResult.PlatformFailure -> fallback
    }

    private fun NotificationDeliveryResult.toDiagnosticOutcome(): AlarmDeliveryOutcome =
        when (this) {
            NotificationDeliveryResult.Delivered -> AlarmDeliveryOutcome.DELIVERED
            is NotificationDeliveryResult.Blocked -> AlarmDeliveryOutcome.BLOCKED
            NotificationDeliveryResult.PlatformFailure ->
                AlarmDeliveryOutcome.PLATFORM_FAILURE
        }

    companion object {
        private val DEFAULT_PLANNING_HORIZON: Duration = Duration.ofDays(15)
        private val RESPONSE_WINDOW: Duration = Duration.ofMinutes(30)
        private val RESPONSE_WINDOW_RECOVERY_DELAY: Duration = Duration.ofSeconds(5)
        private val TEST_ALARM_DELAY: Duration = Duration.ofMinutes(1)
        private const val MIN_SNOOZE_MINUTES = 1
        private const val MAX_SNOOZE_MINUTES = 60
        private const val MIN_SNOOZE_COUNT = 1
        private const val MAX_SNOOZE_COUNT = 5
    }
}
