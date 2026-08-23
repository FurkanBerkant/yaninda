package com.berkant.yaninda.secondary

import com.berkant.yaninda.core.time.TimeProvider
import com.berkant.yaninda.data.device.DeviceIdentityRepository
import com.berkant.yaninda.domain.family.DeviceRole
import com.berkant.yaninda.domain.family.FamilyDoseOccurrence
import com.berkant.yaninda.domain.family.FamilyPairing
import com.berkant.yaninda.domain.occurrence.DoseOccurrenceStatus
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecondaryReminderCoordinatorTest {
    private val now = Instant.parse("2026-08-21T17:00:00Z")
    private val timeProvider = FixedTimeProvider(now)

    @Test
    fun remoteSchedule_isCachedButNotScheduledUntilCaregiverOptsIn() = runBlocking {
        val settings = FakeSettingsRepository()
        val scheduler = FakeSecondaryScheduler()
        val coordinator = coordinator(settings = settings, scheduler = scheduler)
        val occurrence = remoteOccurrence("occurrence-1", now.plusSeconds(3_600))

        val disabled = coordinator.updateFromRemote("family-1", listOf(occurrence))
        val enabled = coordinator.setEnabled(true)

        assertEquals(1, disabled.cachedOccurrenceCount)
        assertEquals(0, disabled.scheduledOccurrenceCount)
        assertEquals(now.plusSeconds(3_600), scheduler.triggers["occurrence-1"])
        assertEquals(1, enabled.scheduledOccurrenceCount)
        assertEquals(now.plusSeconds(3_600), enabled.nextReminderAt)
    }

    @Test
    fun rebootRecovery_schedulesRecentlyMissedCachedTimeFiveSecondsLater() = runBlocking {
        val cached = CachedSecondaryReminder(
            occurrenceId = "occurrence-recovery",
            familyId = "family-1",
            scheduledAt = now.minusSeconds(10 * 60),
            syncedAt = now.minusSeconds(20 * 60),
            sourceDeviceId = "primary-1",
            version = 1,
        )
        val scheduler = FakeSecondaryScheduler()
        val coordinator = coordinator(
            settings = FakeSettingsRepository(initialEnabled = true),
            scheduler = scheduler,
            cache = FakeCacheRepository(listOf(cached)),
        )

        val restored = coordinator.restoreFromCache()

        assertEquals(now.plusSeconds(5), scheduler.triggers[cached.occurrenceId])
        assertEquals(1, restored.scheduledOccurrenceCount)
    }

    @Test
    fun delivery_consumesCacheBeforeShowingAndCannotNotifyTwice() = runBlocking {
        val cached = CachedSecondaryReminder(
            occurrenceId = "occurrence-due",
            familyId = "family-1",
            scheduledAt = now,
            syncedAt = now.minusSeconds(60),
            sourceDeviceId = "primary-1",
            version = 1,
        )
        val notifier = FakeSecondaryNotifier()
        val coordinator = coordinator(
            settings = FakeSettingsRepository(initialEnabled = true),
            cache = FakeCacheRepository(listOf(cached)),
            notifier = notifier,
        )

        assertTrue(coordinator.deliver(cached.occurrenceId))
        assertFalse(coordinator.deliver(cached.occurrenceId))
        assertEquals(listOf(cached.occurrenceId), notifier.deliveredOccurrenceIds)
    }

    private fun coordinator(
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        scheduler: FakeSecondaryScheduler = FakeSecondaryScheduler(),
        cache: FakeCacheRepository = FakeCacheRepository(),
        notifier: FakeSecondaryNotifier = FakeSecondaryNotifier(),
    ) = SecondaryReminderCoordinator(
        cacheRepository = cache,
        settingsRepository = settings,
        deviceIdentityRepository = FakeDeviceIdentityRepository(
            FamilyPairing("family-1", DeviceRole.ADMIN_DEVICE)
        ),
        scheduler = scheduler,
        notifier = notifier,
        timeProvider = timeProvider,
    )

    private fun remoteOccurrence(
        occurrenceId: String,
        scheduledAt: Instant,
    ) = FamilyDoseOccurrence(
        occurrenceId = occurrenceId,
        medicationDisplayName = "Test ilacı",
        scheduledAt = scheduledAt,
        status = DoseOccurrenceStatus.SCHEDULED,
        acknowledgedAt = null,
        acknowledgementActor = null,
        lastAlertedAt = null,
        updatedAt = now,
        syncedAt = now,
        version = 1,
        sourceDeviceId = "primary-1",
    )
}

