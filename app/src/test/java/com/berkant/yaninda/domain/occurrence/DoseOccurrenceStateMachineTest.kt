package com.berkant.yaninda.domain.occurrence

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class DoseOccurrenceStateMachineTest {
    private val stateMachine = DoseOccurrenceStateMachine()

    @Test
    fun scheduledDueSnoozedDueAcknowledged_isValidStatePath() {
        val dueAt = Instant.parse("2026-08-21T17:00:00Z")
        val snoozedAt = dueAt.plusSeconds(30)
        val alertedAgainAt = dueAt.plusSeconds(10 * 60)
        val acknowledgedAt = alertedAgainAt.plusSeconds(15)

        val due = stateMachine.transition(
            occurrence(),
            DoseOccurrenceEvent.ReminderDue(dueAt),
        )
        val snoozed = stateMachine.transition(
            due,
            DoseOccurrenceEvent.SnoozeRequested(
                occurredAt = snoozedAt,
                remindAt = alertedAgainAt,
                maxSnoozes = 1,
            ),
        )
        val dueAgain = stateMachine.transition(
            snoozed,
            DoseOccurrenceEvent.ReminderDue(alertedAgainAt),
        )
        val acknowledged = stateMachine.transition(
            dueAgain,
            DoseOccurrenceEvent.TakenAcknowledged(
                acknowledgedAt,
                AcknowledgementActor.GRANDFATHER,
            ),
        )

        assertEquals(DoseOccurrenceStatus.ACKNOWLEDGED_TAKEN, acknowledged.status)
        assertEquals(acknowledgedAt, acknowledged.acknowledgedAt)
        assertEquals(AcknowledgementActor.GRANDFATHER, acknowledged.acknowledgementActor)
        assertEquals(1, acknowledged.snoozeCount)
        assertEquals(alertedAgainAt, acknowledged.lastAlertedAt)
        assertNull(acknowledged.nextReminderAt)
        assertEquals(5L, acknowledged.version)
    }

    @Test
    fun duplicateDueAndAcknowledgementEvents_areIdempotent() {
        val dueAt = Instant.parse("2026-08-21T17:00:00Z")
        val due = stateMachine.transition(
            occurrence(),
            DoseOccurrenceEvent.ReminderDue(dueAt),
        )
        val duplicateDue = stateMachine.transition(
            due,
            DoseOccurrenceEvent.ReminderDue(dueAt.plusSeconds(1)),
        )
        val acknowledged = stateMachine.transition(
            duplicateDue,
            DoseOccurrenceEvent.TakenAcknowledged(
                dueAt.plusSeconds(10),
                AcknowledgementActor.GRANDFATHER,
            ),
        )
        val duplicateAcknowledgement = stateMachine.transition(
            acknowledged,
            DoseOccurrenceEvent.TakenAcknowledged(
                dueAt.plusSeconds(20),
                AcknowledgementActor.CAREGIVER,
            ),
        )

        assertEquals(due, duplicateDue)
        assertEquals(acknowledged, duplicateAcknowledgement)
    }

    @Test
    fun noConfirmation_doesNotOverwriteAcknowledgement() {
        val dueAt = Instant.parse("2026-08-21T17:00:00Z")
        val due = stateMachine.transition(
            occurrence(),
            DoseOccurrenceEvent.ReminderDue(dueAt),
        )
        val acknowledged = stateMachine.transition(
            due,
            DoseOccurrenceEvent.TakenAcknowledged(
                dueAt.plusSeconds(10),
                AcknowledgementActor.GRANDFATHER,
            ),
        )

        val afterTimer = stateMachine.transition(
            acknowledged,
            DoseOccurrenceEvent.ResponseWindowElapsed(dueAt.plusSeconds(600)),
        )

        assertEquals(acknowledged, afterTimer)
    }

    @Test
    fun lateExplicitAcknowledgement_canReplaceNoConfirmation() {
        val dueAt = Instant.parse("2026-08-21T17:00:00Z")
        val due = stateMachine.transition(
            occurrence(),
            DoseOccurrenceEvent.ReminderDue(dueAt),
        )
        val noConfirmation = stateMachine.transition(
            due,
            DoseOccurrenceEvent.ResponseWindowElapsed(dueAt.plusSeconds(600)),
        )

        val acknowledged = stateMachine.transition(
            noConfirmation,
            DoseOccurrenceEvent.TakenAcknowledged(
                dueAt.plusSeconds(700),
                AcknowledgementActor.CAREGIVER,
            ),
        )

        assertEquals(DoseOccurrenceStatus.ACKNOWLEDGED_TAKEN, acknowledged.status)
        assertEquals(AcknowledgementActor.CAREGIVER, acknowledged.acknowledgementActor)
    }

    @Test
    fun snoozeBeyondConfiguredMaximum_isRejected() {
        val dueAt = Instant.parse("2026-08-21T17:00:00Z")
        val due = occurrence().copy(
            status = DoseOccurrenceStatus.DUE,
            snoozeCount = 1,
            lastAlertedAt = dueAt,
            nextReminderAt = null,
        )

        assertThrows(InvalidDoseOccurrenceTransition::class.java) {
            stateMachine.transition(
                due,
                DoseOccurrenceEvent.SnoozeRequested(
                    occurredAt = dueAt.plusSeconds(10),
                    remindAt = dueAt.plusSeconds(610),
                    maxSnoozes = 1,
                ),
            )
        }
    }

    @Test
    fun acknowledgementBeforeReminderIsDue_isRejected() {
        assertThrows(InvalidDoseOccurrenceTransition::class.java) {
            stateMachine.transition(
                occurrence(),
                DoseOccurrenceEvent.TakenAcknowledged(
                    Instant.parse("2026-08-21T16:59:00Z"),
                    AcknowledgementActor.GRANDFATHER,
                ),
            )
        }
    }

    @Test
    fun futureScheduledOccurrence_canBeCancelledWithoutAcknowledgementData() {
        val cancelled = stateMachine.transition(
            occurrence(),
            DoseOccurrenceEvent.Cancelled(Instant.parse("2026-08-21T16:00:00Z")),
        )

        assertEquals(DoseOccurrenceStatus.CANCELLED, cancelled.status)
        assertNull(cancelled.acknowledgedAt)
        assertNull(cancelled.acknowledgementActor)
    }

    private fun occurrence(): DoseOccurrence {
        val createdAt = Instant.parse("2026-08-21T16:00:00Z")
        return DoseOccurrence(
            id = "occurrence-1",
            medicationId = "medication-1",
            scheduleId = "schedule-1",
            scheduledAt = Instant.parse("2026-08-21T17:00:00Z"),
            status = DoseOccurrenceStatus.SCHEDULED,
            acknowledgedAt = null,
            acknowledgementActor = null,
            snoozeCount = 0,
            lastAlertedAt = null,
            nextReminderAt = Instant.parse("2026-08-21T17:00:00Z"),
            createdAt = createdAt,
            updatedAt = createdAt,
            version = 1L,
        )
    }
}
