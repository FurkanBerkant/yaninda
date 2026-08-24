package com.berkant.yaninda.ui.admin

import com.berkant.yaninda.domain.family.FamilyDoseOccurrence
import com.berkant.yaninda.domain.occurrence.AcknowledgementActor
import com.berkant.yaninda.domain.occurrence.DoseOccurrenceStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class AdminHistoryBuilderTest {
    private val zoneId = ZoneId.of("Europe/Istanbul")
    private val today = LocalDate.of(2026, 8, 24)

    @Test
    fun futurePlannerRows_areNotShownBeforeTodaysHistory() {
        val days = buildAdminHistoryDays(
            occurrences = listOf(
                report("future", today.plusDays(1), DoseOccurrenceStatus.SCHEDULED),
                report("today", today, DoseOccurrenceStatus.ACKNOWLEDGED_TAKEN),
            ),
            zoneId = zoneId,
            throughDate = today,
        )

        assertEquals(listOf(today), days.map(AdminHistoryDay::date))
        assertEquals(
            DoseOccurrenceStatus.ACKNOWLEDGED_TAKEN,
            days.single().doseGroups.single().status,
        )
    }

    @Test
    fun acknowledgementFromEitherAlarmDevice_winsForLogicalOccurrence() {
        val days = buildAdminHistoryDays(
            occurrences = listOf(
                report(
                    occurrenceId = "logical-dose",
                    date = today,
                    status = DoseOccurrenceStatus.SCHEDULED,
                    sourceDeviceId = "anneanne",
                ),
                report(
                    occurrenceId = "logical-dose",
                    date = today,
                    status = DoseOccurrenceStatus.ACKNOWLEDGED_TAKEN,
                    sourceDeviceId = "dede",
                ),
            ),
            zoneId = zoneId,
            throughDate = today,
        )

        val group = days.single().doseGroups.single()
        assertEquals(DoseOccurrenceStatus.ACKNOWLEDGED_TAKEN, group.status)
        assertEquals(2, group.sourceDeviceCount)
    }

    private fun report(
        occurrenceId: String,
        date: LocalDate,
        status: DoseOccurrenceStatus,
        sourceDeviceId: String = "dede",
    ): FamilyDoseOccurrence {
        val scheduledAt = instant(date, 13, 55)
        val acknowledgedAt = if (status == DoseOccurrenceStatus.ACKNOWLEDGED_TAKEN) {
            instant(date, 13, 56)
        } else {
            null
        }
        return FamilyDoseOccurrence(
            occurrenceId = occurrenceId,
            medicationDisplayName = "Şeker İlacı",
            scheduledAt = scheduledAt,
            status = status,
            acknowledgedAt = acknowledgedAt,
            acknowledgementActor = acknowledgedAt?.let {
                AcknowledgementActor.GRANDFATHER
            },
            lastAlertedAt = null,
            updatedAt = acknowledgedAt ?: scheduledAt,
            syncedAt = (acknowledgedAt ?: scheduledAt).plusSeconds(1),
            version = if (acknowledgedAt == null) 1L else 3L,
            sourceDeviceId = sourceDeviceId,
        )
    }

    private fun instant(
        date: LocalDate,
        hour: Int,
        minute: Int,
    ): Instant = LocalDateTime.of(date, LocalTime.of(hour, minute))
        .atZone(zoneId)
        .toInstant()
}
