package com.berkant.yaninda.ui.admin

import com.berkant.yaninda.domain.family.DeviceRegistration
import com.berkant.yaninda.domain.family.DeviceRole
import com.berkant.yaninda.ui.components.YanindaStatusTone
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class AdminDeviceStatusPresentationTest {
    private val now = Instant.parse("2026-08-25T10:00:00Z")

    @Test
    fun currentAdmin_isNotShownAsWaitingForMedicationSync() {
        val device = device(DeviceRole.ADMIN_DEVICE, null)
        val result = deviceStatusPresentation(device, device.deviceId, now)
        assertEquals("Bu telefon", result.label)
        assertEquals(YanindaStatusTone.INFO, result.tone)
    }

    @Test
    fun recentlySyncedAlarm_isCurrent() {
        val result = deviceStatusPresentation(
            device(DeviceRole.ALARM_DEVICE, now.minusSeconds(10 * 60)),
            null,
            now,
        )
        assertEquals("Bağlantı güncel", result.label)
        assertEquals(YanindaStatusTone.SUCCESS, result.tone)
    }

    @Test
    fun staleAlarm_isNotShownAsConnected() {
        val result = deviceStatusPresentation(
            device(DeviceRole.ALARM_DEVICE, now.minusSeconds(31 * 60)),
            null,
            now,
        )
        assertEquals("Uzun süredir bağlantı yok", result.label)
        assertEquals(YanindaStatusTone.ERROR, result.tone)
    }

    private fun device(role: DeviceRole, lastSync: Instant?) = DeviceRegistration(
        deviceId = "device-a",
        familyId = "sefer-family",
        ownerUid = "owner-a",
        role = role,
        displayName = "Test telefonu",
        appVersion = "1.0.3",
        lastSeenAt = lastSync,
        lastSuccessfulSyncAt = lastSync,
        version = 1,
    )
}
