package com.berkant.yaninda.domain.occurrence

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

enum class DoseOccurrenceStatus {
    SCHEDULED,
    DUE,
    SNOOZED,
    ACKNOWLEDGED_TAKEN,
    NO_CONFIRMATION,
    CANCELLED,
}

enum class AcknowledgementActor {
    GRANDFATHER,
    CAREGIVER,
}

data class DoseOccurrence(
    val id: String,
    val medicationId: String,
    val scheduleId: String,
    val scheduledAt: Instant,
    val status: DoseOccurrenceStatus,
    val acknowledgedAt: Instant?,
    val acknowledgementActor: AcknowledgementActor?,
    val snoozeCount: Int,
    val lastAlertedAt: Instant?,
    val nextReminderAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long,
)

data class PlannedDoseOccurrence(
    val id: String,
    val medicationId: String,
    val scheduleId: String,
    val scheduledAt: Instant,
)

data class OccurrencePlanningWindow(
    val startInclusive: Instant,
    val endExclusive: Instant,
    val zoneId: ZoneId,
) {
    init {
        require(endExclusive > startInclusive) {
            "Occurrence planning window must have a positive duration."
        }
    }
}

enum class OccurrencePlanningIssueType {
    NONEXISTENT_LOCAL_TIME,
    AMBIGUOUS_LOCAL_TIME_EARLIER_OFFSET_USED,
}

data class OccurrencePlanningIssue(
    val medicationId: String,
    val scheduleId: String,
    val localDateTime: LocalDateTime,
    val zoneId: ZoneId,
    val type: OccurrencePlanningIssueType,
)

data class OccurrencePlan(
    val occurrences: List<PlannedDoseOccurrence>,
    val issues: List<OccurrencePlanningIssue>,
)

data class NextOccurrenceResult(
    val occurrence: PlannedDoseOccurrence?,
    val issues: List<OccurrencePlanningIssue>,
)

sealed interface DoseOccurrenceEvent {
    val occurredAt: Instant

    data class ReminderDue(
        override val occurredAt: Instant,
    ) : DoseOccurrenceEvent

    data class SnoozeRequested(
        override val occurredAt: Instant,
        val remindAt: Instant,
        val maxSnoozes: Int,
    ) : DoseOccurrenceEvent {
        init {
            require(remindAt > occurredAt) {
                "A snoozed reminder must be scheduled after the request time."
            }
        }
    }

    data class TakenAcknowledged(
        override val occurredAt: Instant,
        val actor: AcknowledgementActor,
    ) : DoseOccurrenceEvent

    data class ResponseWindowElapsed(
        override val occurredAt: Instant,
    ) : DoseOccurrenceEvent

    data class Cancelled(
        override val occurredAt: Instant,
    ) : DoseOccurrenceEvent
}
