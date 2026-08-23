package com.berkant.yaninda.domain.family

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class FamilyMonitoringPolicyTest {
    private val now = Instant.parse("2026-08-21T18:00:00Z")
    private val policy = FamilyMonitoringPolicy()

    @Test
    fun missingPrimary_isNotPresentedAsCurrentOrOffline() {
        assertEquals(
            FamilyConnectionFreshness.PRIMARY_NOT_PAIRED,
            policy.evaluate(primaryDevice = null, now = now).freshness,
        )
    }

    @Test
    fun pairedDeviceWithoutSync_waitsForFirstKnownState() {
        assertEquals(
            FamilyConnectionFreshness.WAITING_FOR_FIRST_SYNC,
            policy.evaluate(primaryDevice(lastSync = null), now).freshness,
        )
    }

    @Test
    fun updateAtThreshold_isCurrentButOlderUpdateIsStale() {
        assertEquals(
            FamilyConnectionFreshness.CURRENT,
            policy.evaluate(primaryDevice(now.minusSeconds(30 * 60)), now).freshness,
        )
        assertEquals(
            FamilyConnectionFreshness.STALE,
            policy.evaluate(primaryDevice(now.minusSeconds(30 * 60 + 1)), now).freshness,
        )
    }

    private fun primaryDevice(lastSync: Instant?): DeviceRegistration = DeviceRegistration(
        deviceId = "primary-1",
        familyId = "family-1",
        ownerUid = "primary-user",
        role = DeviceRole.ALARM_DEVICE,
        displayName = "Dede telefonu",
        appVersion = "1.0",
        lastSeenAt = lastSync,
        lastSuccessfulSyncAt = lastSync,
        version = 1L,
    )
}
