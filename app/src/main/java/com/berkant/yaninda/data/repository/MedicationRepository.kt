package com.berkant.yaninda.data.repository

import com.berkant.yaninda.data.local.MedicationDao
import com.berkant.yaninda.data.local.MedicationEntity
import com.berkant.yaninda.data.local.MedicationScheduleEntity
import com.berkant.yaninda.data.local.MedicationWithSchedulesEntity
import com.berkant.yaninda.core.time.TimeProvider
import com.berkant.yaninda.domain.medication.DayOfWeekMask
import com.berkant.yaninda.domain.medication.Medication
import com.berkant.yaninda.domain.medication.MedicationConfiguration
import com.berkant.yaninda.domain.medication.MedicationSchedule
import com.berkant.yaninda.domain.medication.MedicationScheduleType
import com.berkant.yaninda.domain.medication.ValidatedMedicationDraft
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface MedicationRepository {
    val configurations: Flow<List<MedicationConfiguration>>

    suspend fun get(medicationId: String): MedicationConfiguration?

    suspend fun save(draft: ValidatedMedicationDraft)

    suspend fun deactivate(medicationId: String)
}

class RoomMedicationRepository(
    private val medicationDao: MedicationDao,
    private val timeProvider: TimeProvider,
) : MedicationRepository {
    override val configurations: Flow<List<MedicationConfiguration>> =
        medicationDao.observeConfigurations().map { records -> records.map { it.toDomain() } }

    override suspend fun get(medicationId: String): MedicationConfiguration? =
        medicationDao.getConfiguration(medicationId)?.toDomain()

    override suspend fun save(draft: ValidatedMedicationDraft) {
        val existing = draft.medicationId?.let { medicationId ->
            medicationDao.getConfiguration(medicationId)
                ?: error("The medication being edited no longer exists.")
        }
        val now = timeProvider.now()
        val nowMillis = now.toEpochMilli()
        val medicationId = existing?.medication?.id ?: UUID.randomUUID().toString()
        val medication = MedicationEntity(
            id = medicationId,
            displayName = draft.displayName,
            dosageText = draft.dosageText,
            instructionText = draft.instructionText,
            photoUri = existing?.medication?.photoUri,
            scheduleType = MedicationScheduleType.FIXED_ONLY,
            active = existing?.medication?.active ?: true,
            createdAtEpochMillis = existing?.medication?.createdAtEpochMillis ?: nowMillis,
            updatedAtEpochMillis = nowMillis,
            version = (existing?.medication?.version ?: 0L) + 1L,
        )
        val existingSchedules = existing?.schedules.orEmpty().associateBy { it.id }
        val todayEpochDay = now.atZone(timeProvider.currentZoneId()).toLocalDate().toEpochDay()
        val schedules = draft.schedules.map { scheduleDraft ->
            val previous = scheduleDraft.id?.let { scheduleId ->
                existingSchedules[scheduleId]
                    ?: error("The medication schedule being edited no longer exists.")
            }
            MedicationScheduleEntity(
                id = previous?.id ?: UUID.randomUUID().toString(),
                medicationId = medicationId,
                localTimeMinutes = scheduleDraft.localTime.toSecondOfDay() / 60,
                daysOfWeekMask = DayOfWeekMask.encode(draft.daysOfWeek),
                validFromEpochDay = previous?.validFromEpochDay ?: todayEpochDay,
                validUntilEpochDay = previous?.validUntilEpochDay,
                snoozeEnabled = draft.snoozeEnabled,
                snoozeMinutes = draft.snoozeMinutes,
                maxSnoozes = draft.maxSnoozes,
                createdAtEpochMillis = previous?.createdAtEpochMillis ?: nowMillis,
                updatedAtEpochMillis = nowMillis,
                version = (previous?.version ?: 0L) + 1L,
            )
        }

        medicationDao.replaceConfiguration(medication, schedules)
    }

    override suspend fun deactivate(medicationId: String) {
        val changedRows = medicationDao.deactivate(medicationId, timeProvider.now().toEpochMilli())
        check(changedRows == 1) { "The medication is missing or already inactive." }
    }
}

internal fun MedicationWithSchedulesEntity.toDomain(): MedicationConfiguration =
    MedicationConfiguration(
        medication = Medication(
            id = medication.id,
            displayName = medication.displayName,
            dosageText = medication.dosageText,
            instructionText = medication.instructionText,
            photoUri = medication.photoUri,
            scheduleType = medication.scheduleType,
            active = medication.active,
            createdAt = Instant.ofEpochMilli(medication.createdAtEpochMillis),
            updatedAt = Instant.ofEpochMilli(medication.updatedAtEpochMillis),
            version = medication.version,
        ),
        schedules = schedules
            .sortedBy { it.localTimeMinutes }
            .map { schedule ->
                MedicationSchedule(
                    id = schedule.id,
                    medicationId = schedule.medicationId,
                    localTime = LocalTime.ofSecondOfDay(schedule.localTimeMinutes.toLong() * 60L),
                    daysOfWeek = DayOfWeekMask.decode(schedule.daysOfWeekMask),
                    validFrom = LocalDate.ofEpochDay(schedule.validFromEpochDay),
                    validUntil = schedule.validUntilEpochDay?.let(LocalDate::ofEpochDay),
                    snoozeEnabled = schedule.snoozeEnabled,
                    snoozeMinutes = schedule.snoozeMinutes,
                    maxSnoozes = schedule.maxSnoozes,
                    createdAt = Instant.ofEpochMilli(schedule.createdAtEpochMillis),
                    updatedAt = Instant.ofEpochMilli(schedule.updatedAtEpochMillis),
                    version = schedule.version,
                )
            },
    )
