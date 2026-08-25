package com.berkant.yaninda.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationAlarmAttentionPolicyTest {

    @Test
    fun hardTimeout_isFortySeconds() {
        assertEquals(
            40_000L,
            MedicationAlarmAttentionService.HARD_TIMEOUT_MILLIS,
        )
    }

    @Test
    fun timeout_onlyOwnsTheLatestServiceStart() {
        assertTrue(
            isCurrentAttentionStart(
                timedOutStartId = 12,
                activeStartId = 12,
            )
        )

        assertFalse(
            isCurrentAttentionStart(
                timedOutStartId = 11,
                activeStartId = 12,
            )
        )

        assertFalse(
            isCurrentAttentionStart(
                timedOutStartId = 12,
                activeStartId = null,
            )
        )
    }
}
