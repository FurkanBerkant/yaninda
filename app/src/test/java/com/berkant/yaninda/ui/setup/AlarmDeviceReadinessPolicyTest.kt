package com.berkant.yaninda.ui.setup

import com.berkant.yaninda.notification.FullScreenIntentCapability
import com.berkant.yaninda.notification.NotificationCapability
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmDeviceReadinessPolicyTest {

    @Test
    fun notificationProblem_blocksReadinessBeforeFullScreenCheck() {
        assertEquals(
            AlarmDeviceReadiness.NOTIFICATION_ACTION_REQUIRED,
            resolveAlarmDeviceReadiness(
                notificationCapability = NotificationCapability.RUNTIME_PERMISSION_REQUIRED,
                fullScreenIntentCapability = FullScreenIntentCapability.USER_ACTION_REQUIRED,
            ),
        )
    }

    @Test
    fun fullScreenProblem_blocksReadinessAfterNotificationsAreReady() {
        assertEquals(
            AlarmDeviceReadiness.FULL_SCREEN_ACTION_REQUIRED,
            resolveAlarmDeviceReadiness(
                notificationCapability = NotificationCapability.AVAILABLE,
                fullScreenIntentCapability = FullScreenIntentCapability.USER_ACTION_REQUIRED,
            ),
        )
    }

    @Test
    fun allRequiredCapabilities_readyAllowsAlarmDeviceHome() {
        assertEquals(
            AlarmDeviceReadiness.READY,
            resolveAlarmDeviceReadiness(
                notificationCapability = NotificationCapability.AVAILABLE,
                fullScreenIntentCapability = FullScreenIntentCapability.AVAILABLE,
            ),
        )
    }
}
