package com.berkant.yaninda.push

enum class FamilyPushEventType {
    TAKEN_ACKNOWLEDGEMENT,
    NO_CONFIRMATION,
}

data class FamilyPushPayload(
    val type: FamilyPushEventType,
    val familyId: String,
    val occurrenceId: String,
    val eventId: String,
    val scheduledTime: String,
)

object FamilyPushPayloadParser {
    fun parse(data: Map<String, String>): FamilyPushPayload? {
        if (data.keys.any { it !in ALLOWED_KEYS }) return null
        val type = when (data[TYPE]) {
            ACKNOWLEDGED_TAKEN -> FamilyPushEventType.TAKEN_ACKNOWLEDGEMENT
            NO_CONFIRMATION -> FamilyPushEventType.NO_CONFIRMATION
            else -> return null
        }
        val familyId = data[FAMILY_ID]?.validIdentifier() ?: return null
        val occurrenceId = data[OCCURRENCE_ID]?.validIdentifier() ?: return null
        val eventId = data[EVENT_ID]?.validIdentifier(MAX_EVENT_ID_LENGTH) ?: return null
        val scheduledTime = data[SCHEDULED_TIME]
            ?.takeIf(TIME_PATTERN::matches)
            ?: return null
        return FamilyPushPayload(type, familyId, occurrenceId, eventId, scheduledTime)
    }

    private fun String.validIdentifier(maxLength: Int = MAX_ID_LENGTH): String? =
        takeIf { isNotBlank() && length <= maxLength && '/' !in this }

    private val ALLOWED_KEYS = setOf(
        TYPE,
        FAMILY_ID,
        OCCURRENCE_ID,
        EVENT_ID,
        SCHEDULED_TIME,
    )
    private val TIME_PATTERN = Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$")
    private const val MAX_ID_LENGTH = 128
    private const val MAX_EVENT_ID_LENGTH = 256
    const val TYPE = "type"
    const val FAMILY_ID = "familyId"
    const val OCCURRENCE_ID = "occurrenceId"
    const val EVENT_ID = "eventId"
    const val SCHEDULED_TIME = "scheduledTime"
    const val ACKNOWLEDGED_TAKEN = "ACKNOWLEDGED_TAKEN"
    const val NO_CONFIRMATION = "NO_CONFIRMATION"
}

data class ScheduleChangedPushPayload(
    val familyId: String,
    val scheduleVersion: Long,
)

object ScheduleChangedPushPayloadParser {

    fun parse(
        data: Map<String, String>,
    ): ScheduleChangedPushPayload? {

        if (
            data.keys.any {
                it !in ALLOWED_KEYS
            }
        ) {
            return null
        }

        if (
            data[TYPE] != SCHEDULE_CHANGED
        ) {
            return null
        }

        val familyId =
            data[FAMILY_ID]
                ?.takeIf {
                    it.isNotBlank() &&
                            it.length <=
                            MAX_ID_LENGTH &&
                            '/' !in it
                }
                ?: return null

        val scheduleVersion =
            data[SCHEDULE_VERSION]
                ?.toLongOrNull()
                ?.takeIf {
                    it > 0L
                }
                ?: return null

        return ScheduleChangedPushPayload(
            familyId = familyId,
            scheduleVersion =
                scheduleVersion,
        )
    }

    private val ALLOWED_KEYS =
        setOf(
            TYPE,
            FAMILY_ID,
            SCHEDULE_VERSION,
        )

    const val TYPE =
        "type"

    const val FAMILY_ID =
        "familyId"

    const val SCHEDULE_VERSION =
        "scheduleVersion"

    const val SCHEDULE_CHANGED =
        "SCHEDULE_CHANGED"

    private const val MAX_ID_LENGTH =
        128
}