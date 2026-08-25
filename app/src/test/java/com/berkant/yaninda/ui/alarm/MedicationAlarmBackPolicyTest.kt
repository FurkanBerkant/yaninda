package com.berkant.yaninda.ui.alarm

import org.junit.Assert.assertEquals
import org.junit.Test

class MedicationAlarmBackPolicyTest {
    @Test
    fun alarmBack_isConsumed() {
        assertEquals(
            MedicationAlarmBackAction.CONSUME,
            resolveMedicationAlarmBackAction(),
        )
    }
}
