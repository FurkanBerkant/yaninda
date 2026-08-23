package com.berkant.yaninda.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.berkant.yaninda.domain.medication.MedicationScheduleType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MedicationDaoTest {
    private lateinit var database: YanindaDatabase
    private lateinit var dao: MedicationDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, YanindaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.medicationDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun replaceConfigurationAndDeactivate_areAtomicLocalChanges() = runBlocking {
        val medication = MedicationEntity(
            id = "medication-1",
            displayName = "Şeker İlacı",
            dosageText = "1 tablet",
            instructionText = "Yemekten sonra",
            photoUri = null,
            scheduleType = MedicationScheduleType.FIXED_ONLY,
            active = true,
            createdAtEpochMillis = 100L,
            updatedAtEpochMillis = 100L,
            version = 1L,
        )
        val schedule = MedicationScheduleEntity(
            id = "schedule-1",
            medicationId = medication.id,
            localTimeMinutes = 20 * 60,
            daysOfWeekMask = 127,
            validFromEpochDay = 1L,
            validUntilEpochDay = null,
            snoozeEnabled = false,
            snoozeMinutes = 10,
            maxSnoozes = 1,
            createdAtEpochMillis = 100L,
            updatedAtEpochMillis = 100L,
            version = 1L,
        )

        dao.replaceConfiguration(medication, listOf(schedule))
        assertEquals(1, dao.observeConfigurations().first().single().schedules.size)

        assertEquals(1, dao.deactivate(medication.id, 200L))
        assertFalse(dao.observeConfigurations().first().single().medication.active)
    }
}
