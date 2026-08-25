package com.berkant.yaninda.reminder

import java.time.Duration

internal object MedicationAlarmPolicy {
    val RESPONSE_WINDOW: Duration = Duration.ofSeconds(40)
    val AUTOMATIC_RETRY_DELAY: Duration = Duration.ofMinutes(10)

    const val MAX_AUTOMATIC_RETRIES: Int = 1
    const val ATTENTION_TIMEOUT_MILLIS: Long = 40_000L
}
