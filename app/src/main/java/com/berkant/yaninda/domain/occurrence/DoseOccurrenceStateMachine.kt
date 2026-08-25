package com.berkant.yaninda.domain.occurrence

class InvalidDoseOccurrenceTransition(message: String) : IllegalStateException(message)

class DoseOccurrenceStateMachine {
    fun transition(
        current: DoseOccurrence,
        event: DoseOccurrenceEvent,
    ): DoseOccurrence {
        validate(current)
        val next = when (event) {
            is DoseOccurrenceEvent.ReminderDue -> reminderDue(current, event)
            is DoseOccurrenceEvent.SnoozeRequested -> snooze(current, event)
            is DoseOccurrenceEvent.AutomaticRetryScheduled -> automaticRetry(current, event)
            is DoseOccurrenceEvent.TakenAcknowledged -> acknowledge(current, event)
            is DoseOccurrenceEvent.ResponseWindowElapsed -> noConfirmation(current, event)
            is DoseOccurrenceEvent.Cancelled -> cancel(current)
        }
        if (next == current) return current
        return next.copy(
            updatedAt = event.occurredAt,
            version = current.version + 1L,
        ).also(::validate)
    }

    private fun reminderDue(
        current: DoseOccurrence,
        event: DoseOccurrenceEvent.ReminderDue,
    ): DoseOccurrence = when (current.status) {
        DoseOccurrenceStatus.SCHEDULED,
        DoseOccurrenceStatus.SNOOZED,
        -> current.copy(
            status = DoseOccurrenceStatus.DUE,
            lastAlertedAt = event.occurredAt,
            nextReminderAt = null,
        )

        DoseOccurrenceStatus.DUE -> {
            /*
             * DUE + nextReminderAt means that the first 40-second window
             * elapsed and one automatic retry is waiting to fire.
             *
             * A duplicate delivery while an alarm is already actively DUE
             * has nextReminderAt == null and stays idempotent.
             */
            if (
                current.nextReminderAt != null &&
                current.automaticRetryCount > 0
            ) {
                current.copy(
                    lastAlertedAt = event.occurredAt,
                    nextReminderAt = null,
                )
            } else {
                current
            }
        }

        DoseOccurrenceStatus.ACKNOWLEDGED_TAKEN,
        DoseOccurrenceStatus.NO_CONFIRMATION,
        DoseOccurrenceStatus.CANCELLED,
        -> current
    }

    private fun snooze(
        current: DoseOccurrence,
        event: DoseOccurrenceEvent.SnoozeRequested,
    ): DoseOccurrence = when (current.status) {
        DoseOccurrenceStatus.DUE -> {
            if (event.maxSnoozes <= 0 || current.snoozeCount >= event.maxSnoozes) {
                invalid(current, event)
            }
            current.copy(
                status = DoseOccurrenceStatus.SNOOZED,
                snoozeCount = current.snoozeCount + 1,
                nextReminderAt = event.remindAt,
            )
        }

        DoseOccurrenceStatus.SNOOZED,
        DoseOccurrenceStatus.ACKNOWLEDGED_TAKEN,
        DoseOccurrenceStatus.NO_CONFIRMATION,
        DoseOccurrenceStatus.CANCELLED,
        -> current

        DoseOccurrenceStatus.SCHEDULED -> invalid(current, event)
    }

    private fun automaticRetry(
        current: DoseOccurrence,
        event: DoseOccurrenceEvent.AutomaticRetryScheduled,
    ): DoseOccurrence = when (current.status) {
        DoseOccurrenceStatus.DUE -> {
            when {
                /*
                 * Duplicate response-window delivery after the retry has
                 * already been persisted must be harmless.
                 */
                current.nextReminderAt != null -> current

                current.automaticRetryCount >= event.maxAutomaticRetries -> current

                else -> current.copy(
                    automaticRetryCount = current.automaticRetryCount + 1,
                    nextReminderAt = event.remindAt,
                )
            }
        }

        DoseOccurrenceStatus.SNOOZED,
        DoseOccurrenceStatus.ACKNOWLEDGED_TAKEN,
        DoseOccurrenceStatus.NO_CONFIRMATION,
        DoseOccurrenceStatus.CANCELLED,
        -> current

        DoseOccurrenceStatus.SCHEDULED -> invalid(current, event)
    }

