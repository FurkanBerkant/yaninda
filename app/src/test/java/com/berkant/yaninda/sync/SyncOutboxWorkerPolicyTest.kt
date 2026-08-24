package com.berkant.yaninda.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncOutboxWorkerPolicyTest {
    @Test
    fun transientAuthenticationAndPairingReadiness_requestRetry() {
        listOf(
            RemoteSyncReadiness.AUTHENTICATION_REQUIRED,
            RemoteSyncReadiness.PAIRING_REQUIRED,
        ).forEach { readiness ->
            assertEquals(
                SyncWorkerDecision.RETRY,
                resolveSyncWorkerDecision(
                    SyncProcessResult.RemoteNotReady(readiness)
                ),
            )
        }
    }

    @Test
    fun adminDeviceDoesNotRetryAlarmOnlyOutboxWorker() {
        assertEquals(
            SyncWorkerDecision.SUCCESS,
            resolveSyncWorkerDecision(
                SyncProcessResult.RemoteNotReady(
                    RemoteSyncReadiness.ALARM_DEVICE_REQUIRED
                )
            ),
        )
    }

    @Test
    fun retryableDeliveryRequestsWorkManagerRetry() {
        assertEquals(
            SyncWorkerDecision.RETRY,
            resolveSyncWorkerDecision(
                SyncProcessResult.RetryRequired(processedCount = 0)
            ),
        )
    }
}
