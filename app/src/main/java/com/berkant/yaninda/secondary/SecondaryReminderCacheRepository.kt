package com.berkant.yaninda.secondary

import androidx.room.withTransaction
import com.berkant.yaninda.data.local.SecondaryReminderCacheDao
import com.berkant.yaninda.data.local.SecondaryReminderCacheEntity
import com.berkant.yaninda.data.local.YanindaDatabase
import com.berkant.yaninda.domain.family.FamilyDoseOccurrence
import com.berkant.yaninda.domain.occurrence.DoseOccurrenceStatus
import java.time.Instant

data class CachedSecondaryReminder(
    val occurrenceId: String,
    val familyId: String,
    val scheduledAt: Instant,
    val syncedAt: Instant,
    val sourceDeviceId: String,
    val version: Long,
)

data class SecondaryCacheReplacement(
    val previousOccurrenceIds: List<String>,
    val cachedOccurrences: List<CachedSecondaryReminder>,
)

interface SecondaryReminderCacheRepository {
    suspend fun all(): List<CachedSecondaryReminder>

    suspend fun replaceSchedule(
        familyId: String,
        occurrences: List<FamilyDoseOccurrence>,
        now: Instant,
    ): SecondaryCacheReplacement

    suspend fun remove(occurrenceId: String): CachedSecondaryReminder?

    suspend fun clear(): List<String>
}

class RoomSecondaryReminderCacheRepository(
    private val database: YanindaDatabase,
    private val dao: SecondaryReminderCacheDao,
) : SecondaryReminderCacheRepository {
    override suspend fun all(): List<CachedSecondaryReminder> =
        dao.getAll().map { entity -> entity.toDomain() }

    override suspend fun replaceSchedule(
        familyId: String,
        occurrences: List<FamilyDoseOccurrence>,
        now: Instant,
    ): SecondaryCacheReplacement = database.withTransaction {
        require(familyId.isNotBlank() && familyId.length <= MAX_ID_LENGTH && '/' !in familyId) {
            "Family identity is invalid."
        }
        val previous = dao.getAll()
        val cacheable = occurrences.asSequence()
            .filter { occurrence ->
                occurrence.status == DoseOccurrenceStatus.SCHEDULED &&
                    occurrence.scheduledAt > now &&
                    occurrence.occurrenceId.isValidId() &&
                    occurrence.sourceDeviceId.isValidId() &&
                    occurrence.version > 0
            }
            .distinctBy(FamilyDoseOccurrence::occurrenceId)
            .sortedBy(FamilyDoseOccurrence::scheduledAt)
            .take(MAX_CACHED_OCCURRENCES)
            .map { occurrence ->
                SecondaryReminderCacheEntity(
                    occurrenceId = occurrence.occurrenceId,
                    familyId = familyId,
                    scheduledAtEpochMillis = occurrence.scheduledAt.toEpochMilli(),
                    syncedAtEpochMillis = occurrence.syncedAt.toEpochMilli(),
                    sourceDeviceId = occurrence.sourceDeviceId,
                    version = occurrence.version,
                )
            }
            .toList()
        dao.clear()
        if (cacheable.isNotEmpty()) dao.insertAll(cacheable)
        SecondaryCacheReplacement(
            previousOccurrenceIds = previous.map(SecondaryReminderCacheEntity::occurrenceId),
            cachedOccurrences = cacheable.map { entity -> entity.toDomain() },
        )
    }

    override suspend fun remove(occurrenceId: String): CachedSecondaryReminder? =
        database.withTransaction {
            val cached = dao.getById(occurrenceId) ?: return@withTransaction null
            if (dao.deleteById(occurrenceId) != 1) return@withTransaction null
            cached.toDomain()
        }

    override suspend fun clear(): List<String> = database.withTransaction {
        val occurrenceIds = dao.getAll().map(SecondaryReminderCacheEntity::occurrenceId)
        dao.clear()
        occurrenceIds
    }

    private fun String.isValidId(): Boolean =
        isNotBlank() && length <= MAX_ID_LENGTH && '/' !in this

    private fun SecondaryReminderCacheEntity.toDomain() = CachedSecondaryReminder(
        occurrenceId = occurrenceId,
        familyId = familyId,
        scheduledAt = Instant.ofEpochMilli(scheduledAtEpochMillis),
        syncedAt = Instant.ofEpochMilli(syncedAtEpochMillis),
        sourceDeviceId = sourceDeviceId,
        version = version,
    )

    private companion object {
        const val MAX_ID_LENGTH = 128
        const val MAX_CACHED_OCCURRENCES = 32
    }
}
