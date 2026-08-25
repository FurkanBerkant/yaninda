package com.berkant.yaninda.reminder

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.berkant.yaninda.MainActivity
import com.berkant.yaninda.receiver.MedicationAlarmReceiver
import com.berkant.yaninda.ui.alarm.MedicationAlarmActivity

data class ResponseWindowLaunch(
    val occurrenceId: String,
    val expectedAutomaticRetryCount: Int,
)

sealed interface AlarmActivityLaunch {
    data class Medication(
        val occurrenceId: String,
    ) : AlarmActivityLaunch

    data object Test : AlarmActivityLaunch
}

internal object AlarmIntentFactory {
    const val ACTION_MEDICATION_ALARM = "com.berkant.yaninda.action.MEDICATION_ALARM"
    const val ACTION_TEST_ALARM = "com.berkant.yaninda.action.TEST_ALARM"
    const val ACTION_RESPONSE_WINDOW_ELAPSED =
        "com.berkant.yaninda.action.RESPONSE_WINDOW_ELAPSED"

    private const val ACTION_OPEN_APP = "com.berkant.yaninda.action.OPEN_FROM_REMINDER"
    private const val ACTION_OPEN_MEDICATION_ALARM =
        "com.berkant.yaninda.action.OPEN_MEDICATION_ALARM"
    private const val ACTION_OPEN_TEST_ALARM = "com.berkant.yaninda.action.OPEN_TEST_ALARM"
    private const val EXTRA_OCCURRENCE_ID = "com.berkant.yaninda.extra.OCCURRENCE_ID"
    private const val EXTRA_EXPECTED_AUTOMATIC_RETRY_COUNT =
        "com.berkant.yaninda.extra.EXPECTED_AUTOMATIC_RETRY_COUNT"
    private const val OCCURRENCE_REQUEST_CODE = 100
    private const val TEST_REQUEST_CODE = 101
    private const val RESPONSE_WINDOW_REQUEST_CODE = 103
    private const val CONTENT_REQUEST_CODE = 102
    private const val MEDICATION_ACTIVITY_REQUEST_CODE = 200
    private const val TEST_ACTIVITY_REQUEST_CODE = 201
    private const val URI_SCHEME = "yaninda"
    private const val URI_AUTHORITY = "local-reminder"

