package com.berkant.yaninda.domain.occurrence

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class OccurrenceIdFactoryTest {
    @Test
    fun sameScheduleAndInstant_produceSameStableId() {
        val scheduledAt = Instant.parse("2026-08-21T17:00:00Z")

        val first = StableOccurrenceIdFactory.create("med-1", "schedule-1", scheduledAt)
        val second = StableOccurrenceIdFactory.create("med-1", "schedule-1", scheduledAt)

        assertEquals(first, second)
    }

    @Test
    fun scheduleOrInstantChange_producesDifferentId() {
        val scheduledAt = Instant.parse("2026-08-21T17:00:00Z")
        val original = StableOccurrenceIdFactory.create("med-1", "schedule-1", scheduledAt)

        assertNotEquals(
            original,
            StableOccurrenceIdFactory.create("med-1", "schedule-2", scheduledAt),
        )
        assertNotEquals(
            original,
            StableOccurrenceIdFactory.create(
                "med-1",
                "schedule-1",
                scheduledAt.plusSeconds(60),
            ),
        )
    }
}
