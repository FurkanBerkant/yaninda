package com.berkant.yaninda

import com.berkant.yaninda.domain.family.DeviceRole
import com.berkant.yaninda.domain.family.FamilyPairing
import com.berkant.yaninda.family.private.PrivateDeviceProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class MainRoutingPolicyTest {
    @Test
    fun alarmDevice_withoutPrivateProfile_opensSetup() {
        val route = resolveMainRoute(
            roleLoaded = true,
            role = DeviceRole.ALARM_DEVICE,
            pairingLoaded = true,
            pairing = FamilyPairing("family-1", DeviceRole.ALARM_DEVICE),
            profileLoaded = true,
            profile = null,
        )

        assertEquals(MainRoute.ROLE_SETUP, route)
    }

    @Test
    fun pairedAlarmDevice_opensGrandfatherHome() {
        val route = resolveMainRoute(
            roleLoaded = true,
            role = DeviceRole.ALARM_DEVICE,
            pairingLoaded = true,
            pairing = FamilyPairing("family-1", DeviceRole.ALARM_DEVICE),
            profileLoaded = true,
            profile = PrivateDeviceProfile.GRANDFATHER,
        )

        assertEquals(MainRoute.ALARM_DEVICE_HOME, route)
    }

    @Test
    fun adminDevice_opensAdminHome() {
        val route = resolveMainRoute(
            roleLoaded = true,
            role = DeviceRole.ADMIN_DEVICE,
            pairingLoaded = true,
            pairing = FamilyPairing("family-1", DeviceRole.ADMIN_DEVICE),
            profileLoaded = true,
            profile = PrivateDeviceProfile.BERKANT,
        )

        assertEquals(MainRoute.ADMIN_HOME, route)
    }

    @Test
    fun mismatchedProfile_returnsToPrivateSetup() {
        val route = resolveMainRoute(
            roleLoaded = true,
            role = DeviceRole.ALARM_DEVICE,
            pairingLoaded = true,
            pairing = FamilyPairing("family-1", DeviceRole.ALARM_DEVICE),
            profileLoaded = true,
            profile = PrivateDeviceProfile.BERKANT,
        )

        assertEquals(MainRoute.ROLE_SETUP, route)
    }
}
