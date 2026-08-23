package com.berkant.yaninda.push

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

class FamilyPushNotificationManager(context: Context) {
    private val applicationContext = context.applicationContext
    private val notificationManager = NotificationManagerCompat.from(applicationContext)

    fun ensureChannel() {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.family_push_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = applicationContext.getString(
                    R.string.family_push_channel_description
                )
            }
        )
    }

    fun canNotify(): Boolean {
        val runtimeAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        return runtimeAllowed && notificationManager.areNotificationsEnabled()
    }

    fun show(payload: FamilyPushPayload): Boolean {
        if (!canNotify()) return false
        val body = when (payload.type) {
            FamilyPushEventType.TAKEN_ACKNOWLEDGEMENT -> applicationContext.getString(
                R.string.family_push_acknowledged_body,
                payload.scheduledTime,
            )

            FamilyPushEventType.NO_CONFIRMATION -> applicationContext.getString(
                R.string.family_push_no_confirmation_body,
                payload.scheduledTime,
            )
        }
        val contentIntent = PendingIntent.getActivity(
            applicationContext,
            FAMILY_CONTENT_REQUEST_CODE,
            Intent(applicationContext, MainActivity::class.java).apply {
                action = ACTION_OPEN_FAMILY_UPDATE
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle(applicationContext.getString(R.string.family_push_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        return try {
            notificationManager.notify(payload.eventId.hashCode(), notification)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }

    companion object {
        const val CHANNEL_ID = "family_status_updates_v1"
        private const val ACTION_OPEN_FAMILY_UPDATE =
            "com.berkant.yaninda.action.OPEN_FAMILY_UPDATE"
        private const val FAMILY_CONTENT_REQUEST_CODE = 410
    }
}
