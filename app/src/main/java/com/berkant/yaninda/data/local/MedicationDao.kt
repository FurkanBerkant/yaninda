package com.berkant.yaninda.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class MedicationDao {

    @Transaction
    @Query(
        "SELECT * FROM medications " +
                "ORDER BY active DESC, updatedAtEpochMillis DESC"
    )
    abstract fun observeConfigurations():
            Flow<List<MedicationWithSchedulesEntity>>

    @Transaction
    @Query(
        "SELECT * FROM medications " +
                "WHERE id = :medicationId"
    )
    abstract suspend fun getConfiguration(
        medicationId: String,
    ): MedicationWithSchedulesEntity?

    @Transaction
    @Query(
        "SELECT * FROM medications " +
                "WHERE active = 1 ORDER BY id"
    )
    abstract suspend fun getActiveConfigurations():
            List<MedicationWithSchedulesEntity>

    @Upsert
    protected abstract suspend fun upsertMedication(
        medication: MedicationEntity,
    )

    @Upsert
    protected abstract suspend fun upsertSchedules(
        schedules: List<MedicationScheduleEntity>,
    )

    @Query(
        "DELETE FROM medication_schedules " +
                "WHERE medicationId = :medicationId " +
                "AND id NOT IN (:retainedScheduleIds)"
    )
    protected abstract suspend fun deleteObsoleteSchedules(
        medicationId: String,
        retainedScheduleIds: List<String>,
    )

    @Query(
        """
        UPDATE medications
        SET active = 0,
            updatedAtEpochMillis = :updatedAtEpochMillis,
            version = version + 1
        WHERE active = 1
          AND id NOT IN (:retainedMedicationIds)
        """
    )
    protected abstract suspend fun deactivateMissingMedications(
        retainedMedicationIds: List<String>,
        updatedAtEpochMillis: Long,
    )

    @Query(
        """
        UPDATE medications
        SET active = 0,
            updatedAtEpochMillis = :updatedAtEpochMillis,
            version = version + 1
        WHERE active = 1
        """
    )
    protected abstract suspend fun deactivateAllMedications(
        updatedAtEpochMillis: Long,
    )

    @Transaction
    open suspend fun replaceConfiguration(
        medication: MedicationEntity,
        schedules: List<MedicationScheduleEntity>,
    ) {
        require(schedules.isNotEmpty()) {
            "A fixed medication needs at least one schedule."
        }

        upsertMedication(medication)

        deleteObsoleteSchedules(
            medicationId = medication.id,
            retainedScheduleIds =
                schedules.map { it.id },
        )

        upsertSchedules(schedules)
    }

    /**
     * Replaces the complete authoritative medication snapshot downloaded
     * from the family schedule.
     *
     * Everything happens inside one Room transaction:
     *
     * - incoming medications are upserted
     * - incoming schedules replace previous schedules
     * - medications missing from the new snapshot are deactivated
     *
     * Existing medication rows are intentionally not deleted because old
     * dose occurrences may still reference them.
     */
    @Transaction
    open suspend fun applyRemoteSnapshot(
        medications: List<MedicationEntity>,
        schedulesByMedication:
        Map<String, List<MedicationScheduleEntity>>,
        updatedAtEpochMillis: Long,
    ) {
        val medicationIds =
            medications.map { it.id }

        require(
            medicationIds.size ==
                    medicationIds.toSet().size
        ) {
            "Remote medication snapshot contains duplicate medication IDs."
        }

        medications.forEach { medication ->
            val schedules =
                requireNotNull(
                    schedulesByMedication[medication.id]
                ) {
                    "Remote medication schedules are missing."
                }

            require(schedules.isNotEmpty()) {
                "A fixed medication needs at least one schedule."
            }

            upsertMedication(medication)

            deleteObsoleteSchedules(
                medicationId = medication.id,
                retainedScheduleIds =
                    schedules.map { it.id },
            )

            upsertSchedules(schedules)
        }

        if (medicationIds.isEmpty()) {
            deactivateAllMedications(
                updatedAtEpochMillis
            )
        } else {
            deactivateMissingMedications(
                retainedMedicationIds =
                    medicationIds,
                updatedAtEpochMillis =
                    updatedAtEpochMillis,
            )
        }
    }

    @Query(
        "UPDATE medications " +
                "SET active = 0, " +
                "updatedAtEpochMillis = :updatedAtEpochMillis, " +
                "version = version + 1 " +
                "WHERE id = :medicationId AND active = 1"
    )
    abstract suspend fun deactivate(
        medicationId: String,
        updatedAtEpochMillis: Long,
    ): Int
}