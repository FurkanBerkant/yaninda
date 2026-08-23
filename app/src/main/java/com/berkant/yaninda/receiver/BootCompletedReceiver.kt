package com.berkant.yaninda.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.berkant.yaninda.YanindaApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val application = context.applicationContext as? YanindaApplication
        if (application == null) {
            Log.e(TAG, "Boot restoration could not access the application container.")
            return
        }

        val pendingResult = goAsync()
        application.applicationScope.launch {
            try {
                application.reminderCoordinator.refreshUpcoming()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                Log.e(TAG, "Medication alarms could not be restored after reboot.")
            }
            try {
                application.secondaryReminderCoordinator.restoreFromCache()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                Log.e(TAG, "Secondary caregiver reminders could not be restored after reboot.")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootAlarmRestore"
    }
}
