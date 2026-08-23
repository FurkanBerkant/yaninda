package com.berkant.yaninda.reminder

import com.berkant.yaninda.core.time.TimeProvider
import com.berkant.yaninda.data.diagnostics.AlarmDeliveryDiagnostic
import com.berkant.yaninda.data.diagnostics.AlarmDeliveryOutcome
import com.berkant.yaninda.data.diagnostics.ReminderDiagnosticsRepository
import com.berkant.yaninda.data.diagnostics.ReminderDiagnosticsSnapshot
import com.berkant.yaninda.data.repository.DoseOccurrenceRepository
import com.berkant.yaninda.data.repository.DoseOccurrenceTransition
import com.berkant.yaninda.data.repository.PersistedOccurrencePlan
import com.berkant.yaninda.domain.occurrence.DoseOccurrence
import com.berkant.yaninda.domain.occurrence.DoseOccurrenceEvent
import com.berkant.yaninda.domain.occurrence.DoseOccurrenceStateMachine
import com.berkant.yaninda.domain.occurrence.DoseOccurrenceStatus
import com.berkant.yaninda.domain.occurrence.NextOccurrenceResult
import com.berkant.yaninda.domain.occurrence.OccurrencePlanningWindow
import com.berkant.yaninda.notification.NotificationCapability
import com.berkant.yaninda.notification.FullScreenIntentCapability
import com.berkant.yaninda.notification.NotificationCancellationResult
import com.berkant.yaninda.notification.NotificationDeliveryResult
import com.berkant.yaninda.notification.ReminderNotifier
import com.berkant.yaninda.sync.SyncWorkRequestResult
import com.berkant.yaninda.sync.SyncWorkScheduler
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.berkant.yaninda.domain.occurrence.DoseGroup
import com.berkant.yaninda.domain.occurrence.NextDoseGroupResult
class ReminderCoordinatorTest {
    private val now = Instant.parse("2026-08-21T12:00:00Z")
    private val timeProvider = FixedTimeProvider(now, ZoneId.of("Europe/Istanbul"))

    @Test
    fun refresh_persistsAndCancelsBeforeScheduling() = runBlocking {
        val actions = mutableListOf<String>()
        val occurrence = scheduledOccurrence(
            id = "occurrence-new",
            scheduledAt = now.plusSeconds(3_600),
        )
        val repository = FakeOccurrenceRepository(
            actions = actions,
            plan = PersistedOccurrencePlan(
                plannedCount = 1,
                insertedCount = 1,
                scheduledOccurrences = listOf(occurrence),
                cancelledOccurrenceIds = listOf("occurrence-old"),
                issues = emptyList(),
            ),
        )
        val scheduler = FakeReminderScheduler(actions = actions)
        val coordinator = ReminderCoordinator(
            occurrenceRepository = repository,
            scheduler = scheduler,
            notifier = FakeReminderNotifier(),
            diagnosticsRepository = FakeReminderDiagnosticsRepository(),
            timeProvider = timeProvider,
            syncWorkScheduler = FakeSyncWorkScheduler(actions),
        )

        val result = coordinator.refreshUpcoming()

        assertEquals(
            listOf(
                "persist",
                "request-sync",
                "cancel:occurrence-old",
                "schedule:occurrence-new",
            ),
            actions,
        )
        assertEquals(1, result.scheduledAlarmCount)
        assertEquals(1, result.cancelledAlarmCount)
        assertEquals(occurrence.scheduledAt, result.nextAlarmAt)
        assertEquals(0, result.failedOperationCount)
    }

