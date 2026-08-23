package com.berkant.yaninda.domain.medication

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

enum class MedicationScheduleType {
    FIXED_ONLY,
}

data class Medication(
    val id: String,
    val displayName: String,
    val dosageText: String,
    val instructionText: String,
    val photoUri: String?,
    val scheduleType: MedicationScheduleType,
    val active: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long,
)

data class MedicationSchedule(
    val id: String,
    val medicationId: String,
    val localTime: LocalTime,
    val daysOfWeek: Set<DayOfWeek>,
    val validFrom: LocalDate,
    val validUntil: LocalDate?,
    val snoozeEnabled: Boolean,
    val snoozeMinutes: Int,
    val maxSnoozes: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long,
)

data class MedicationConfiguration(
    val medication: Medication,
    val schedules: List<MedicationSchedule>,
)

data class ScheduleDraft(
    val id: String? = null,
    val timeText: String,
)

data class MedicationDraft(
    val medicationId: String? = null,
    val schedules: List<ScheduleDraft>,
    val displayName: String,
    val dosageText: String,
    val instructionText: String,
    val daysOfWeek: Set<DayOfWeek>,
    val snoozeEnabled: Boolean,
    val snoozeMinutesText: String,
    val maxSnoozesText: String,
    val fixedScheduleConfirmed: Boolean,
    val instructionsConfirmed: Boolean,
)

data class ValidatedScheduleDraft(
    val id: String?,
    val localTime: LocalTime,
)

data class ValidatedMedicationDraft(
    val medicationId: String?,
    val schedules: List<ValidatedScheduleDraft>,
    val displayName: String,
    val dosageText: String,
    val instructionText: String,
    val daysOfWeek: Set<DayOfWeek>,
    val snoozeEnabled: Boolean,
    val snoozeMinutes: Int,
    val maxSnoozes: Int,
)
