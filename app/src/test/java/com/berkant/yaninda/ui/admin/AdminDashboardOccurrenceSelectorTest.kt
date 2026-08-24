package com.berkant.yaninda.ui.admin

import com.berkant.yaninda.domain.family.FamilyDoseOccurrence
import com.berkant.yaninda.domain.occurrence.AcknowledgementActor
import com.berkant.yaninda.domain.occurrence.DoseOccurrenceStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdminDashboardOccurrenceSelectorTest {
    private val zoneId = ZoneId.of("Europe/Istanbul")
    private val today = LocalDate.of(2026, 8, 24)

    @Test
    fun futureScheduledReport_doesNotHideTodaysAcknowledgement() {
        val future = occurrence(
            occurrenceId = "future",
            scheduledDate = today.plusDays(7),
            status = DoseOccurrenceStatus.SCHEDULED,
        )
        val acknowledgedToday = occurrence(
            occurrenceId = "today",
            scheduledDate = today,
            status = DoseOccurrenceStatus.ACKNOWLEDGED_TAKEN,
            acknowledgedAt = instant(today, 13, 56),
        )

        val selected = selectOccurrenceForPublishedDose(
            occurrences = listOf(future, acknowledgedToday),
            medicationDisplayName = MEDICATION_NAME,
            scheduledDate = today,
            localTimeMinutes = 13 * 60 + 55,
            zoneId = zoneId,
        )

        assertEquals(acknowledgedToday, selected)
    }

    @Test
    fun acknowledgedDeviceReport_winsOverScheduledReportForSameDose() {
        val scheduled = occurrence(
            occurrenceId = "logical-dose",
            scheduledDate = today,
            status = DoseOccurrenceStatus.SCHEDULED,
            sourceDeviceId = "anneanne-device",
            updatedAt = instant(today, 13, 55),
        )
        val acknowledged = occurrence(
            occurrenceId = "logical-dose",
            scheduledDate = today,
            status = DoseOccurrenceStatus.ACKNOWLEDGED_TAKEN,
            sourceDeviceId = "dede-device",
            acknowledgedAt = instant(today, 13, 56),
            updatedAt = instant(today, 13, 56),
        )

        val selected = selectOccurrenceForPublishedDose(
            occurrences = listOf(scheduled, acknowledged),
            medicationDisplayName = MEDICATION_NAME,
            scheduledDate = today,
            localTimeMinutes = 13 * 60 + 55,
            zoneId = zoneId,
        )

        assertEquals(acknowledged, selected)
    }

    @Test
    fun missingReportForRequestedDate_returnsNull() {
        val selected = selectOccurrenceForPublishedDose(
            occurrences = listOf(
                occurrence(
                    occurrenceId = "future",
                    scheduledDate = today.plusDays(1),
                    status = DoseOccurrenceStatus.SCHEDULED,
                )
            ),
            medicationDisplayName = MEDICATION_NAME,
            scheduledDate = today,
            localTimeMinutes = 13 * 60 + 55,
            zoneId = zoneId,
        )

        assertNull(selected)
    }

    private fun occurrence(
        occurrenceId: String,
        scheduledDate: LocalDate,
        status: DoseOccurrenceStatus,
        sourceDeviceId: String = "dede-device",
        acknowledgedAt: Instant? = null,
        updatedAt: Instant = instant(scheduledDate, 13, 55),
    ): FamilyDoseOccurrence = FamilyDoseOccurrence(
        occurrenceId = occurrenceId,
        medicationDisplayName = MEDICATION_NAME,
        scheduledAt = instant(scheduledDate, 13, 55),
        status = status,
        acknowledgedAt = acknowledgedAt,
        acknowledgementActor = if (acknowledgedAt == null) {
            null
        } else {
            AcknowledgementActor.GRANDFATHER
        },
        lastAlertedAt = null,
        updatedAt = updatedAt,
        syncedAt = updatedAt.plusSeconds(1),
        version = if (status == DoseOccurrenceStatus.ACKNOWLEDGED_TAKEN) 3L else 1L,
        sourceDeviceId = sourceDeviceId,
    )

    private fun instant(
        date: LocalDate,
        hour: Int,
        minute: Int,
    ): Instant = LocalDateTime.of(date, java.time.LocalTime.of(hour, minute))
        .atZone(zoneId)
        .toInstant()

    private companion object {
        const val MEDICATION_NAME = "test"
    }
}