    @Test
    fun refresh_persistsButDoesNotScheduleWithoutExactAlarmCapability() = runBlocking {
        val actions = mutableListOf<String>()
        val repository = FakeOccurrenceRepository(
            actions = actions,
            plan = PersistedOccurrencePlan(
                plannedCount = 1,
                insertedCount = 1,
                scheduledOccurrences = listOf(
                    scheduledOccurrence("occurrence-1", now.plusSeconds(3_600))
                ),
                cancelledOccurrenceIds = emptyList(),
                issues = emptyList(),
            ),
        )
        val scheduler = FakeReminderScheduler(
            actions = actions,
            capability = ExactAlarmCapability.USER_ACTION_REQUIRED,
        )
        val coordinator = ReminderCoordinator(
            occurrenceRepository = repository,
            scheduler = scheduler,
            notifier = FakeReminderNotifier(),
            diagnosticsRepository = FakeReminderDiagnosticsRepository(),
            timeProvider = timeProvider,
            syncWorkScheduler = FakeSyncWorkScheduler(actions),
        )

        val result = coordinator.refreshUpcoming()

        assertEquals(listOf("persist", "request-sync"), actions)
        assertEquals(1, result.plannedOccurrenceCount)
        assertEquals(0, result.scheduledAlarmCount)
        assertEquals(ExactAlarmCapability.USER_ACTION_REQUIRED, result.exactAlarmCapability)
    }

    @Test
    fun refresh_schedulesResponseWindowAndRecoversAnOverdueWindow() = runBlocking {
        val currentDue = scheduledOccurrence(
            id = "occurrence-current",
            scheduledAt = now,
        ).copy(
            status = DoseOccurrenceStatus.DUE,
            lastAlertedAt = now,
            nextReminderAt = null,
            version = 2L,
        )
        val overdue = currentDue.copy(
            id = "occurrence-overdue",
            lastAlertedAt = now.minusSeconds(3_600),
        )
        val scheduler = FakeReminderScheduler()
        val coordinator = ReminderCoordinator(
            occurrenceRepository = FakeOccurrenceRepository(
                plan = PersistedOccurrencePlan(
                    plannedCount = 0,
                    insertedCount = 0,
                    scheduledOccurrences = emptyList(),
                    cancelledOccurrenceIds = emptyList(),
                    issues = emptyList(),
                    awaitingResponseOccurrences = listOf(currentDue, overdue),
                ),
            ),
            scheduler = scheduler,
            notifier = FakeReminderNotifier(),
            diagnosticsRepository = FakeReminderDiagnosticsRepository(),
            timeProvider = timeProvider,
            syncWorkScheduler = FakeSyncWorkScheduler(),
        )

        val result = coordinator.refreshUpcoming()

        assertEquals(now.plusSeconds(30 * 60), scheduler.responseTriggers["occurrence-current"])
        assertEquals(now.plusSeconds(5), scheduler.responseTriggers["occurrence-overdue"])
        assertEquals(0, result.failedOperationCount)
    }

    @Test
    fun oneMinuteTest_requiresVisibleNotificationsBeforeScheduling() = runBlocking {
        val scheduler = FakeReminderScheduler()
        val coordinator = ReminderCoordinator(
            occurrenceRepository = FakeOccurrenceRepository(),
            scheduler = scheduler,
            notifier = FakeReminderNotifier(
                notificationCapability = NotificationCapability.RUNTIME_PERMISSION_REQUIRED
            ),
            diagnosticsRepository = FakeReminderDiagnosticsRepository(),
            timeProvider = timeProvider,
            syncWorkScheduler = FakeSyncWorkScheduler(),
        )

        val result = coordinator.scheduleOneMinuteTest()

        assertTrue(result is ReminderTestResult.NotificationUnavailable)
        assertEquals(null, scheduler.testTriggerAt)
    }

    @Test
    fun oneMinuteTest_schedulesExactlySixtySecondsAfterInjectedTime() = runBlocking {
        val scheduler = FakeReminderScheduler()
        val coordinator = ReminderCoordinator(
            occurrenceRepository = FakeOccurrenceRepository(),
            scheduler = scheduler,
            notifier = FakeReminderNotifier(),
            diagnosticsRepository = FakeReminderDiagnosticsRepository(),
            timeProvider = timeProvider,
            syncWorkScheduler = FakeSyncWorkScheduler(),
        )

        val result = coordinator.scheduleOneMinuteTest()

        assertEquals(now.plusSeconds(60), scheduler.testTriggerAt)
        assertEquals(
            ReminderTestResult.Scheduled(now.plusSeconds(60)),
            result,
        )
        assertEquals(now.plusSeconds(60), coordinator.status.value.testAlarmScheduledAt)
    }

