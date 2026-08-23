package com.berkant.yaninda.family.private

import com.berkant.yaninda.domain.family.DeviceRole

object PrivateFamilyConfig {
    const val FAMILY_ID = "sefer-family"
    const val FAMILY_NAME = "Sefer Ailesi"
}

enum class PrivateDeviceProfile(
    val displayName: String,
    val role: DeviceRole,
) {
    GRANDFATHER(
        displayName = "Dede telefonu",
        role = DeviceRole.ALARM_DEVICE,
    ),

    GRANDMOTHER(
        displayName = "Anneanne telefonu",
        role = DeviceRole.ALARM_DEVICE,
    ),

    BERKANT(
        displayName = "Berkant telefonu",
        role = DeviceRole.ADMIN_DEVICE,
    ),

    MOTHER(
        displayName = "Anne telefonu",
        role = DeviceRole.ADMIN_DEVICE,
    ),
}
