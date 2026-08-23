package com.berkant.yaninda

import com.berkant.yaninda.domain.family.DeviceRole
import com.berkant.yaninda.domain.family.FamilyPairing

internal enum class MainRoute {
    LOADING,
    ROLE_SETUP,
    ADMIN_HOME,
    ALARM_DEVICE_SETUP,
    ALARM_DEVICE_HOME,
}

internal fun resolveMainRoute(
    roleLoaded: Boolean,
    role: DeviceRole?,
    pairingLoaded: Boolean,
    pairing: FamilyPairing?,
): MainRoute = when {
    !roleLoaded || !pairingLoaded -> MainRoute.LOADING

    role == null -> MainRoute.ROLE_SETUP

    role == DeviceRole.ADMIN_DEVICE -> MainRoute.ADMIN_HOME

    role == DeviceRole.ALARM_DEVICE && pairing == null ->
        MainRoute.ALARM_DEVICE_SETUP

    role == DeviceRole.ALARM_DEVICE ->
        MainRoute.ALARM_DEVICE_HOME

    else -> MainRoute.ROLE_SETUP
}