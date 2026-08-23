package com.berkant.yaninda.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        MedicationEntity::class,
        MedicationScheduleEntity::class,
        DoseOccurrenceEntity::class,
        SyncOutboxEntity::class,
        SecondaryReminderCacheEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(MedicationTypeConverters::class)
abstract class YanindaDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao

    abstract fun doseOccurrenceDao(): DoseOccurrenceDao

    abstract fun syncOutboxDao(): SyncOutboxDao

    abstract fun secondaryReminderCacheDao(): SecondaryReminderCacheDao
}
