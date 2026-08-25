package com.berkant.yaninda.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.berkant.yaninda.R
import com.berkant.yaninda.reminder.AlarmIntentFactory
import com.berkant.yaninda.reminder.MedicationAlarmPolicy

enum class NotificationCapability {
    NOT_CHECKED,
    AVAILABLE,
    RUNTIME_PERMISSION_REQUIRED,
    APP_NOTIFICATIONS_DISABLED,
    CHANNEL_DISABLED,
    CHANNEL_ATTENTION_REQUIRED,
    CHECK_FAILED,
}

enum class FullScreenIntentCapability {
    NOT_CHECKED,
    AVAILABLE,
    USER_ACTION_REQUIRED,
    CHECK_FAILED,
}

sealed interface NotificationDeliveryResult {
    data object Delivered : NotificationDeliveryResult

    data class Blocked(
        val capability: NotificationCapability,
    ) : NotificationDeliveryResult

    data object PlatformFailure : NotificationDeliveryResult
}

enum class NotificationCancellationResult {
    CANCELLED,
    PLATFORM_FAILURE,
}

interface ReminderNotifier {
    fun ensureChannel()

    fun capability(): NotificationCapability

    fun fullScreenIntentCapability(): FullScreenIntentCapability

    fun showMedicationReminder(occurrenceId: String): NotificationDeliveryResult

    fun showTestReminder(): NotificationDeliveryResult

    fun cancelMedicationReminder(occurrenceId: String): NotificationCancellationResult

    fun cancelTestReminder(): NotificationCancellationResult
}