    @Test
    fun snooze_persistsNextReminderBeforeSchedulingAndThenCancelsNotification() = runBlocking {
        val actions = mutableListOf<String>()
        val dueOccurrence = scheduledOccurrence(
            id = "occurrence-1",
            scheduledAt = now,
        ).copy(
            status = DoseOccurrenceStatus.DUE,
            lastAlertedAt = now,
            nextReminderAt = null,
            version = 2L,
        )
        val coordinator = ReminderCoordinator(
            occurrenceRepository = FakeOccurrenceRepository(
                actions = actions,
                occurrence = dueOccurrence,
            ),
            scheduler = FakeReminderScheduler(actions = actions),
            notifier = FakeReminderNotifier(actions = actions),
            diagnosticsRepository = FakeReminderDiagnosticsRepository(),
            timeProvider = timeProvider,
            syncWorkScheduler = FakeSyncWorkScheduler(actions),
        )

        val result = coordinator.snoozeOccurrence(
            occurrenceId = dueOccurrence.id,
            snoozeMinutes = 10,
            maxSnoozes = 2,
        )

        assertEquals(ReminderSnoozeResult.Scheduled(now.plusSeconds(600)), result)
        assertEquals(
            listOf(
                "persist-event:occurrence-1",
                "request-sync",
                "cancel-response:occurrence-1",
                "schedule:occurrence-1",
                "cancel-notification:occurrence-1",
            ),
            actions,
        )
    }

    @Test
    fun acknowledgeTaken_persistsThenRequestsSyncBeforeAlarmCleanup() = runBlocking {
        val actions = mutableListOf<String>()
        val dueOccurrence = scheduledOccurrence(
            id = "occurrence-ack",
            scheduledAt = now,
        ).copy(
            status = DoseOccurrenceStatus.DUE,
            lastAlertedAt = now,
            nextReminderAt = null,
            version = 2L,
        )
        val coordinator = ReminderCoordinator(
            occurrenceRepository = FakeOccurrenceRepository(
                actions = actions,
                occurrence = dueOccurrence,
            ),
            scheduler = FakeReminderScheduler(actions = actions),
            notifier = FakeReminderNotifier(actions = actions),
            diagnosticsRepository = FakeReminderDiagnosticsRepository(),
            timeProvider = timeProvider,
            syncWorkScheduler = FakeSyncWorkScheduler(actions),
        )

        val result = coordinator.acknowledgeTaken(dueOccurrence.id)

        assertTrue(result.stateChanged)
        assertEquals(DoseOccurrenceStatus.ACKNOWLEDGED_TAKEN, result.occurrence.status)
        assertEquals(
            listOf(
                "persist-event:occurrence-ack",
                "request-sync",
                "cancel:occurrence-ack",
                "cancel-notification:occurrence-ack",
            ),
            actions,
        )
    }

    @Test
    fun alarmDeliveryDiagnostics_arePersistedAndRestoredAfterProcessRecreation() = runBlocking {
        val diagnosticsRepository = FakeReminderDiagnosticsRepository()
        val firstCoordinator = ReminderCoordinator(
            occurrenceRepository = FakeOccurrenceRepository(),
            scheduler = FakeReminderScheduler(),
            notifier = FakeReminderNotifier(),
            diagnosticsRepository = diagnosticsRepository,
            timeProvider = timeProvider,
            syncWorkScheduler = FakeSyncWorkScheduler(),
        )
        val firedAt = now.plusSeconds(30)

        firstCoordinator.recordMedicationAlarmDelivery(
            firedAt = firedAt,
            result = NotificationDeliveryResult.Delivered,
        )

        val recreatedCoordinator = ReminderCoordinator(
            occurrenceRepository = FakeOccurrenceRepository(),
            scheduler = FakeReminderScheduler(),
            notifier = FakeReminderNotifier(),
            diagnosticsRepository = diagnosticsRepository,
            timeProvider = timeProvider,
            syncWorkScheduler = FakeSyncWorkScheduler(),
        )
        val restoredStatus = recreatedCoordinator.refreshUpcoming()

        assertEquals(
            AlarmDeliveryDiagnostic(firedAt, AlarmDeliveryOutcome.DELIVERED),
            restoredStatus.diagnostics.lastMedicationAlarm,
        )
        assertEquals(firedAt, restoredStatus.lastAlarmFiredAt)
        assertEquals(false, restoredStatus.diagnosticsStorageIssue)
    }

