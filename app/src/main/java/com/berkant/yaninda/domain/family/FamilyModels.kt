package com.berkant.yaninda.domain.family

import java.security.SecureRandom
import java.time.Instant
import com.berkant.yaninda.domain.occurrence.AcknowledgementActor
import com.berkant.yaninda.domain.occurrence.DoseOccurrenceStatus

enum class DeviceRole {
    ALARM_DEVICE,
    ADMIN_DEVICE,
}

enum class FamilyMemberRole {
    ADMIN,
    CAREGIVER_VIEWER,
}

data class FamilyPairing(
    val familyId: String,
    val deviceRole: DeviceRole,
)

data class FamilyMembership(
    val familyId: String,
    val familyName: String,
    val role: FamilyMemberRole,
    val displayName: String,
    val joinedAt: Instant,
)

data class FamilyMember(
    val uid: String,
    val familyId: String,
    val role: FamilyMemberRole,
    val displayName: String,
    val joinedAt: Instant,
    val version: Long,
)

data class FamilyContact(
    val contactId: String,
    val familyId: String,
    val displayName: String,
    val phoneNumber: String,
    val isDefault: Boolean,
    val updatedAt: Instant,
)

data class DeviceRegistration(
    val deviceId: String,
    val familyId: String,
    val ownerUid: String,
    val role: DeviceRole,
    val displayName: String,
    val appVersion: String,
    val lastSeenAt: Instant?,
    val lastSuccessfulSyncAt: Instant?,
    val version: Long,
)

data class PairingInvitation(
    val code: String,
    val familyId: String,
    val targetRole: DeviceRole,
    val expiresAt: Instant,
)

data class FamilyDoseOccurrence(
    val occurrenceId: String,
    val medicationDisplayName: String,
    val scheduledAt: Instant,
    val status: DoseOccurrenceStatus,
    val acknowledgedAt: Instant?,
    val acknowledgementActor: AcknowledgementActor?,
    val lastAlertedAt: Instant?,
    val updatedAt: Instant,
    val syncedAt: Instant,
    val version: Long,
    val sourceDeviceId: String,
)

class PairingCodeGenerator(
    private val nextIndex: (Int) -> Int = SecureRandom()::nextInt,
) {
    fun create(): String = buildString(CODE_LENGTH) {
        repeat(CODE_LENGTH) {
            append(ALPHABET[nextIndex(ALPHABET.length)])
        }
    }

    companion object {
        const val CODE_LENGTH = 16
        private const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    }
}

object PairingCodeNormalizer {
    private val VALID_PATTERN = Regex("^[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{16}$")

    fun normalize(value: String): String? {
        val normalized = value
            .uppercase()
            .filterNot(Char::isWhitespace)
            .replace("-", "")
        return normalized.takeIf(VALID_PATTERN::matches)
    }

    fun display(value: String): String {
        val normalized = requireNotNull(normalize(value)) { "The pairing code is invalid." }
        return normalized.chunked(4).joinToString("-")
    }
}
