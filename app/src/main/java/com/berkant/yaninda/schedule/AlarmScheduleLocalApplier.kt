package com.berkant.yaninda.schedule

import com.berkant.yaninda.core.time.TimeProvider
import com.berkant.yaninda.data.local.MedicationDao
import com.berkant.yaninda.data.local.MedicationEntity
import com.berkant.yaninda.data.local.MedicationScheduleEntity
import com.berkant.yaninda.domain.medication.DayOfWeekMask
import com.berkant.yaninda.domain.medication.MedicationDraftValidator
import com.berkant.yaninda.domain.medication.MedicationScheduleType

class AlarmScheduleLocalApplier(
    private val medicationDao: MedicationDao,
    private val timeProvider: TimeProvider,
) {

    suspend fun apply(
        schedule: PublishedScheduleVersion,
    ) {
        validate(schedule)

        val now = timeProvider.now()
        val nowMillis = now.toEpochMilli()

        val todayEpochDay =
            now
                .atZone(
                    timeProvider.currentZoneId()
                )
                .toLocalDate()
                .toEpochDay()

        val medicationEntities =
            mutableListOf<MedicationEntity>()

        val schedulesByMedication =
            linkedMapOf<
                    String,
                    List<MedicationScheduleEntity>,
                    >()

        schedule.medications.forEach { remoteMedication ->

            val existing =
                medicationDao.getConfiguration(
                    remoteMedication.medicationId
                )

            val medicationEntity =
                MedicationEntity(
                    id =
                        remoteMedication.medicationId,
                    displayName =
                        remoteMedication.displayName,
                    dosageText =
                        remoteMedication.dosageText,
                    instructionText =
                        remoteMedication.instructionText,
                    photoUri =
                        existing
                            ?.medication
                            ?.photoUri,
                    scheduleType =
                        MedicationScheduleType.FIXED_ONLY,
                    active =
                        remoteMedication.active,
                    createdAtEpochMillis =
                        existing
                            ?.medication
                            ?.createdAtEpochMillis
                            ?: nowMillis,
                    updatedAtEpochMillis =
                        nowMillis,
                    version =
                        maxOf(
                            schedule.version,
                            (
                                    existing
                                        ?.medication
                                        ?.version
                                        ?: 0L
                                    ) + 1L,
                        ),
                )

            val existingSchedules =
                existing
                    ?.schedules
                    .orEmpty()
                    .associateBy { it.id }

            val localSchedules =
                remoteMedication.schedules.map {
                        remoteSchedule ->

                    val previous =
                        existingSchedules[
                            remoteSchedule.scheduleId
                        ]

                    MedicationScheduleEntity(
                        id =
                            remoteSchedule.scheduleId,
                        medicationId =
                            remoteMedication.medicationId,
                        localTimeMinutes =
                            remoteSchedule
                                .localTimeMinutes,
                        daysOfWeekMask =
                            remoteSchedule
                                .daysOfWeekMask,
                        validFromEpochDay =
                            previous
                                ?.validFromEpochDay
                                ?: todayEpochDay,
                        validUntilEpochDay =
                            previous
                                ?.validUntilEpochDay,
                        snoozeEnabled =
                            remoteSchedule.snoozeEnabled,
                        snoozeMinutes =
                            remoteSchedule.snoozeMinutes,
                        maxSnoozes =
                            remoteSchedule.maxSnoozes,
                        createdAtEpochMillis =
                            previous
                                ?.createdAtEpochMillis
                                ?: nowMillis,
                        updatedAtEpochMillis =
                            nowMillis,
                        version =
                            maxOf(
                                schedule.version,
                                (
                                        previous
                                            ?.version
                                            ?: 0L
                                        ) + 1L,
                            ),
                    )
                }

            medicationEntities += medicationEntity

            schedulesByMedication[
                remoteMedication.medicationId
            ] = localSchedules
        }

        medicationDao.applyRemoteSnapshot(
            medications = medicationEntities,
            schedulesByMedication =
                schedulesByMedication,
            updatedAtEpochMillis = nowMillis,
        )
    }

    private fun validate(
        schedule: PublishedScheduleVersion,
    ) {
        require(schedule.familyId.isNotBlank()) {
            "Schedule family ID is missing."
        }

        require(schedule.version > 0L) {
            "Schedule version must be positive."
        }

        require(schedule.medications.size <= 50) {
            "Schedule contains too many medications."
        }

        val medicationIds =
            mutableSetOf<String>()

        val scheduleIds =
            mutableSetOf<String>()

        schedule.medications.forEach { medication ->

            require(
                medication.medicationId.isNotBlank() &&
                        medication.medicationId.length <= 128
            ) {
                "Medication ID is invalid."
            }

            require(
                medicationIds.add(
                    medication.medicationId
                )
            ) {
                "Duplicate medication ID."
            }

            require(
                medication.displayName.isNotBlank() &&
                        medication.displayName.length <=
                        MedicationDraftValidator
                            .NAME_MAX_LENGTH
            ) {
                "Medication name is invalid."
            }

            require(
                medication.dosageText.isNotBlank() &&
                        medication.dosageText.length <=
                        MedicationDraftValidator
                            .DOSAGE_MAX_LENGTH
            ) {
                "Medication dosage text is invalid."
            }

            require(
                medication.instructionText.isNotBlank() &&
                        medication.instructionText.length <=
                        MedicationDraftValidator
                            .INSTRUCTION_MAX_LENGTH
            ) {
                "Medication instruction text is invalid."
            }

            require(
                medication.schedules.isNotEmpty()
            ) {
                "Medication has no fixed schedule."
            }

            medication.schedules.forEach {
                    remoteSchedule ->

                require(
                    remoteSchedule.scheduleId
                        .isNotBlank() &&
                            remoteSchedule.scheduleId
                                .length <= 128
                ) {
                    "Schedule ID is invalid."
                }

                require(
                    scheduleIds.add(
                        remoteSchedule.scheduleId
                    )
                ) {
                    "Duplicate schedule ID."
                }

                require(
                    remoteSchedule.localTimeMinutes
                            in 0..1439
                ) {
                    "Schedule time is invalid."
                }

                require(
                    remoteSchedule.daysOfWeekMask
                            in 1..127
                ) {
                    "Schedule day mask is invalid."
                }

                require(
                    DayOfWeekMask.decode(
                        remoteSchedule.daysOfWeekMask
                    ).isNotEmpty()
                ) {
                    "Schedule must contain at least one day."
                }

                require(
                    remoteSchedule.snoozeMinutes in
                            MedicationDraftValidator
                                .MIN_SNOOZE_MINUTES..
                            MedicationDraftValidator
                                .MAX_SNOOZE_MINUTES
                ) {
                    "Snooze minutes are invalid."
                }

                require(
                    remoteSchedule.maxSnoozes in
                            MedicationDraftValidator
                                .MIN_SNOOZE_COUNT..
                            MedicationDraftValidator
                                .MAX_SNOOZE_COUNT
                ) {
                    "Maximum snooze count is invalid."
                }
            }
        }
    }
}