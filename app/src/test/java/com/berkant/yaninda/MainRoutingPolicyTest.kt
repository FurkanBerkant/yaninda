package com.berkant.yaninda

import com.berkant.yaninda.domain.family.DeviceRole
import com.berkant.yaninda.domain.family.FamilyPairing
import org.junit.Assert.assertEquals
import org.junit.Test

class MainRoutingPolicyTest {
    @Test
    fun alarmDevice_withoutPairing_opensSetup() {
        val route = resolveMainRoute(
            roleLoaded = true,
            role = DeviceRole.ALARM_DEVICE,
            pairingLoaded = true,
            pairing = null,
        )

        assertEquals(MainRoute.ALARM_DEVICE_SETUP, route)
    }

    @Test
    fun pairedAlarmDevice_opensGrandfatherHome() {
        val route = resolveMainRoute(
            roleLoaded = true,
            role = DeviceRole.ALARM_DEVICE,
            pairingLoaded = true,
            pairing = FamilyPairing("family-1", DeviceRole.ALARM_DEVICE),
        )

        assertEquals(MainRoute.ALARM_DEVICE_HOME, route)
    }

    @Test
    fun adminDevice_opensAdminHome() {
        val route = resolveMainRoute(
            roleLoaded = true,
            role = DeviceRole.ADMIN_DEVICE,
            pairingLoaded = true,
            pairing = null,
        )

        assertEquals(MainRoute.ADMIN_HOME, route)
    }
}