    fun occurrenceBroadcast(
        context: Context,
        occurrenceId: String,
    ): PendingIntent {
        require(occurrenceId.isNotBlank()) { "Occurrence ID cannot be blank." }
        val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            action = ACTION_MEDICATION_ALARM
            data = occurrenceUri(occurrenceId)
            putExtra(EXTRA_OCCURRENCE_ID, occurrenceId)
        }
        return PendingIntent.getBroadcast(
            context,
            OCCURRENCE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun testBroadcast(context: Context): PendingIntent {
        val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            action = ACTION_TEST_ALARM
            data = Uri.Builder()
                .scheme(URI_SCHEME)
                .authority(URI_AUTHORITY)
                .appendPath("test")
                .build()
        }
        return PendingIntent.getBroadcast(
            context,
            TEST_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /*
     * Legacy form is kept only so alarms scheduled by the previous app
     * version can still be parsed/cancelled after an in-place update.
     */
    fun responseWindowBroadcast(
        context: Context,
        occurrenceId: String,
    ): PendingIntent {
        require(occurrenceId.isNotBlank()) { "Occurrence ID cannot be blank." }

        val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            action = ACTION_RESPONSE_WINDOW_ELAPSED
            data = Uri.Builder()
                .scheme(URI_SCHEME)
                .authority(URI_AUTHORITY)
                .appendPath("response-window")
                .appendPath(occurrenceId)
                .build()
            putExtra(EXTRA_OCCURRENCE_ID, occurrenceId)
        }

        return PendingIntent.getBroadcast(
            context,
            RESPONSE_WINDOW_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun responseWindowBroadcast(
        context: Context,
        occurrenceId: String,
        expectedAutomaticRetryCount: Int,
    ): PendingIntent {
        require(occurrenceId.isNotBlank()) { "Occurrence ID cannot be blank." }
        require(expectedAutomaticRetryCount >= 0) {
            "Expected automatic retry count cannot be negative."
        }

        val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            action = ACTION_RESPONSE_WINDOW_ELAPSED
            data = Uri.Builder()
                .scheme(URI_SCHEME)
                .authority(URI_AUTHORITY)
                .appendPath("response-window")
                .appendPath(occurrenceId)
                .appendPath(expectedAutomaticRetryCount.toString())
                .build()

            putExtra(EXTRA_OCCURRENCE_ID, occurrenceId)
            putExtra(
                EXTRA_EXPECTED_AUTOMATIC_RETRY_COUNT,
                expectedAutomaticRetryCount,
            )
        }

        return PendingIntent.getBroadcast(
            context,
            RESPONSE_WINDOW_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun appContent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_APP
            data = Uri.Builder()
                .scheme(URI_SCHEME)
                .authority(URI_AUTHORITY)
                .appendPath("overview")
                .build()
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            CONTENT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun medicationAlarmActivityIntent(
        context: Context,
        occurrenceId: String,
    ): Intent {
        require(occurrenceId.isNotBlank()) { "Occurrence ID cannot be blank." }

        return Intent(context, MedicationAlarmActivity::class.java).apply {
            action = ACTION_OPEN_MEDICATION_ALARM
            data = alarmActivityOccurrenceUri(occurrenceId)
            putExtra(EXTRA_OCCURRENCE_ID, occurrenceId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    fun medicationAlarmActivity(
        context: Context,
        occurrenceId: String,
    ): PendingIntent {
        val intent =
            medicationAlarmActivityIntent(
                context = context,
                occurrenceId = occurrenceId,
            )

        return PendingIntent.getActivity(
            context,
            MEDICATION_ACTIVITY_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun testAlarmActivity(context: Context): PendingIntent {
        val intent = Intent(context, MedicationAlarmActivity::class.java).apply {
            action = ACTION_OPEN_TEST_ALARM
            data = Uri.Builder()
                .scheme(URI_SCHEME)
                .authority(URI_AUTHORITY)
                .appendPath("alarm")
                .appendPath("test")
                .build()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return PendingIntent.getActivity(
            context,
            TEST_ACTIVITY_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun alarmActivityLaunch(intent: Intent): AlarmActivityLaunch? = when (intent.action) {
        ACTION_OPEN_MEDICATION_ALARM -> {
            val occurrenceId = intent.getStringExtra(EXTRA_OCCURRENCE_ID)
                ?.takeIf(String::isNotBlank)
                ?: return null
            val segments = intent.data?.pathSegments.orEmpty()
            if (
                segments.size == 3 &&
                segments[0] == "alarm" &&
                segments[1] == "occurrence" &&
                segments[2] == occurrenceId
            ) {
                AlarmActivityLaunch.Medication(occurrenceId)
            } else {
                null
            }
        }

        ACTION_OPEN_TEST_ALARM -> {
            val segments = intent.data?.pathSegments.orEmpty()
            if (segments == listOf("alarm", "test")) AlarmActivityLaunch.Test else null
        }

        else -> null
    }

    fun occurrenceId(intent: Intent): String? {
        if (intent.action != ACTION_MEDICATION_ALARM) return null
        val occurrenceId = intent.getStringExtra(EXTRA_OCCURRENCE_ID)?.takeIf(String::isNotBlank)
            ?: return null
        val segments = intent.data?.pathSegments.orEmpty()
        return occurrenceId.takeIf {
            segments.size == 2 && segments[0] == "occurrence" && segments[1] == occurrenceId
        }
    }

    fun responseWindowLaunch(intent: Intent): ResponseWindowLaunch? {
        if (intent.action != ACTION_RESPONSE_WINDOW_ELAPSED) return null

        val occurrenceId =
            intent.getStringExtra(EXTRA_OCCURRENCE_ID)
                ?.takeIf(String::isNotBlank)
                ?: return null

        val segments =
            intent.data?.pathSegments.orEmpty()

        if (
            segments.size == 2 &&
            segments[0] == "response-window" &&
            segments[1] == occurrenceId
        ) {
            /*
             * Previous app versions did not encode a generation.
             * Treat them as the first response window.
             */
            return ResponseWindowLaunch(
                occurrenceId = occurrenceId,
                expectedAutomaticRetryCount = 0,
            )
        }

        if (
            segments.size != 3 ||
            segments[0] != "response-window" ||
            segments[1] != occurrenceId
        ) {
            return null
        }

        val expectedAutomaticRetryCount =
            segments[2].toIntOrNull()
                ?: return null

        if (
            expectedAutomaticRetryCount < 0 ||
            intent.getIntExtra(
                EXTRA_EXPECTED_AUTOMATIC_RETRY_COUNT,
                -1,
            ) != expectedAutomaticRetryCount
        ) {
            return null
        }

        return ResponseWindowLaunch(
            occurrenceId = occurrenceId,
            expectedAutomaticRetryCount = expectedAutomaticRetryCount,
        )
    }

    fun responseWindowOccurrenceId(intent: Intent): String? =
        responseWindowLaunch(intent)?.occurrenceId

    private fun occurrenceUri(occurrenceId: String): Uri = Uri.Builder()
        .scheme(URI_SCHEME)
        .authority(URI_AUTHORITY)
        .appendPath("occurrence")
        .appendPath(occurrenceId)
        .build()

    private fun alarmActivityOccurrenceUri(occurrenceId: String): Uri = Uri.Builder()
        .scheme(URI_SCHEME)
        .authority(URI_AUTHORITY)
        .appendPath("alarm")
        .appendPath("occurrence")
        .appendPath(occurrenceId)
        .build()
}
