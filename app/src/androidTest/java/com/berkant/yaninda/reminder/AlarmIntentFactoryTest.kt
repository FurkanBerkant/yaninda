package com.berkant.yaninda.reminder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmIntentFactoryTest {
    @Test
    fun occurrencePendingIntent_isStableAndUniquePerOccurrence() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = AlarmIntentFactory.occurrenceBroadcast(context, "occurrence-1")
        val same = AlarmIntentFactory.occurrenceBroadcast(context, "occurrence-1")
        val different = AlarmIntentFactory.occurrenceBroadcast(context, "occurrence-2")

        try {
            assertEquals(first, same)
            assertNotEquals(first, different)
        } finally {
            first.cancel()
            different.cancel()
        }
    }

    @Test
    fun testPendingIntent_doesNotCollideWithMedicationOccurrence() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val occurrence = AlarmIntentFactory.occurrenceBroadcast(context, "test")
        val test = AlarmIntentFactory.testBroadcast(context)

        try {
            assertNotEquals(occurrence, test)
        } finally {
            occurrence.cancel()
            test.cancel()
        }
    }

    @Test
    fun responseWindowPendingIntent_isStableUniqueAndSeparateFromReminder() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = AlarmIntentFactory.responseWindowBroadcast(context, "occurrence-1")
        val same = AlarmIntentFactory.responseWindowBroadcast(context, "occurrence-1")
        val different = AlarmIntentFactory.responseWindowBroadcast(context, "occurrence-2")
        val medication = AlarmIntentFactory.occurrenceBroadcast(context, "occurrence-1")

        try {
            assertEquals(first, same)
            assertNotEquals(first, different)
            assertNotEquals(first, medication)
        } finally {
            first.cancel()
            different.cancel()
            medication.cancel()
        }
    }

    @Test
    fun alarmActivityPendingIntent_isStableAndUniquePerOccurrence() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = AlarmIntentFactory.medicationAlarmActivity(context, "occurrence-1")
        val same = AlarmIntentFactory.medicationAlarmActivity(context, "occurrence-1")
        val different = AlarmIntentFactory.medicationAlarmActivity(context, "occurrence-2")
        val test = AlarmIntentFactory.testAlarmActivity(context)

        try {
            assertEquals(first, same)
            assertNotEquals(first, different)
            assertNotEquals(first, test)
        } finally {
            first.cancel()
            different.cancel()
            test.cancel()
        }
    }
}