    private fun scheduledOccurrence(
        id: String,
        scheduledAt: Instant,
    ): DoseOccurrence = DoseOccurrence(
        id = id,
        medicationId = "medication-1",
        scheduleId = "schedule-1",
        scheduledAt = scheduledAt,
        status = DoseOccurrenceStatus.SCHEDULED,
        acknowledgedAt = null,
        acknowledgementActor = null,
        snoozeCount = 0,
        lastAlertedAt = null,
        nextReminderAt = scheduledAt,
        createdAt = now,
        updatedAt = now,
        version = 1L,
    )
}

private class FakeOccurrenceRepository(
    private val actions: MutableList<String> = mutableListOf(),
    private val plan: PersistedOccurrencePlan = PersistedOccurrencePlan(
        plannedCount = 0,
        insertedCount = 0,
        scheduledOccurrences = emptyList(),
        cancelledOccurrenceIds = emptyList(),
        issues = emptyList(),
    ),
    occurrence: DoseOccurrence? = null,
) : DoseOccurrenceRepository {
    private var storedOccurrence = occurrence
    private val stateMachine = DoseOccurrenceStateMachine()
    override suspend fun getDoseGroupForOccurrence(
        occurrenceId: String,
    ): DoseGroup? = null

    override suspend fun getOccurrencesForDoseGroup(
        occurrenceId: String,
    ): List<DoseOccurrence> =
        storedOccurrence
            ?.takeIf {
                it.id == occurrenceId
            }
            ?.let(::listOf)
            .orEmpty()

    override suspend fun markDoseGroupReminderDue(
        occurrenceId: String,
        firedAt: Instant,
    ): List<DoseOccurrenceTransition> =
        listOf(
            markReminderDue(
                occurrenceId = occurrenceId,
                firedAt = firedAt,
            )
        )

    override suspend fun applyEventToDoseGroup(
        occurrenceId: String,
        event: DoseOccurrenceEvent,
    ): List<DoseOccurrenceTransition> =
        listOf(
            applyEvent(
                occurrenceId = occurrenceId,
                event = event,
            )
        )
    override fun observeActionable(fromInclusive: Instant): Flow<List<DoseOccurrence>> =
        flowOf(emptyList())

    override suspend fun get(occurrenceId: String): DoseOccurrence? =
        storedOccurrence?.takeIf { it.id == occurrenceId }

    override suspend fun calculateNextOccurrence(): NextOccurrenceResult =
        NextOccurrenceResult(
            occurrence = null,
            issues = emptyList(),
        )

    override suspend fun calculateNextDoseGroup(): NextDoseGroupResult =
        NextDoseGroupResult(
            group = null,
            issues = emptyList(),
        )

    override suspend fun persistPlan(
        window: OccurrencePlanningWindow,
    ): PersistedOccurrencePlan {
        actions += "persist"
        return plan
    }

    override suspend fun markReminderDue(
        occurrenceId: String,
        firedAt: Instant,
    ): DoseOccurrenceTransition = error("Not used by this test.")

    override suspend fun applyEvent(
        occurrenceId: String,
        event: DoseOccurrenceEvent,
    ): DoseOccurrenceTransition {
        actions += "persist-event:$occurrenceId"
        val current = checkNotNull(storedOccurrence)
        val updated = stateMachine.transition(current, event)
        storedOccurrence = updated
        return DoseOccurrenceTransition(
            occurrence = updated,
            stateChanged = updated != current,
        )
    }
}

