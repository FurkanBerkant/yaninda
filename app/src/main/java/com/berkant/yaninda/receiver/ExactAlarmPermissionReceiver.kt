package com.berkant.yaninda.receiver

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.berkant.yaninda.YanindaApplication
import com.berkant.yaninda.reminder.ExactAlarmCapability
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class ExactAlarmPermissionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) {
            return
        }
        val application = context.applicationContext as? YanindaApplication
        if (application == null) {
            Log.e(TAG, "Exact-alarm permission change could not access the application container.")
            return
        }
        if (
            application.reminderScheduler.exactAlarmCapability() !=
            ExactAlarmCapability.AVAILABLE
        ) {
            return
        }

        val pendingResult = goAsync()
        application.applicationScope.launch {
            try {
                application.reminderCoordinator.refreshUpcoming()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                Log.e(TAG, "Alarms could not be restored after exact-alarm access changed.")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ExactAlarmAccess"
    }
}
