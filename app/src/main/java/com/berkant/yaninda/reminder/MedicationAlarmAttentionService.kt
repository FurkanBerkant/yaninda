package com.berkant.yaninda.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.berkant.yaninda.R

class MedicationAlarmAttentionService : Service() {

    private val handler =
        Handler(Looper.getMainLooper())

    private var mediaPlayer: MediaPlayer? = null

    private var vibrator: Vibrator? = null

    private var activeStartId: Int? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {

        when (intent?.action) {

            ACTION_STOP -> {
                activeStartId = null
                stopAttention()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()

                return START_NOT_STICKY
            }

            ACTION_START -> {
                val occurrenceId =
                    intent
                        .getStringExtra(EXTRA_OCCURRENCE_ID)
                        ?.takeIf(String::isNotBlank)

                if (occurrenceId == null) {
                    stopSelf(startId)
                    return START_NOT_STICKY
                }

                activeStartId = startId

                startAsForeground(
                    occurrenceId = occurrenceId,
                )

                startAttention(startId)
            }

            else -> {
                activeStartId = null
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    private fun startAsForeground(
        occurrenceId: String,
    ) {
        val alarmIntent =
            AlarmIntentFactory
                .medicationAlarmActivity(
                    context = this,
                    occurrenceId = occurrenceId,
                )

        val notification =
            NotificationCompat
                .Builder(
                    this,
                    SERVICE_CHANNEL_ID,
                )
                .setSmallIcon(
                    R.drawable.ic_notification_reminder
                )
                .setContentTitle(
                    getString(R.string.alarm_title)
                )
                .setContentText(
                    getString(
                        R.string.reminder_notification_body
                    )
                )
                .setCategory(
                    NotificationCompat.CATEGORY_ALARM
                )
                .setPriority(
                    NotificationCompat.PRIORITY_LOW
                )
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setContentIntent(alarmIntent)
                .build()

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.Q
        ) {
            startForeground(
                SERVICE_NOTIFICATION_ID,
                notification,
                ServiceInfo
                    .FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(
                SERVICE_NOTIFICATION_ID,
                notification,
            )
        }
    }

    private fun startAttention(
        startId: Int,
    ) {

        stopAttention()

        /*
         * Bildirim kanalındaki:
         *
         * "Dede, ilacını alma zamanı."
         *
         * sesinin önce duyulabilmesi için ring'i
         * çok kısa gecikmeyle başlatıyoruz.
         *
         * Bildirim sesi çalışmasa bile birkaç saniye
         * sonra gerçek alarm tonu devreye girer.
         */
        startVibration()

        handler.postDelayed(
            {
                startAlarmTone()
            },
            ALARM_TONE_DELAY_MILLIS,
        )

        handler.postDelayed(
            {
                stopAfterSafetyTimeout(startId)
            },
            HARD_TIMEOUT_MILLIS,
        )
    }

    private fun stopAfterSafetyTimeout(
        timedOutStartId: Int,
    ) {
        if (
            !isCurrentAttentionStart(
                timedOutStartId = timedOutStartId,
                activeStartId = activeStartId,
            )
        ) {
            return
        }

        Log.w(
            LOG_TAG,
            "Medication alarm attention stopped after the safety timeout.",
        )

        activeStartId = null
        stopAttention()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(timedOutStartId)
    }

    private fun startAlarmTone() {

        stopAlarmTone()

        val alarmUri =
            RingtoneManager
                .getDefaultUri(
                    RingtoneManager.TYPE_ALARM
                )
                ?: RingtoneManager
                    .getDefaultUri(
                        RingtoneManager.TYPE_RINGTONE
                    )
                ?: RingtoneManager
                    .getDefaultUri(
                        RingtoneManager.TYPE_NOTIFICATION
                    )
                ?: return

        val audioAttributes =
            AudioAttributes
                .Builder()
                .setUsage(
                    AudioAttributes.USAGE_ALARM
                )
                .setContentType(
                    AudioAttributes.CONTENT_TYPE_SONIFICATION
                )
                .build()

        try {

            mediaPlayer =
                MediaPlayer().apply {

                    setAudioAttributes(
                        audioAttributes
                    )

                    setDataSource(
                        this@MedicationAlarmAttentionService,
                        alarmUri,
                    )

                    isLooping = true

                    /*
                     * Bu MediaPlayer içindeki ses seviyesidir.
                     * Telefonun sistem Alarm volume seviyesini
                     * zorla değiştirmiyoruz.
                     */
                    setVolume(
                        1.0f,
                        1.0f,
                    )

                    setOnPreparedListener {
                            player ->

                        player.start()
                    }

                    setOnErrorListener {
                            player,
                            _,
                            _ ->

                        runCatching {
                            player.stop()
                        }

                        runCatching {
                            player.release()
                        }

                        if (
                            mediaPlayer === player
                        ) {
                            mediaPlayer = null
                        }

                        true
                    }

                    prepareAsync()
                }

        } catch (_: Exception) {
            stopAlarmTone()
        }
    }

    private fun startVibration() {

        val currentVibrator =
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.S
            ) {

                getSystemService(
                    VibratorManager::class.java
                )
                    ?.defaultVibrator

            } else {

                @Suppress("DEPRECATION")
                getSystemService(
                    VIBRATOR_SERVICE
                ) as? Vibrator
            }
                ?: return

        vibrator =
            currentVibrator

        if (
            !currentVibrator.hasVibrator()
        ) {
            return
        }

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            currentVibrator.vibrate(
                VibrationEffect
                    .createWaveform(
                        VIBRATION_PATTERN,
                        0,
                    )
            )

        } else {

            @Suppress("DEPRECATION")
            currentVibrator.vibrate(
                VIBRATION_PATTERN,
                0,
            )
        }
    }

    private fun stopAttention() {

        handler.removeCallbacksAndMessages(null)

        stopAlarmTone()

        vibrator
            ?.cancel()

        vibrator = null
    }

    private fun stopAlarmTone() {

        val player =
            mediaPlayer
                ?: return

        mediaPlayer = null

        runCatching {

            if (player.isPlaying) {
                player.stop()
            }
        }

        runCatching {
            player.reset()
        }

        runCatching {
            player.release()
        }
    }

    private fun ensureChannel() {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.O
        ) {
            return
        }

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        val channel =
            NotificationChannel(
                SERVICE_CHANNEL_ID,
                "Aktif ilaç alarmı",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {

                description =
                    "İlaç alarmı aktifken uygulamayı çalışır tutar."

                setSound(
                    null,
                    null,
                )

                enableVibration(false)

                lockscreenVisibility =
                    Notification.VISIBILITY_PRIVATE

                setShowBadge(false)
            }

        manager.createNotificationChannel(
            channel
        )
    }

    override fun onDestroy() {

        activeStartId = null
        stopAttention()

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?,
    ): IBinder? = null

    companion object {

        private const val ACTION_START =
            "com.berkant.yaninda.action.START_MEDICATION_ATTENTION"

        private const val ACTION_STOP =
            "com.berkant.yaninda.action.STOP_MEDICATION_ATTENTION"

        private const val EXTRA_OCCURRENCE_ID =
            "medication_attention_occurrence_id"

        private const val SERVICE_CHANNEL_ID =
            "medication_alarm_active_service_v1"

        private const val SERVICE_NOTIFICATION_ID =
            5_300

        private const val LOG_TAG =
            "YanindaAlarm"

        /*
         * Önce kısa Türkçe bildirim sesi duyulsun,
         * ardından yüksek ve sürekli ring başlasın.
         */
        private const val ALARM_TONE_DELAY_MILLIS =
            2_000L

        internal const val HARD_TIMEOUT_MILLIS =
            5 * 60 * 1_000L

        private val VIBRATION_PATTERN =
            longArrayOf(
                0L,
                800L,
                350L,
                800L,
                350L,
                1_200L,
                500L,
            )

        fun start(
            context: Context,
            occurrenceId: String,
        ) {

            val intent =
                Intent(
                    context,
                    MedicationAlarmAttentionService::class.java,
                ).apply {

                    action =
                        ACTION_START

                    putExtra(
                        EXTRA_OCCURRENCE_ID,
                        occurrenceId,
                    )
                }

            ContextCompat
                .startForegroundService(
                    context,
                    intent,
                )
        }

        fun stop(
            context: Context,
        ) {

            context.stopService(
                Intent(
                    context,
                    MedicationAlarmAttentionService::class.java,
                )
            )
        }
    }
}

internal fun isCurrentAttentionStart(
    timedOutStartId: Int,
    activeStartId: Int?,
): Boolean =
    timedOutStartId > 0 &&
        timedOutStartId == activeStartId
