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
        val action =
            intent.action
                ?: return

        if (action !in SUPPORTED_ACTIONS) {
            return
        }

        val application = context.applicationContext as? YanindaApplication
        if (application == null) {
            Log.e(
                TAG,
                "Alarm schedule restoration could not access the application container. action=$action",
            )
            return
        }

        val pendingResult = goAsync()
        application.applicationScope.launch {
            try {
                /*
                 * refreshUpcoming() recalculates wall-clock medication
                 * schedules using TimeProvider.currentZoneId().
                 *
                 * This is needed after:
                 * - reboot (AlarmManager entries are lost)
                 * - manual device time changes
                 * - timezone changes
                 */
                if (!application.isConfiguredAlarmDevice()) {
                    Log.i(
                        TAG,
                        "Alarm schedule restore ignored because this is not a configured ALARM_DEVICE. action=$action",
                    )
                    return@launch
                }

                application.reminderCoordinator.refreshUpcoming()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                Log.e(
                    TAG,
                    "Medication alarms could not be recalculated. action=$action",
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "AlarmScheduleRestore"

        private val SUPPORTED_ACTIONS =
            setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
            )
    }
}
