package com.berkant.yaninda.receiver

import com.berkant.yaninda.notification.NotificationCapability
import com.berkant.yaninda.notification.NotificationDeliveryResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationAlarmDeliverySafetyPolicyTest {

    @Test
    fun deliveredNotification_allowsControllableAttention() {
        assertTrue(
            shouldStartMedicationAttention(
                NotificationDeliveryResult.Delivered
            )
        )
    }

    @Test
    fun blockedNotification_neverStartsUncontrollableAttention() {
        assertFalse(
            shouldStartMedicationAttention(
                NotificationDeliveryResult.Blocked(
                    NotificationCapability.RUNTIME_PERMISSION_REQUIRED
                )
            )
        )
    }

    @Test
    fun platformFailure_neverStartsUncontrollableAttention() {
        assertFalse(
            shouldStartMedicationAttention(
                NotificationDeliveryResult.PlatformFailure
            )
        )
    }
}
