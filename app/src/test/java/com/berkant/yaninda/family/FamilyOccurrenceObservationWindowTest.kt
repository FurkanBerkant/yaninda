package com.berkant.yaninda.family

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class FamilyOccurrenceObservationWindowTest {
    @Test
    fun window_includesHistoryThroughEndOfCurrentLocalDay() {
        val window = familyOccurrenceObservationWindow(
            now = Instant.parse("2026-08-24T15:00:00Z"),
            zoneId = ZoneId.of("Europe/Istanbul"),
            historyDays = 90L,
        )

        assertEquals(
            Instant.parse("2026-05-26T21:00:00Z"),
            window.startInclusive,
        )
        assertEquals(
            Instant.parse("2026-08-24T21:00:00Z"),
            window.endExclusive,
        )
    }

    @Test
    fun window_usesCalendarDaysAcrossDaylightSavingChange() {
        val window = familyOccurrenceObservationWindow(
            now = Instant.parse("2026-10-25T12:00:00Z"),
            zoneId = ZoneId.of("Europe/Berlin"),
            historyDays = 1L,
        )

        assertEquals(
            Duration.ofHours(25L),
            Duration.between(window.startInclusive, window.endExclusive),
        )
    }
}
