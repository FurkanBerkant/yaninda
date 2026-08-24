package com.berkant.yaninda

import com.berkant.yaninda.domain.family.DeviceRole
import com.berkant.yaninda.domain.family.FamilyPairing
import com.berkant.yaninda.family.private.PrivateDeviceProfile

internal enum class MainRoute {
    LOADING,
    ROLE_SETUP,
    ADMIN_HOME,
    ALARM_DEVICE_HOME,
}

internal fun resolveMainRoute(
    roleLoaded: Boolean,
    role: DeviceRole?,
    pairingLoaded: Boolean,
    pairing: FamilyPairing?,
    profileLoaded: Boolean,
    profile: PrivateDeviceProfile?,
): MainRoute = when {
    !roleLoaded || !pairingLoaded || !profileLoaded -> MainRoute.LOADING

    role == null || pairing == null || profile == null -> MainRoute.ROLE_SETUP

    role != pairing.deviceRole || role != profile.role -> MainRoute.ROLE_SETUP

    role == DeviceRole.ADMIN_DEVICE -> MainRoute.ADMIN_HOME

    role == DeviceRole.ALARM_DEVICE ->
        MainRoute.ALARM_DEVICE_HOME

    else -> MainRoute.ROLE_SETUP
}