private class FakeCacheRepository(
    initial: List<CachedSecondaryReminder> = emptyList(),
) : SecondaryReminderCacheRepository {
    private val records = initial.associateByTo(mutableMapOf()) { it.occurrenceId }

    override suspend fun all(): List<CachedSecondaryReminder> =
        records.values.sortedBy(CachedSecondaryReminder::scheduledAt)

    override suspend fun replaceSchedule(
        familyId: String,
        occurrences: List<FamilyDoseOccurrence>,
        now: Instant,
    ): SecondaryCacheReplacement {
        val previousIds = records.keys.toList()
        records.clear()
        occurrences.filter {
            it.status == DoseOccurrenceStatus.SCHEDULED && it.scheduledAt > now
        }.forEach { occurrence ->
            records[occurrence.occurrenceId] = CachedSecondaryReminder(
                occurrenceId = occurrence.occurrenceId,
                familyId = familyId,
                scheduledAt = occurrence.scheduledAt,
                syncedAt = occurrence.syncedAt,
                sourceDeviceId = occurrence.sourceDeviceId,
                version = occurrence.version,
            )
        }
        return SecondaryCacheReplacement(previousIds, all())
    }

    override suspend fun remove(occurrenceId: String): CachedSecondaryReminder? =
        records.remove(occurrenceId)

    override suspend fun clear(): List<String> = records.keys.toList().also { records.clear() }
}

private class FakeSettingsRepository(
    initialEnabled: Boolean = false,
) : SecondaryReminderSettingsRepository {
    private val mutableEnabled = MutableStateFlow(initialEnabled)
    override val enabled: Flow<Boolean> = mutableEnabled

    override suspend fun isEnabled(): Boolean = mutableEnabled.value

    override suspend fun setEnabled(enabled: Boolean) {
        mutableEnabled.value = enabled
    }
}

private class FakeSecondaryScheduler(
    private val result: SecondaryReminderSchedulingResult =
        SecondaryReminderSchedulingResult.EXACT,
) : SecondaryReminderScheduler {
    val triggers = mutableMapOf<String, Instant>()

    override fun schedule(
        occurrenceId: String,
        triggerAt: Instant,
    ): SecondaryReminderSchedulingResult {
        triggers[occurrenceId] = triggerAt
        return result
    }

    override fun cancel(occurrenceId: String) {
        triggers.remove(occurrenceId)
    }
}

private class FakeSecondaryNotifier : SecondaryReminderNotifier {
    val deliveredOccurrenceIds = mutableListOf<String>()

    override fun ensureChannel() = Unit

    override fun show(occurrenceId: String, scheduledAt: Instant): Boolean {
        deliveredOccurrenceIds += occurrenceId
        return true
    }
}

private class FakeDeviceIdentityRepository(
    initialPairing: FamilyPairing?,
) : DeviceIdentityRepository {
    private val pairingState = MutableStateFlow(initialPairing)
    override val selectedRole: Flow<DeviceRole?> = MutableStateFlow(initialPairing?.deviceRole)
    override val pairing: Flow<FamilyPairing?> = pairingState

    override suspend fun selectRole(role: DeviceRole) = Unit

    override suspend fun getOrCreateDeviceId(): String = "caregiver-device-1"

    override suspend fun recordPairing(pairing: FamilyPairing) {
        pairingState.value = pairing
    }

    override suspend fun clearPairing() {
        pairingState.value = null
    }
}

private class FixedTimeProvider(
    private val instant: Instant,
) : TimeProvider {
    override fun now(): Instant = instant

    override fun currentZoneId(): ZoneId = ZoneId.of("Europe/Istanbul")
}
