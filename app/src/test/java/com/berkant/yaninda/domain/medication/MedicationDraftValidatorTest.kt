package com.berkant.yaninda.domain.medication

import java.time.DayOfWeek
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationDraftValidatorTest {
    private val validator = MedicationDraftValidator()

    @Test
    fun validFixedSchedule_preservesCaregiverTextAndParsesTimes() {
        val result = validator.validate(
            validDraft(
                displayName = "  Şeker İlacı  ",
                dosageText = "  1 tablet  ",
                instructionText = "  Yemekten sonra  ",
                schedules = listOf(
                    ScheduleDraft(timeText = "08:00"),
                    ScheduleDraft(timeText = "20:00"),
                ),
            )
        )

        assertTrue(result.isValid)
        assertEquals("Şeker İlacı", result.value?.displayName)
        assertEquals("1 tablet", result.value?.dosageText)
        assertEquals("Yemekten sonra", result.value?.instructionText)
        assertEquals(
            listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
            result.value?.schedules?.map { it.localTime },
        )
    }

    @Test
    fun invalidAndDuplicateTimes_areRejected() {
        val invalid = validator.validate(
            validDraft(schedules = listOf(ScheduleDraft(timeText = "25:00")))
        )
        val duplicate = validator.validate(
            validDraft(
                schedules = listOf(
                    ScheduleDraft(timeText = "20:00"),
                    ScheduleDraft(timeText = "20:00"),
                )
            )
        )

        assertTrue(MedicationDraftError.INVALID_TIME in invalid.errors)
        assertTrue(MedicationDraftError.DUPLICATE_TIME in duplicate.errors)
    }

    @Test
    fun missingRequiredMedicationFieldsAndDays_areRejected() {
        val result = validator.validate(
            validDraft(
                dosageText = " ",
                instructionText = " ",
                daysOfWeek = emptySet(),
            )
        )

        assertTrue(
            MedicationDraftError.DOSAGE_REQUIRED in result.errors
        )

        assertTrue(
            MedicationDraftError.INSTRUCTION_REQUIRED in result.errors
        )

        assertTrue(
            MedicationDraftError.DAY_REQUIRED in result.errors
        )
    }

    @Test
    fun enabledSnooze_requiresBoundedExplicitValues() {
        val result = validator.validate(
            validDraft(
                snoozeEnabled = true,
                snoozeMinutesText = "0",
                maxSnoozesText = "6",
            )
        )

        assertTrue(MedicationDraftError.SNOOZE_MINUTES_INVALID in result.errors)
        assertTrue(MedicationDraftError.MAX_SNOOZES_INVALID in result.errors)
    }

    private fun validDraft(
        displayName: String = "Şeker İlacı",
        dosageText: String = "1 tablet",
        instructionText: String = "Yemekten sonra",
        schedules: List<ScheduleDraft> = listOf(ScheduleDraft(timeText = "20:00")),
        daysOfWeek: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
        snoozeEnabled: Boolean = false,
        snoozeMinutesText: String = "",
        maxSnoozesText: String = "",
    ) = MedicationDraft(
        schedules = schedules,
        displayName = displayName,
        dosageText = dosageText,
        instructionText = instructionText,
        daysOfWeek = daysOfWeek,
        snoozeEnabled = snoozeEnabled,
        snoozeMinutesText = snoozeMinutesText,
        maxSnoozesText = maxSnoozesText
    )
}
