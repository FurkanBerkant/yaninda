package com.berkant.yaninda.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.berkant.yaninda.YanindaApplication
import com.berkant.yaninda.secondary.SecondaryReminderIntentFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class SecondaryReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val occurrenceId = SecondaryReminderIntentFactory.occurrenceId(intent) ?: return
        val application = context.applicationContext as? YanindaApplication ?: return
        val pendingResult = goAsync()
        application.applicationScope.launch {
            try {
                application.secondaryReminderCoordinator.deliver(occurrenceId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                Log.e(TAG, "Secondary caregiver reminder could not be delivered.")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "SecondaryReminder"
    }
}
