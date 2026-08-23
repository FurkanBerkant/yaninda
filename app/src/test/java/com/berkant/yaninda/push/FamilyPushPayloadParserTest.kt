package com.berkant.yaninda.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FamilyPushPayloadParserTest {
    private val validData = mapOf(
        "type" to "ACKNOWLEDGED_TAKEN",
        "familyId" to "family-1",
        "occurrenceId" to "occurrence-1",
        "eventId" to "event-1",
        "scheduledTime" to "20:00",
    )

    @Test
    fun validPayload_isParsedWithoutAcceptingServerProvidedDisplayText() {
        assertEquals(
            FamilyPushPayload(
                type = FamilyPushEventType.TAKEN_ACKNOWLEDGEMENT,
                familyId = "family-1",
                occurrenceId = "occurrence-1",
                eventId = "event-1",
                scheduledTime = "20:00",
            ),
            FamilyPushPayloadParser.parse(validData),
        )
    }

    @Test
    fun unknownFieldsAndTypes_areRejected() {
        assertNull(FamilyPushPayloadParser.parse(validData + ("body" to "trust me")))
        assertNull(FamilyPushPayloadParser.parse(validData + ("type" to "TAKEN")))
    }

    @Test
    fun malformedTimeAndIdentifiers_areRejected() {
        assertNull(FamilyPushPayloadParser.parse(validData + ("scheduledTime" to "25:90")))
        assertNull(FamilyPushPayloadParser.parse(validData + ("familyId" to "a/b")))
    }
}
