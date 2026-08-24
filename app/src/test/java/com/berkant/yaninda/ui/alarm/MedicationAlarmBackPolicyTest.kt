package com.berkant.yaninda.ui.alarm

import org.junit.Assert.assertEquals
import org.junit.Test

class MedicationAlarmBackPolicyTest {
    @Test
    fun confirmationBack_returnsToAlarm() {
        assertEquals(
            MedicationAlarmBackAction.RETURN_TO_ALARM,
            resolveMedicationAlarmBackAction(
                MedicationAlarmDestination.TAKEN_CONFIRMATION
            ),
        )
    }

    @Test
    fun alarmBack_keepsAttentionActiveAndMovesTaskToBackground() {
        assertEquals(
            MedicationAlarmBackAction.MOVE_TASK_TO_BACKGROUND,
            resolveMedicationAlarmBackAction(
                MedicationAlarmDestination.ALARM
            ),
        )
    }
}
