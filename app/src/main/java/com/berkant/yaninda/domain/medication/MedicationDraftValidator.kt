package com.berkant.yaninda.domain.medication

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.time.temporal.ChronoField
import java.util.Locale

enum class MedicationDraftError {
    FIXED_SCHEDULE_NOT_CONFIRMED,
    INSTRUCTIONS_NOT_CONFIRMED,
    NAME_REQUIRED,
    NAME_TOO_LONG,
    DOSAGE_REQUIRED,
    DOSAGE_TOO_LONG,
    INSTRUCTION_REQUIRED,
    INSTRUCTION_TOO_LONG,
    TIME_REQUIRED,
    INVALID_TIME,
    DUPLICATE_TIME,
    DAY_REQUIRED,
    SNOOZE_MINUTES_INVALID,
    MAX_SNOOZES_INVALID,
}

data class MedicationDraftValidation(
    val value: ValidatedMedicationDraft?,
    val errors: Set<MedicationDraftError>,
) {
    val isValid: Boolean = value != null && errors.isEmpty()
}

class MedicationDraftValidator {
    fun validate(draft: MedicationDraft): MedicationDraftValidation {
        val errors = linkedSetOf<MedicationDraftError>()
        val name = draft.displayName.trim()
        val dosage = draft.dosageText.trim()
        val instruction = draft.instructionText.trim()

        if (!draft.fixedScheduleConfirmed) {
            errors += MedicationDraftError.FIXED_SCHEDULE_NOT_CONFIRMED
        }
        if (!draft.instructionsConfirmed) {
            errors += MedicationDraftError.INSTRUCTIONS_NOT_CONFIRMED
        }
        validateText(name, NAME_MAX_LENGTH, MedicationDraftError.NAME_REQUIRED, MedicationDraftError.NAME_TOO_LONG, errors)
        validateText(
            dosage,
            DOSAGE_MAX_LENGTH,
            MedicationDraftError.DOSAGE_REQUIRED,
            MedicationDraftError.DOSAGE_TOO_LONG,
            errors,
        )
        validateText(
            instruction,
            INSTRUCTION_MAX_LENGTH,
            MedicationDraftError.INSTRUCTION_REQUIRED,
            MedicationDraftError.INSTRUCTION_TOO_LONG,
            errors,
        )

        if (draft.daysOfWeek.isEmpty()) {
            errors += MedicationDraftError.DAY_REQUIRED
        }

        val validatedSchedules = draft.schedules.mapNotNull { schedule ->
            val timeText = schedule.timeText.trim()
            if (timeText.isEmpty()) {
                errors += MedicationDraftError.TIME_REQUIRED
                null
            } else {
                val time = parseTimeOrNull(timeText)
                if (time == null) {
                    errors += MedicationDraftError.INVALID_TIME
                    null
                } else {
                    ValidatedScheduleDraft(id = schedule.id, localTime = time)
                }
            }
        }
        if (draft.schedules.isEmpty()) {
            errors += MedicationDraftError.TIME_REQUIRED
        }
        if (validatedSchedules.map { it.localTime }.distinct().size != validatedSchedules.size) {
            errors += MedicationDraftError.DUPLICATE_TIME
        }

        val snoozeMinutes = draft.snoozeMinutesText.toIntOrNull()
        val maxSnoozes = draft.maxSnoozesText.toIntOrNull()
        if (draft.snoozeEnabled && snoozeMinutes !in MIN_SNOOZE_MINUTES..MAX_SNOOZE_MINUTES) {
            errors += MedicationDraftError.SNOOZE_MINUTES_INVALID
        }
        if (draft.snoozeEnabled && maxSnoozes !in MIN_SNOOZE_COUNT..MAX_SNOOZE_COUNT) {
            errors += MedicationDraftError.MAX_SNOOZES_INVALID
        }

        if (errors.isNotEmpty()) {
            return MedicationDraftValidation(value = null, errors = errors)
        }

        return MedicationDraftValidation(
            value = ValidatedMedicationDraft(
                medicationId = draft.medicationId,
                schedules = validatedSchedules,
                displayName = name,
                dosageText = dosage,
                instructionText = instruction,
                daysOfWeek = draft.daysOfWeek,
                snoozeEnabled = draft.snoozeEnabled,
                snoozeMinutes = snoozeMinutes ?: DEFAULT_SNOOZE_MINUTES,
                maxSnoozes = maxSnoozes ?: DEFAULT_MAX_SNOOZES,
            ),
            errors = emptySet(),
        )
    }

    private fun validateText(
        value: String,
        maxLength: Int,
        requiredError: MedicationDraftError,
        lengthError: MedicationDraftError,
        errors: MutableSet<MedicationDraftError>,
    ) {
        if (value.isEmpty()) errors += requiredError
        if (value.length > maxLength) errors += lengthError
    }

    companion object {
        const val NAME_MAX_LENGTH = 80
        const val DOSAGE_MAX_LENGTH = 120
        const val INSTRUCTION_MAX_LENGTH = 200
        const val MIN_SNOOZE_MINUTES = 1
        const val MAX_SNOOZE_MINUTES = 60
        const val MIN_SNOOZE_COUNT = 1
        const val MAX_SNOOZE_COUNT = 5
        const val DEFAULT_SNOOZE_MINUTES = 10
        const val DEFAULT_MAX_SNOOZES = 1

        private val timeFormatter = DateTimeFormatterBuilder()
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .toFormatter(Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT)

        fun parseTimeOrNull(value: String): LocalTime? = try {
            LocalTime.parse(value, timeFormatter)
        } catch (_: DateTimeParseException) {
            null
        }

        fun formatTime(value: LocalTime): String = value.format(timeFormatter)
    }
}

object DayOfWeekMask {
    fun encode(days: Set<DayOfWeek>): Int = days.fold(0) { mask, day ->
        mask or (1 shl (day.value - 1))
    }

    fun decode(mask: Int): Set<DayOfWeek> = DayOfWeek.entries
        .filterTo(linkedSetOf()) { day -> mask and (1 shl (day.value - 1)) != 0 }
}