class MedicationNotificationManager(
    context: Context,
) : ReminderNotifier {
    private val applicationContext = context.applicationContext
    private val platformManager =
        applicationContext.getSystemService(NotificationManager::class.java)
    private val compatManager = NotificationManagerCompat.from(applicationContext)

    override fun ensureChannel() {
        val alarmAudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val channel = NotificationChannel(
            CHANNEL_ID,
            applicationContext.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = applicationContext.getString(R.string.reminder_channel_description)
            setSound(bundledSoundUriOrFallback(), alarmAudioAttributes)
            enableVibration(true)
            vibrationPattern = VIBRATION_PATTERN
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        platformManager.createNotificationChannel(channel)
    }

    override fun capability(): NotificationCapability = try {
        ensureChannel()
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED ->
                NotificationCapability.RUNTIME_PERMISSION_REQUIRED

            !compatManager.areNotificationsEnabled() ->
                NotificationCapability.APP_NOTIFICATIONS_DISABLED

            platformManager.getNotificationChannel(CHANNEL_ID)?.importance ==
                NotificationManager.IMPORTANCE_NONE -> NotificationCapability.CHANNEL_DISABLED

            platformManager.getNotificationChannel(CHANNEL_ID)?.let { channel ->
                channel.importance < NotificationManager.IMPORTANCE_HIGH ||
                    channel.sound == null ||
                    !channel.shouldVibrate()
            } == true -> NotificationCapability.CHANNEL_ATTENTION_REQUIRED

            else -> NotificationCapability.AVAILABLE
        }
    } catch (_: RuntimeException) {
        NotificationCapability.CHECK_FAILED
    }

    override fun fullScreenIntentCapability(): FullScreenIntentCapability = try {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                if (platformManager.canUseFullScreenIntent()) {
                    FullScreenIntentCapability.AVAILABLE
                } else {
                    FullScreenIntentCapability.USER_ACTION_REQUIRED
                }
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.USE_FULL_SCREEN_INTENT,
                ) != PackageManager.PERMISSION_GRANTED ->
                FullScreenIntentCapability.CHECK_FAILED

            else -> FullScreenIntentCapability.AVAILABLE
        }
    } catch (_: RuntimeException) {
        FullScreenIntentCapability.CHECK_FAILED
    }

    override fun showMedicationReminder(occurrenceId: String): NotificationDeliveryResult {
        require(occurrenceId.isNotBlank()) { "Occurrence ID cannot be blank." }
        val alarmIntent = AlarmIntentFactory.medicationAlarmActivity(
            context = applicationContext,
            occurrenceId = occurrenceId,
        )
        val notification = alarmBuilder(
            title = applicationContext.getString(R.string.alarm_title),
            body = applicationContext.getString(R.string.reminder_notification_body),
            alarmIntent = alarmIntent,
        ).build()
        return deliver(
            tag = occurrenceNotificationTag(occurrenceId),
            notificationId = MEDICATION_NOTIFICATION_ID,
            notification = notification,
        )
    }

    override fun showTestReminder(): NotificationDeliveryResult {
        val alarmIntent = AlarmIntentFactory.testAlarmActivity(applicationContext)
        val notification = alarmBuilder(
            title = applicationContext.getString(R.string.local_alarm_test_notification_title),
            body = applicationContext.getString(R.string.local_alarm_test_notification_body),
            alarmIntent = alarmIntent,
        ).build()
        return deliver(
            tag = null,
            notificationId = TEST_NOTIFICATION_ID,
            notification = notification,
        )
    }

    override fun cancelMedicationReminder(
        occurrenceId: String,
    ): NotificationCancellationResult {
        require(occurrenceId.isNotBlank()) { "Occurrence ID cannot be blank." }
        return cancel(occurrenceNotificationTag(occurrenceId), MEDICATION_NOTIFICATION_ID)
    }

    override fun cancelTestReminder(): NotificationCancellationResult =
        cancel(tag = null, notificationId = TEST_NOTIFICATION_ID)

    private fun alarmBuilder(
        title: String,
        body: String,
        alarmIntent: PendingIntent,
    ): NotificationCompat.Builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification_reminder)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setContentIntent(alarmIntent)
        .setAutoCancel(false)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setTimeoutAfter(
            MedicationAlarmPolicy.ATTENTION_TIMEOUT_MILLIS
        )
        .apply {
            when (fullScreenIntentCapability()) {
                FullScreenIntentCapability.AVAILABLE ->
                    setFullScreenIntent(alarmIntent, true)

                FullScreenIntentCapability.USER_ACTION_REQUIRED,
                FullScreenIntentCapability.NOT_CHECKED,
                FullScreenIntentCapability.CHECK_FAILED,
                -> Unit
            }
        }

    @SuppressLint("MissingPermission")
    private fun deliver(
        tag: String?,
        notificationId: Int,
        notification: Notification,
    ): NotificationDeliveryResult {
        val currentCapability = capability()
        if (
            currentCapability != NotificationCapability.AVAILABLE &&
            currentCapability != NotificationCapability.CHANNEL_ATTENTION_REQUIRED
        ) {
            return NotificationDeliveryResult.Blocked(currentCapability)
        }
        return try {
            compatManager.notify(tag, notificationId, notification)
            NotificationDeliveryResult.Delivered
        } catch (_: SecurityException) {
            NotificationDeliveryResult.Blocked(NotificationCapability.RUNTIME_PERMISSION_REQUIRED)
        } catch (_: RuntimeException) {
            NotificationDeliveryResult.PlatformFailure
        }
    }

    private fun bundledSoundUriOrFallback(): Uri = try {
        applicationContext.resources.openRawResource(R.raw.medication_alarm_tr).use { stream ->
            check(stream.read() != -1) { "The bundled alarm audio is empty." }
        }
        Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(applicationContext.packageName)
            .appendPath("raw")
            .appendPath("medication_alarm_tr")
            .build()
    } catch (_: RuntimeException) {
        Log.e(TAG, "Bundled alarm audio is unavailable; using the platform alarm tone.")
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    }

    private fun occurrenceNotificationTag(occurrenceId: String): String =
        "medication-occurrence:$occurrenceId"

    private fun cancel(
        tag: String?,
        notificationId: Int,
    ): NotificationCancellationResult = try {
        compatManager.cancel(tag, notificationId)
        NotificationCancellationResult.CANCELLED
    } catch (_: RuntimeException) {
        NotificationCancellationResult.PLATFORM_FAILURE
    }

    companion object {
        const val CHANNEL_ID = "medication_alarm_phase5_v1"

        private const val TEST_NOTIFICATION_ID = 5_100
        private const val MEDICATION_NOTIFICATION_ID = 5_200
        private const val TAG = "MedicationNotification"
        private val VIBRATION_PATTERN = longArrayOf(0L, 500L, 250L, 500L, 250L, 700L)
    }
}
