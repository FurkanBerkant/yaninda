package com.berkant.yaninda.ui.caregiver

import org.junit.Assert.assertEquals
import org.junit.Test

class CaregiverBackPolicyTest {
    @Test
    fun childDestination_returnsToMedicationList() {
        val action = resolveCaregiverBackAction(
            initialSetup = false,
            destination = CaregiverDestination.MEDICATION_FORM,
        )

        assertEquals(CaregiverBackAction.RETURN_TO_MEDICATION_LIST, action)
    }

    @Test
    fun incompleteInitialSetup_locksBackToPin() {
        val action = resolveCaregiverBackAction(
            initialSetup = true,
            destination = CaregiverDestination.MEDICATION_LIST,
        )

        assertEquals(CaregiverBackAction.LOCK_TO_PIN, action)
    }

    @Test
    fun completedSetup_locksAndReturnsToGrandfatherHome() {
        val action = resolveCaregiverBackAction(
            initialSetup = false,
            destination = CaregiverDestination.MEDICATION_LIST,
        )

        assertEquals(CaregiverBackAction.LOCK_TO_GRANDFATHER_HOME, action)
    }
}
