package com.berkant.yaninda.ui.grandfather

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GrandfatherHomePresentationTest {
    private val zoneId = ZoneId.of("Europe/Istanbul")

    @Test
    fun sameDayMedication_doesNotRepeatDateLabel() {
        assertNull(
            formatNextMedicationDayLabel(
                now = Instant.parse("2026-08-24T10:00:00Z"),
                nextMedicationAt = Instant.parse("2026-08-24T17:00:00Z"),
                zoneId = zoneId,
            )
        )
    }

    @Test
    fun nextDayMedication_isClearlyLabeledTomorrow() {
        assertEquals(
            "Yarın",
            formatNextMedicationDayLabel(
                now = Instant.parse("2026-08-24T15:45:00Z"),
                nextMedicationAt = Instant.parse("2026-08-25T15:32:00Z"),
                zoneId = zoneId,
            ),
        )
    }

    @Test
    fun laterMedication_showsItsTurkishCalendarDate() {
        assertEquals(
            "27 Ağustos Perşembe",
            formatNextMedicationDayLabel(
                now = Instant.parse("2026-08-24T15:45:00Z"),
                nextMedicationAt = Instant.parse("2026-08-27T15:32:00Z"),
                zoneId = zoneId,
            ),
        )
    }
}
