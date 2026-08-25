package com.berkant.yaninda.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.berkant.yaninda.domain.sync.SyncEventIdFactory
import com.berkant.yaninda.domain.sync.SyncEventType

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `dose_occurrences` (
                `id` TEXT NOT NULL,
                `medicationId` TEXT NOT NULL,
                `scheduleId` TEXT NOT NULL,
                `scheduledAtEpochMillis` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `acknowledgedAtEpochMillis` INTEGER,
                `acknowledgementActor` TEXT,
                `snoozeCount` INTEGER NOT NULL,
                `lastAlertedAtEpochMillis` INTEGER,
                `createdAtEpochMillis` INTEGER NOT NULL,
                `updatedAtEpochMillis` INTEGER NOT NULL,
                `version` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`medicationId`) REFERENCES `medications`(`id`)
                    ON UPDATE NO ACTION ON DELETE NO ACTION
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_dose_occurrences_medicationId` " +
                "ON `dose_occurrences` (`medicationId`)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_dose_occurrences_scheduleId_scheduledAtEpochMillis` " +
                "ON `dose_occurrences` (`scheduleId`, `scheduledAtEpochMillis`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_dose_occurrences_status_scheduledAtEpochMillis` " +
                "ON `dose_occurrences` (`status`, `scheduledAtEpochMillis`)"
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `dose_occurrences` " +
                "ADD COLUMN `nextReminderAtEpochMillis` INTEGER"
        )
        db.execSQL(
            "UPDATE `dose_occurrences` " +
                "SET `nextReminderAtEpochMillis` = `scheduledAtEpochMillis` " +
                "WHERE `status` = 'SCHEDULED'"
        )
        db.execSQL(
            """
            UPDATE `dose_occurrences`
            SET `nextReminderAtEpochMillis` = `updatedAtEpochMillis` + (
                SELECT `snoozeMinutes` * 60000
                FROM `medication_schedules`
                WHERE `medication_schedules`.`id` = `dose_occurrences`.`scheduleId`
            )
            WHERE `status` = 'SNOOZED'
              AND EXISTS (
                  SELECT 1
                  FROM `medication_schedules`
                  WHERE `medication_schedules`.`id` = `dose_occurrences`.`scheduleId`
              )
            """.trimIndent()
        )
        db.execSQL(
            "UPDATE `dose_occurrences` " +
                "SET `status` = 'CANCELLED' " +
                "WHERE `status` = 'SNOOZED' AND `nextReminderAtEpochMillis` IS NULL"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS " +
                "`index_dose_occurrences_status_nextReminderAtEpochMillis` " +
                "ON `dose_occurrences` (`status`, `nextReminderAtEpochMillis`)"
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sync_outbox` (
                `id` TEXT NOT NULL,
                `eventType` TEXT NOT NULL,
                `aggregateId` TEXT NOT NULL,
                `aggregateVersion` INTEGER NOT NULL,
                `payloadVersion` INTEGER NOT NULL,
                `createdAtEpochMillis` INTEGER NOT NULL,
                `attemptCount` INTEGER NOT NULL,
                `lastAttemptAtEpochMillis` INTEGER,
                `syncState` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_sync_outbox_eventType_aggregateId_aggregateVersion` " +
                "ON `sync_outbox` (`eventType`, `aggregateId`, `aggregateVersion`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_sync_outbox_syncState_createdAtEpochMillis` " +
                "ON `sync_outbox` (`syncState`, `createdAtEpochMillis`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_sync_outbox_aggregateId` " +
                "ON `sync_outbox` (`aggregateId`)"
        )

        val eventType = SyncEventType.DOSE_OCCURRENCE_ACKNOWLEDGED.name
        db.execSQL(
            """
            INSERT INTO `sync_outbox` (
                `id`, `eventType`, `aggregateId`, `aggregateVersion`, `payloadVersion`,
                `createdAtEpochMillis`, `attemptCount`, `lastAttemptAtEpochMillis`, `syncState`
            )
            SELECT
                '$eventType:' || `id` || '${SyncEventIdFactory.VERSION_SEPARATOR}' || `version`,
                '$eventType',
                `id`,
                `version`,
                1,
                COALESCE(`acknowledgedAtEpochMillis`, `updatedAtEpochMillis`),
                0,
                NULL,
                'PENDING'
            FROM `dose_occurrences`
            WHERE `status` = 'ACKNOWLEDGED_TAKEN'
            """.trimIndent()
        )
    }
}

val MIGRATION_4_5 =
    object : Migration(4, 5) {

        override fun migrate(
            db: SupportSQLiteDatabase,
        ) {
            /*
             * Version 5 artık legacy secondary reminder
             * mimarisini içermiyor.
             *
             * Version 4 ve version 5'in aktif Room
             * tabloları aynıdır; bu nedenle schema
             * değişikliği gerekmiyor.
             */
        }
    }

val MIGRATION_5_6 =
    object : Migration(5, 6) {
        override fun migrate(
            db: SupportSQLiteDatabase,
        ) {
            db.execSQL(
                "ALTER TABLE `dose_occurrences` " +
                    "ADD COLUMN `automaticRetryCount` INTEGER NOT NULL DEFAULT 0"
            )
        }
    }
