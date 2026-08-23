package com.berkant.yaninda.domain.medication

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Test

class DayOfWeekMaskTest {
    @Test
    fun encodeAndDecode_roundTripsSelectedDays() {
        val days = linkedSetOf(
            DayOfWeek.MONDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.SUNDAY,
        )

        assertEquals(days, DayOfWeekMask.decode(DayOfWeekMask.encode(days)))
    }

    @Test
    fun allDays_useSevenBits() {
        assertEquals(127, DayOfWeekMask.encode(DayOfWeek.entries.toSet()))
    }
}
