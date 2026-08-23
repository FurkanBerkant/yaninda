package com.berkant.yaninda.secondary

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.berkant.yaninda.receiver.SecondaryReminderReceiver

internal object SecondaryReminderIntentFactory {
    const val ACTION_SECONDARY_REMINDER =
        "com.berkant.yaninda.action.SECONDARY_CAREGIVER_REMINDER"
    private const val EXTRA_OCCURRENCE_ID =
        "com.berkant.yaninda.extra.SECONDARY_OCCURRENCE_ID"
    private const val REQUEST_CODE = 500

    fun broadcast(context: Context, occurrenceId: String): PendingIntent {
        require(occurrenceId.isNotBlank()) { "Occurrence ID cannot be blank." }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, SecondaryReminderReceiver::class.java).apply {
                action = ACTION_SECONDARY_REMINDER
                data = Uri.Builder()
                    .scheme("yaninda")
                    .authority("secondary-reminder")
                    .appendPath("occurrence")
                    .appendPath(occurrenceId)
                    .build()
                putExtra(EXTRA_OCCURRENCE_ID, occurrenceId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun occurrenceId(intent: Intent): String? {
        if (intent.action != ACTION_SECONDARY_REMINDER) return null
        val occurrenceId = intent.getStringExtra(EXTRA_OCCURRENCE_ID)
            ?.takeIf(String::isNotBlank)
            ?: return null
        val segments = intent.data?.pathSegments.orEmpty()
        return occurrenceId.takeIf {
            segments.size == 2 &&
                segments[0] == "occurrence" &&
                segments[1] == occurrenceId
        }
    }
}
