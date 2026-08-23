package com.berkant.yaninda.secondary

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecondaryReminderIntentFactoryTest {
    @Test
    fun pendingIntent_isStableUniqueAndSeparateFromPrimaryAlarmIdentity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = SecondaryReminderIntentFactory.broadcast(context, "occurrence-1")
        val same = SecondaryReminderIntentFactory.broadcast(context, "occurrence-1")
        val different = SecondaryReminderIntentFactory.broadcast(context, "occurrence-2")

        try {
            assertEquals(first, same)
            assertNotEquals(first, different)
        } finally {
            first.cancel()
            different.cancel()
        }
    }
}