private class FakeReminderScheduler(
    private val actions: MutableList<String> = mutableListOf(),
    private val capability: ExactAlarmCapability = ExactAlarmCapability.AVAILABLE,
) : ReminderScheduler {
    var testTriggerAt: Instant? = null
        private set
    val responseTriggers = mutableMapOf<String, Instant>()

    override fun exactAlarmCapability(): ExactAlarmCapability = capability

    override fun scheduleOccurrence(
        occurrenceId: String,
        triggerAt: Instant,
    ): AlarmSchedulingResult {
        actions += "schedule:$occurrenceId"
        return AlarmSchedulingResult.Scheduled
    }

    override fun cancelOccurrence(occurrenceId: String): AlarmCancellationResult {
        actions += "cancel:$occurrenceId"
        return AlarmCancellationResult.Cancelled
    }

    override fun scheduleResponseWindow(
        occurrenceId: String,
        triggerAt: Instant,
    ): AlarmSchedulingResult {
        actions += "schedule-response:$occurrenceId"
        responseTriggers[occurrenceId] = triggerAt
        return AlarmSchedulingResult.Scheduled
    }

    override fun cancelResponseWindow(occurrenceId: String): AlarmCancellationResult {
        actions += "cancel-response:$occurrenceId"
        responseTriggers.remove(occurrenceId)
        return AlarmCancellationResult.Cancelled
    }

    override fun scheduleTestAlarm(triggerAt: Instant): AlarmSchedulingResult {
        testTriggerAt = triggerAt
        return AlarmSchedulingResult.Scheduled
    }

    override fun cancelTestAlarm(): AlarmCancellationResult = AlarmCancellationResult.Cancelled
}

private class FakeReminderNotifier(
    private val notificationCapability: NotificationCapability = NotificationCapability.AVAILABLE,
    private val actions: MutableList<String> = mutableListOf(),
) : ReminderNotifier {
    override fun ensureChannel() = Unit

    override fun capability(): NotificationCapability = notificationCapability

    override fun fullScreenIntentCapability(): FullScreenIntentCapability =
        FullScreenIntentCapability.AVAILABLE

    override fun showMedicationReminder(occurrenceId: String): NotificationDeliveryResult =
        NotificationDeliveryResult.Delivered

    override fun showTestReminder(): NotificationDeliveryResult =
        NotificationDeliveryResult.Delivered

    override fun cancelMedicationReminder(
        occurrenceId: String,
    ): NotificationCancellationResult {
        actions += "cancel-notification:$occurrenceId"
        return NotificationCancellationResult.CANCELLED
    }

    override fun cancelTestReminder(): NotificationCancellationResult =
        NotificationCancellationResult.CANCELLED
}

private class FakeSyncWorkScheduler(
    private val actions: MutableList<String> = mutableListOf(),
    private val result: SyncWorkRequestResult = SyncWorkRequestResult.ENQUEUED,
) : SyncWorkScheduler {
    override fun requestSync(): SyncWorkRequestResult {
        actions += "request-sync"
        return result
    }
}

private class FakeReminderDiagnosticsRepository(
    private var snapshot: ReminderDiagnosticsSnapshot = ReminderDiagnosticsSnapshot(),
) : ReminderDiagnosticsRepository {
    override suspend fun read(): ReminderDiagnosticsSnapshot = snapshot

    override suspend fun recordMedicationAlarm(diagnostic: AlarmDeliveryDiagnostic) {
        snapshot = snapshot.copy(lastMedicationAlarm = diagnostic)
    }

    override suspend fun recordTestAlarm(diagnostic: AlarmDeliveryDiagnostic) {
        snapshot = snapshot.copy(lastTestAlarm = diagnostic)
    }
}

private data class FixedTimeProvider(
    private val instant: Instant,
    private val zoneId: ZoneId,
) : TimeProvider {
    override fun now(): Instant = instant

    override fun currentZoneId(): ZoneId = zoneId
}
