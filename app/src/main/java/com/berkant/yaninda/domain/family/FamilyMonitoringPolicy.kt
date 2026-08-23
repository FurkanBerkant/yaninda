package com.berkant.yaninda.domain.family

import java.time.Duration
import java.time.Instant

enum class FamilyConnectionFreshness {
    ALARM_DEVICE_NOT_PAIRED,
    WAITING_FOR_FIRST_SYNC,
    CURRENT,
    STALE,
}

data class FamilyConnectionStatus(
    val freshness: FamilyConnectionFreshness,
    val lastSuccessfulSyncAt: Instant?,
)

class FamilyMonitoringPolicy(
    private val staleAfter: Duration = DEFAULT_STALE_AFTER,
) {
    init {
        require(!staleAfter.isNegative && !staleAfter.isZero) {
            "The stale threshold must be positive."
        }
    }

    fun evaluate(
        device: DeviceRegistration?,
        now: Instant,
    ): FamilyConnectionStatus {
        if (device == null) {
            return FamilyConnectionStatus(FamilyConnectionFreshness.ALARM_DEVICE_NOT_PAIRED, null)
        }
        val lastSync = device.lastSuccessfulSyncAt
            ?: return FamilyConnectionStatus(
                FamilyConnectionFreshness.WAITING_FOR_FIRST_SYNC,
                null,
            )
        val age = Duration.between(lastSync, now)
        return FamilyConnectionStatus(
            freshness = if (age > staleAfter) {
                FamilyConnectionFreshness.STALE
            } else {
                FamilyConnectionFreshness.CURRENT
            },
            lastSuccessfulSyncAt = lastSync,
        )
    }

    companion object {
        val DEFAULT_STALE_AFTER: Duration = Duration.ofMinutes(30)
    }
}
