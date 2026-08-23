package com.berkant.yaninda.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.berkant.yaninda.domain.occurrence.AcknowledgementActor
import com.berkant.yaninda.domain.occurrence.DoseOccurrenceStatus

@Entity(
    tableName = "dose_occurrences",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index("medicationId"),
        Index(value = ["scheduleId", "scheduledAtEpochMillis"], unique = true),
        Index(value = ["status", "scheduledAtEpochMillis"]),
        Index(value = ["status", "nextReminderAtEpochMillis"]),
    ],
)
data class DoseOccurrenceEntity(
    @PrimaryKey val id: String,
    val medicationId: String,
    // Intentionally not a foreign key: editing a schedule must not erase occurrence history.
    val scheduleId: String,
    val scheduledAtEpochMillis: Long,
    val status: DoseOccurrenceStatus,
    val acknowledgedAtEpochMillis: Long?,
    val acknowledgementActor: AcknowledgementActor?,
    val snoozeCount: Int,
    val lastAlertedAtEpochMillis: Long?,
    val nextReminderAtEpochMillis: Long?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val version: Long,
)
