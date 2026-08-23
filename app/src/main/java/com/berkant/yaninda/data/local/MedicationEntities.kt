package com.berkant.yaninda.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.TypeConverter
import com.berkant.yaninda.domain.medication.MedicationScheduleType
import com.berkant.yaninda.domain.occurrence.AcknowledgementActor
import com.berkant.yaninda.domain.occurrence.DoseOccurrenceStatus
import com.berkant.yaninda.domain.sync.SyncEventType
import com.berkant.yaninda.domain.sync.SyncState

@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val dosageText: String,
    val instructionText: String,
    val photoUri: String?,
    val scheduleType: MedicationScheduleType,
    val active: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val version: Long,
)

@Entity(
    tableName = "medication_schedules",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("medicationId")],
)
data class MedicationScheduleEntity(
    @PrimaryKey val id: String,
    val medicationId: String,
    val localTimeMinutes: Int,
    val daysOfWeekMask: Int,
    val validFromEpochDay: Long,
    val validUntilEpochDay: Long?,
    val snoozeEnabled: Boolean,
    val snoozeMinutes: Int,
    val maxSnoozes: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val version: Long,
)

data class MedicationWithSchedulesEntity(
    @Embedded val medication: MedicationEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "medicationId",
    )
    val schedules: List<MedicationScheduleEntity>,
)

class MedicationTypeConverters {
    @TypeConverter
    fun scheduleTypeToString(value: MedicationScheduleType): String = value.name

    @TypeConverter
    fun stringToScheduleType(value: String): MedicationScheduleType =
        MedicationScheduleType.valueOf(value)

    @TypeConverter
    fun occurrenceStatusToString(value: DoseOccurrenceStatus): String = value.name

    @TypeConverter
    fun stringToOccurrenceStatus(value: String): DoseOccurrenceStatus =
        DoseOccurrenceStatus.valueOf(value)

    @TypeConverter
    fun acknowledgementActorToString(value: AcknowledgementActor?): String? = value?.name

    @TypeConverter
    fun stringToAcknowledgementActor(value: String?): AcknowledgementActor? =
        value?.let(AcknowledgementActor::valueOf)

    @TypeConverter
    fun syncEventTypeToString(value: SyncEventType): String = value.name

    @TypeConverter
    fun stringToSyncEventType(value: String): SyncEventType = SyncEventType.valueOf(value)

    @TypeConverter
    fun syncStateToString(value: SyncState): String = value.name

    @TypeConverter
    fun stringToSyncState(value: String): SyncState = SyncState.valueOf(value)
}
