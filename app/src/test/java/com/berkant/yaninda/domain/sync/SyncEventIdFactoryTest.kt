package com.berkant.yaninda.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SyncEventIdFactoryTest {
    @Test
    fun create_isStableForTheSameAggregateVersion() {
        val first = SyncEventIdFactory.create(
            eventType = SyncEventType.DOSE_OCCURRENCE_ACKNOWLEDGED,
            aggregateId = "occurrence-1",
            aggregateVersion = 3L,
        )
        val second = SyncEventIdFactory.create(
            eventType = SyncEventType.DOSE_OCCURRENCE_ACKNOWLEDGED,
            aggregateId = "occurrence-1",
            aggregateVersion = 3L,
        )

        assertEquals(first, second)
        assertEquals("DOSE_OCCURRENCE_ACKNOWLEDGED:occurrence-1:v3", first)
    }

    @Test
    fun create_changesWhenAggregateVersionChanges() {
        val first = SyncEventIdFactory.create(
            SyncEventType.DOSE_OCCURRENCE_ACKNOWLEDGED,
            "occurrence-1",
            3L,
        )
        val second = SyncEventIdFactory.create(
            SyncEventType.DOSE_OCCURRENCE_ACKNOWLEDGED,
            "occurrence-1",
            4L,
        )

        assertNotEquals(first, second)
    }

    @Test
    fun create_rejectsInvalidIdentityParts() {
        assertThrows(IllegalArgumentException::class.java) {
            SyncEventIdFactory.create(
                SyncEventType.DOSE_OCCURRENCE_ACKNOWLEDGED,
                " ",
                1L,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SyncEventIdFactory.create(
                SyncEventType.DOSE_OCCURRENCE_ACKNOWLEDGED,
                "occurrence-1",
                0L,
            )
        }
    }
}
