package com.berkant.yaninda.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeProviderTest {
    @Test
    fun today_usesInjectedInstantAndCurrentZone() {
        val instant = Instant.parse("2026-08-21T22:30:00Z")
        var zone = ZoneId.of("UTC")
        val provider = SystemTimeProvider(
            instantProvider = { instant },
            zoneProvider = { zone },
        )

        assertEquals(LocalDate.of(2026, 8, 21), provider.today())
        zone = ZoneId.of("Europe/Istanbul")
        assertEquals(LocalDate.of(2026, 8, 22), provider.today())
    }
}
