package com.berkant.yaninda.receiver

import com.berkant.yaninda.YanindaApplication
import com.berkant.yaninda.domain.family.DeviceRole
import kotlinx.coroutines.flow.first

/**
 * Local medication alarms may only be restored/recovered by a fully
 * configured ALARM_DEVICE.
 *
 * Requiring selected role + family pairing + private profile prevents an
 * ADMIN_DEVICE with stale Room data from scheduling medication alarms after
 * reboot, time/timezone changes, exact-alarm permission changes or app reopen.
 */
internal suspend fun YanindaApplication.isConfiguredAlarmDevice(): Boolean {
    val selectedRole =
        deviceIdentityRepository
            .selectedRole
            .first()
            ?: return false

    val pairing =
        deviceIdentityRepository
            .pairing
            .first()
            ?: return false

    val profile =
        privateDeviceProfileRepository
            .profile
            .first()
            ?: return false

    return selectedRole == DeviceRole.ALARM_DEVICE &&
        pairing.deviceRole == DeviceRole.ALARM_DEVICE &&
        profile.role == DeviceRole.ALARM_DEVICE
}
