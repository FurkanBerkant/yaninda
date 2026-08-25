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
import android.net.Uri
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

                val timeoutMillis =
                    intent
                        .getLongExtra(
                            EXTRA_TIMEOUT_MILLIS,
                            HARD_TIMEOUT_MILLIS,
                        )
                        .coerceIn(
                            1L,
                            HARD_TIMEOUT_MILLIS,
                        )

                activeStartId = startId

                startAsForeground(
                    occurrenceId = occurrenceId,
                )

                startAttention(
                    startId = startId,
                    timeoutMillis = timeoutMillis,
                )
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
        timeoutMillis: Long,
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

        if (
            timeoutMillis >
            ALARM_TONE_DELAY_MILLIS
        ) {
            handler.postDelayed(
                {
                    startAlarmTone()
                },
                ALARM_TONE_DELAY_MILLIS,
            )
        }

        handler.postDelayed(
            {
                stopAfterSafetyTimeout(startId)
            },
            timeoutMillis,
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

        startAlarmToneCandidate(
            candidates =
                alarmToneCandidates(),
            candidateIndex = 0,
        )
    }

    private fun startAlarmToneCandidate(
        candidates: List<Uri>,
        candidateIndex: Int,
    ) {

        val alarmUri =
            candidates
                .getOrNull(
                    candidateIndex
                )
                ?: run {
                    Log.e(
                        LOG_TAG,
                        "No playable medication alarm tone is available.",
                    )
                    return
                }

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

            val player =
                MediaPlayer()

            mediaPlayer = player

            player.apply {

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
                        preparedPlayer ->

                    if (
                        mediaPlayer ===
                        preparedPlayer
                    ) {
                        preparedPlayer.start()
                    }
                }

                setOnErrorListener {
                        failedPlayer,
                        _,
                        _ ->

                    releaseAlarmPlayer(
                        failedPlayer
                    )

                    /*
                     * A default system alarm URI may exist but still fail
                     * while MediaPlayer resolves/prepares it. In that case
                     * continue to the bundled deterministic alarm sound.
                     */
                    startAlarmToneCandidate(
                        candidates =
                            candidates,
                        candidateIndex =
                            candidateIndex + 1,
                    )

                    true
                }

                prepareAsync()
            }

        } catch (_: Exception) {

            mediaPlayer
                ?.let(
                    ::releaseAlarmPlayer
                )

            startAlarmToneCandidate(
                candidates =
                    candidates,
                candidateIndex =
                    candidateIndex + 1,
            )
        }
    }

    private fun alarmToneCandidates():
        List<Uri> {

        val bundledAlarm =
            Uri.parse(
                "android.resource://" +
                    packageName +
                    "/" +
                    R.raw.medication_alarm_tr
            )

        return listOfNotNull(
            /*
             * Respect the user's selected system alarm sound first.
             */
            RingtoneManager
                .getDefaultUri(
                    RingtoneManager.TYPE_ALARM
                ),

            /*
             * Deterministic offline fallback shipped inside the APK.
             */
            bundledAlarm,

            /*
             * Last-resort system sounds in case both alarm sources fail.
             */
            RingtoneManager
                .getDefaultUri(
                    RingtoneManager.TYPE_RINGTONE
                ),

            RingtoneManager
                .getDefaultUri(
                    RingtoneManager.TYPE_NOTIFICATION
                ),
        )
            .distinct()
    }

    private fun releaseAlarmPlayer(
        player: MediaPlayer,
    ) {

        if (
            mediaPlayer === player
        ) {
            mediaPlayer = null
        }

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

        releaseAlarmPlayer(
            player
        )
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

        private const val EXTRA_TIMEOUT_MILLIS =
            "medication_attention_timeout_millis"

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
            MedicationAlarmPolicy.ATTENTION_TIMEOUT_MILLIS

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
            timeoutMillis: Long = HARD_TIMEOUT_MILLIS,
        ) {

            require(occurrenceId.isNotBlank()) {
                "Occurrence ID cannot be blank."
            }

            val boundedTimeoutMillis =
                timeoutMillis.coerceIn(
                    1L,
                    HARD_TIMEOUT_MILLIS,
                )

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

                    putExtra(
                        EXTRA_TIMEOUT_MILLIS,
                        boundedTimeoutMillis,
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