    private fun acknowledge(
        current: DoseOccurrence,
        event: DoseOccurrenceEvent.TakenAcknowledged,
    ): DoseOccurrence = when (current.status) {
        DoseOccurrenceStatus.DUE,
        DoseOccurrenceStatus.SNOOZED,
        DoseOccurrenceStatus.NO_CONFIRMATION,
        -> current.copy(
            status = DoseOccurrenceStatus.ACKNOWLEDGED_TAKEN,
            acknowledgedAt = event.occurredAt,
            acknowledgementActor = event.actor,
            nextReminderAt = null,
        )

        DoseOccurrenceStatus.ACKNOWLEDGED_TAKEN -> current
        DoseOccurrenceStatus.SCHEDULED,
        DoseOccurrenceStatus.CANCELLED,
        -> invalid(current, event)
    }

    private fun noConfirmation(
        current: DoseOccurrence,
        event: DoseOccurrenceEvent.ResponseWindowElapsed,
    ): DoseOccurrence = when (current.status) {
        DoseOccurrenceStatus.DUE,
        DoseOccurrenceStatus.SNOOZED,
        -> current.copy(
            status = DoseOccurrenceStatus.NO_CONFIRMATION,
            nextReminderAt = null,
        )

        DoseOccurrenceStatus.NO_CONFIRMATION,
        DoseOccurrenceStatus.ACKNOWLEDGED_TAKEN,
        DoseOccurrenceStatus.CANCELLED,
        -> current

        DoseOccurrenceStatus.SCHEDULED -> invalid(current, event)
    }

    private fun cancel(current: DoseOccurrence): DoseOccurrence = when (current.status) {
        DoseOccurrenceStatus.SCHEDULED,
        DoseOccurrenceStatus.SNOOZED,
        -> current.copy(
            status = DoseOccurrenceStatus.CANCELLED,
            nextReminderAt = null,
        )

        DoseOccurrenceStatus.CANCELLED -> current
        DoseOccurrenceStatus.DUE,
        DoseOccurrenceStatus.ACKNOWLEDGED_TAKEN,
        DoseOccurrenceStatus.NO_CONFIRMATION,
        -> throw InvalidDoseOccurrenceTransition(
            "Only a future scheduled or snoozed occurrence can be cancelled."
        )
    }

    private fun invalid(
        current: DoseOccurrence,
        event: DoseOccurrenceEvent,
    ): Nothing = throw InvalidDoseOccurrenceTransition(
        "${event::class.simpleName} is invalid from ${current.status}."
    )

    private fun validate(occurrence: DoseOccurrence) {
        require(occurrence.snoozeCount >= 0) { "Snooze count cannot be negative." }
        require(occurrence.automaticRetryCount >= 0) {
            "Automatic retry count cannot be negative."
        }
        require(occurrence.version >= 1L) { "Occurrence version must be positive." }
        when (occurrence.status) {
            DoseOccurrenceStatus.SCHEDULED -> require(
                occurrence.nextReminderAt == occurrence.scheduledAt
            ) {
                "A scheduled occurrence must point to its scheduled reminder time."
            }

            DoseOccurrenceStatus.SNOOZED -> {
                val nextReminderAt = requireNotNull(occurrence.nextReminderAt) {
                    "A snoozed occurrence needs its next reminder time."
                }
                require(nextReminderAt > occurrence.updatedAt) {
                    "A snoozed occurrence must point to a future reminder time."
                }
            }

            DoseOccurrenceStatus.DUE -> {
                occurrence.nextReminderAt?.let { nextReminderAt ->
                    require(occurrence.automaticRetryCount > 0) {
                        "A waiting automatic retry must have a positive retry count."
                    }
                    require(nextReminderAt > occurrence.updatedAt) {
                        "An automatic retry must point to a future reminder time."
                    }
                }
            }

            DoseOccurrenceStatus.ACKNOWLEDGED_TAKEN,
            DoseOccurrenceStatus.NO_CONFIRMATION,
            DoseOccurrenceStatus.CANCELLED,
            -> require(occurrence.nextReminderAt == null) {
                "A completed occurrence cannot have a next reminder time."
            }
        }
        if (occurrence.status == DoseOccurrenceStatus.ACKNOWLEDGED_TAKEN) {
            requireNotNull(occurrence.acknowledgedAt) {
                "An acknowledged occurrence needs an acknowledgement timestamp."
            }
            requireNotNull(occurrence.acknowledgementActor) {
                "An acknowledged occurrence needs an acknowledgement actor."
            }
        } else {
            require(occurrence.acknowledgedAt == null) {
                "Only an acknowledged occurrence can have an acknowledgement timestamp."
            }
            require(occurrence.acknowledgementActor == null) {
                "Only an acknowledged occurrence can have an acknowledgement actor."
            }
        }
    }
}
