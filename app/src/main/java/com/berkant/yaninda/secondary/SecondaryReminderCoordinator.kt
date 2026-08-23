package com.berkant.yaninda.secondary

import com.berkant.yaninda.core.time.TimeProvider
import com.berkant.yaninda.data.device.DeviceIdentityRepository
import com.berkant.yaninda.domain.family.DeviceRole
import com.berkant.yaninda.domain.family.FamilyDoseOccurrence
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SecondaryReminderRuntimeStatus(
    val enabled: Boolean = false,
    val cachedOccurrenceCount: Int = 0,
    val scheduledOccurrenceCount: Int = 0,
    val approximateOccurrenceCount: Int = 0,
    val nextReminderAt: Instant? = null,
    val cacheUpdatedAt: Instant? = null,
    val hasSchedulingFailure: Boolean = false,
)

class SecondaryReminderCoordinator(
    private val cacheRepository: SecondaryReminderCacheRepository,
    private val settingsRepository: SecondaryReminderSettingsRepository,
    private val deviceIdentityRepository: DeviceIdentityRepository,
    private val scheduler: SecondaryReminderScheduler,
    private val notifier: SecondaryReminderNotifier,
    private val timeProvider: TimeProvider,
) {
    private val operationMutex = Mutex()
    private val mutableStatus = MutableStateFlow(SecondaryReminderRuntimeStatus())
    val status: StateFlow<SecondaryReminderRuntimeStatus> = mutableStatus.asStateFlow()

    suspend fun restoreFromCache(): SecondaryReminderRuntimeStatus = operationMutex.withLock {
        refreshCachedSchedule()
    }

    suspend fun updateFromRemote(
        familyId: String,
        occurrences: List<FamilyDoseOccurrence>,
    ): SecondaryReminderRuntimeStatus = operationMutex.withLock {
        val pairing = caregiverPairing()
        if (pairing?.familyId != familyId) return@withLock refreshCachedSchedule()
        val replacement = cacheRepository.replaceSchedule(
            familyId = familyId,
            occurrences = occurrences,
            now = timeProvider.now(),
        )
        replacement.previousOccurrenceIds.forEach(scheduler::cancel)
        scheduleCached(replacement.cachedOccurrences)
    }

    suspend fun setEnabled(enabled: Boolean): SecondaryReminderRuntimeStatus =
        operationMutex.withLock {
            settingsRepository.setEnabled(enabled)
            refreshCachedSchedule()
        }

    suspend fun clearForSignOut() = operationMutex.withLock {
        settingsRepository.setEnabled(false)
        cacheRepository.clear().forEach(scheduler::cancel)
        mutableStatus.value = SecondaryReminderRuntimeStatus()
    }

    suspend fun deliver(occurrenceId: String): Boolean = operationMutex.withLock {
        if (!settingsRepository.isEnabled() || caregiverPairing() == null) return@withLock false
        val cached = cacheRepository.remove(occurrenceId) ?: return@withLock false
        scheduler.cancel(occurrenceId)
        val now = timeProvider.now()
        val deliveryIsCurrent = cached.scheduledAt <= now.plus(EARLY_DELIVERY_TOLERANCE) &&
            cached.scheduledAt >= now.minus(MAXIMUM_LATE_DELIVERY)
        val delivered = deliveryIsCurrent && notifier.show(
            occurrenceId = occurrenceId,
            scheduledAt = cached.scheduledAt,
        )
        refreshCachedSchedule()
        delivered
    }

    private suspend fun refreshCachedSchedule(): SecondaryReminderRuntimeStatus {
        val cached = cacheRepository.all()
        cached.forEach { scheduler.cancel(it.occurrenceId) }
        if (caregiverPairing() == null) {
            cacheRepository.clear()
            return SecondaryReminderRuntimeStatus().also { mutableStatus.value = it }
        }
        return scheduleCached(cached)
    }

    private suspend fun scheduleCached(
        cached: List<CachedSecondaryReminder>,
    ): SecondaryReminderRuntimeStatus {
        val enabled = settingsRepository.isEnabled()
        val now = timeProvider.now()
        val restorable = cached.filter { occurrence ->
            occurrence.scheduledAt >= now.minus(MAXIMUM_LATE_DELIVERY)
        }
        val expiredIds = cached.asSequence()
            .filterNot(restorable::contains)
            .map(CachedSecondaryReminder::occurrenceId)
            .toList()
        expiredIds.forEach { occurrenceId ->
            cacheRepository.remove(occurrenceId)
            scheduler.cancel(occurrenceId)
        }
        var scheduledCount = 0
        var approximateCount = 0
        var hasFailure = false
        if (enabled) {
            restorable.forEach { occurrence ->
                val triggerAt = if (occurrence.scheduledAt > now) {
                    occurrence.scheduledAt
                } else {
                    now.plus(RECOVERY_DELAY)
                }
                when (scheduler.schedule(occurrence.occurrenceId, triggerAt)) {
                    SecondaryReminderSchedulingResult.EXACT -> scheduledCount += 1
                    SecondaryReminderSchedulingResult.APPROXIMATE -> {
                        scheduledCount += 1
                        approximateCount += 1
                    }

                    SecondaryReminderSchedulingResult.FAILED -> hasFailure = true
                }
            }
        }
        return SecondaryReminderRuntimeStatus(
            enabled = enabled,
            cachedOccurrenceCount = restorable.size,
            scheduledOccurrenceCount = scheduledCount,
            approximateOccurrenceCount = approximateCount,
            nextReminderAt = restorable.minOfOrNull(CachedSecondaryReminder::scheduledAt),
            cacheUpdatedAt = restorable.maxOfOrNull(CachedSecondaryReminder::syncedAt),
            hasSchedulingFailure = hasFailure,
        ).also { mutableStatus.value = it }
    }

    private suspend fun caregiverPairing() = deviceIdentityRepository.pairing.first()
        ?.takeIf { pairing -> pairing.deviceRole == DeviceRole.ADMIN_DEVICE }

    private companion object {
        val MAXIMUM_LATE_DELIVERY: Duration = Duration.ofMinutes(60)
        val EARLY_DELIVERY_TOLERANCE: Duration = Duration.ofMinutes(1)
        val RECOVERY_DELAY: Duration = Duration.ofSeconds(5)
    }
}
