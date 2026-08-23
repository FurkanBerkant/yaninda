package com.berkant.yaninda.secondary

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.berkant.yaninda.MainActivity
import com.berkant.yaninda.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

interface SecondaryReminderNotifier {
    fun ensureChannel()

    fun show(occurrenceId: String, scheduledAt: Instant): Boolean
}

class SecondaryReminderNotificationManager(
    context: Context,
) : SecondaryReminderNotifier {
    private val applicationContext = context.applicationContext
    private val notificationManager = NotificationManagerCompat.from(applicationContext)

    override fun ensureChannel() {
        applicationContext.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    applicationContext.getString(R.string.secondary_reminder_channel_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = applicationContext.getString(
                        R.string.secondary_reminder_channel_description
                    )
                    enableVibration(true)
                }
            )
    }

    override fun show(occurrenceId: String, scheduledAt: Instant): Boolean {
        if (!canNotify()) return false
        val scheduledTime = TIME_FORMAT.format(scheduledAt.atZone(ZoneId.systemDefault()))
        val contentIntent = PendingIntent.getActivity(
            applicationContext,
            CONTENT_REQUEST_CODE,
            Intent(applicationContext, MainActivity::class.java).apply {
                action = ACTION_OPEN_SECONDARY_REMINDER
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle(applicationContext.getString(R.string.secondary_reminder_title))
            .setContentText(
                applicationContext.getString(
                    R.string.secondary_reminder_notification_body,
                    scheduledTime,
                )
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        return try {
            notificationManager.notify(occurrenceId.hashCode(), notification)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }

    private fun canNotify(): Boolean {
        val runtimeAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        return runtimeAllowed && notificationManager.areNotificationsEnabled()
    }

    companion object {
        const val CHANNEL_ID = "secondary_caregiver_reminders_v1"
        private const val CONTENT_REQUEST_CODE = 510
        private const val ACTION_OPEN_SECONDARY_REMINDER =
            "com.berkant.yaninda.action.OPEN_SECONDARY_CAREGIVER_REMINDER"
        private val TIME_FORMAT = DateTimeFormatter.ofPattern(
            "HH:mm",
            Locale.forLanguageTag("tr-TR"),
        )
    }
}
