package com.berkant.yaninda.domain.family

import java.time.Instant
import com.berkant.yaninda.domain.occurrence.AcknowledgementActor
import com.berkant.yaninda.domain.occurrence.DoseOccurrenceStatus

enum class DeviceRole {
    ALARM_DEVICE,
    ADMIN_DEVICE,
}

enum class FamilyMemberRole {
    ADMIN,
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

data class PendingDeviceApproval(
    val uid: String,
    val familyId: String,
    val deviceId: String,
    val requestedRole: DeviceRole,
    val displayName: String,
    val appVersion: String,
    val requestedAt: Instant,
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
