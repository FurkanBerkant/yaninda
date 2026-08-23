package com.berkant.yaninda.domain.occurrence

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID

fun interface OccurrenceIdFactory {
    fun create(
        medicationId: String,
        scheduleId: String,
        scheduledAt: Instant,
    ): String
}

object StableOccurrenceIdFactory : OccurrenceIdFactory {
    override fun create(
        medicationId: String,
        scheduleId: String,
        scheduledAt: Instant,
    ): String {
        val identity = listOf(
            ID_NAMESPACE,
            medicationId,
            scheduleId,
            scheduledAt.toEpochMilli().toString(),
        ).joinToString(separator = "|")
        return UUID.nameUUIDFromBytes(identity.toByteArray(StandardCharsets.UTF_8)).toString()
    }

    private const val ID_NAMESPACE = "yaninda-dose-occurrence-v1"
}
