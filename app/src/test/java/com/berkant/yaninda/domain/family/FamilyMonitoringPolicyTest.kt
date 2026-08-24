package com.berkant.yaninda.domain.family

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class FamilyMonitoringPolicyTest {
    private val now = Instant.parse("2026-08-21T18:00:00Z")
    private val policy = FamilyMonitoringPolicy()

    @Test
    fun missingAlarmDevice_isNotPresentedAsCurrentOrOffline() {
        assertEquals(
            FamilyConnectionFreshness.ALARM_DEVICE_NOT_PAIRED,
            policy.evaluate(device = null, now = now).freshness,
        )
    }

    @Test
    fun pairedDeviceWithoutSync_waitsForFirstKnownState() {
        assertEquals(
            FamilyConnectionFreshness.WAITING_FOR_FIRST_SYNC,
            policy.evaluate(alarmDevice(lastSync = null), now).freshness,
        )
    }

    @Test
    fun updateAtThreshold_isCurrentButOlderUpdateIsStale() {
        assertEquals(
            FamilyConnectionFreshness.CURRENT,
            policy.evaluate(alarmDevice(now.minusSeconds(30 * 60)), now).freshness,
        )
        assertEquals(
            FamilyConnectionFreshness.STALE,
            policy.evaluate(alarmDevice(now.minusSeconds(30 * 60 + 1)), now).freshness,
        )
    }

    private fun alarmDevice(lastSync: Instant?): DeviceRegistration = DeviceRegistration(
        deviceId = "alarm-1",
        familyId = "family-1",
        ownerUid = "alarm-user",
        role = DeviceRole.ALARM_DEVICE,
        displayName = "Dede telefonu",
        appVersion = "1.0",
        lastSeenAt = lastSync,
        lastSuccessfulSyncAt = lastSync,
        version = 1L,
    )
}
