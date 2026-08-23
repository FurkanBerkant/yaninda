package com.berkant.yaninda.sync

import com.berkant.yaninda.auth.FamilyAuthRepository
import com.berkant.yaninda.auth.FamilyAuthState
import com.berkant.yaninda.data.device.DeviceIdentityRepository
import com.berkant.yaninda.data.repository.DoseOccurrenceRepository
import com.berkant.yaninda.data.repository.MedicationRepository
import com.berkant.yaninda.domain.family.DeviceRole
import com.berkant.yaninda.domain.sync.SyncOutboxEvent
import com.berkant.yaninda.firebase.awaitFirebaseValue
import com.berkant.yaninda.core.time.TimeProvider
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.time.Instant
import java.util.Date
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

class FirestoreRemoteSyncDataSource(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val authRepository: FamilyAuthRepository,
    private val deviceIdentityRepository: DeviceIdentityRepository,
    private val occurrenceRepository: DoseOccurrenceRepository,
    private val medicationRepository: MedicationRepository,
    private val timeProvider: TimeProvider,
    private val appVersion: String,
) : RemoteSyncDataSource {
    override val readiness: Flow<RemoteSyncReadiness> = combine(
        authRepository.state,
        deviceIdentityRepository.pairing,
    ) { authState, pairing ->
        when {
            authState !is FamilyAuthState.SignedIn ->
                RemoteSyncReadiness.AUTHENTICATION_REQUIRED

            pairing == null -> RemoteSyncReadiness.PAIRING_REQUIRED
            pairing.deviceRole != DeviceRole.ALARM_DEVICE ->
                RemoteSyncReadiness.ALARM_DEVICE_REQUIRED

            else -> RemoteSyncReadiness.READY
        }
    }

    override suspend fun deliver(event: SyncOutboxEvent): RemoteSyncDelivery {
        if (readiness.first() != RemoteSyncReadiness.READY) {
            return RemoteSyncDelivery.RETRYABLE_FAILURE
        }
        val user = auth.currentUser ?: return RemoteSyncDelivery.RETRYABLE_FAILURE
        val pairing = deviceIdentityRepository.pairing.first()
            ?: return RemoteSyncDelivery.RETRYABLE_FAILURE
        val deviceId = deviceIdentityRepository.getOrCreateDeviceId()
        val occurrence = occurrenceRepository.get(event.aggregateId)
            ?: return RemoteSyncDelivery.RETRYABLE_FAILURE
        val medication = medicationRepository.get(occurrence.medicationId)
            ?: return RemoteSyncDelivery.RETRYABLE_FAILURE
        if (event.aggregateVersion > occurrence.version) {
            return RemoteSyncDelivery.RETRYABLE_FAILURE
        }

        return try {
            firestore.runTransaction { transaction ->
                val family = firestore.collection(FAMILIES).document(pairing.familyId)
                val remoteEventId = scopedRemoteId(
                    deviceId = deviceId,
                    localId = event.id,
                )
                val eventReference = family.collection(SYNC_EVENTS).document(remoteEventId)
                if (transaction.get(eventReference).exists()) {
                    return@runTransaction RemoteSyncDelivery.ALREADY_DELIVERED
                }

                val deviceReference = family.collection(DEVICES).document(deviceId)
                val device = transaction.get(deviceReference)
                check(device.exists() && device.getString(OWNER_UID) == user.uid) {
                    "The paired alarm device registration is unavailable."
                }

                val occurrenceReportId = scopedRemoteId(
                    deviceId = deviceId,
                    localId = occurrence.id,
                )
                val occurrenceReference = family.collection(OCCURRENCES)
                    .document(occurrenceReportId)
                val remoteOccurrence = transaction.get(occurrenceReference)
                val remoteVersion = remoteOccurrence.getLong(VERSION) ?: 0L
                if (
                    occurrence.version == event.aggregateVersion &&
                    occurrence.version >= remoteVersion
                ) {
                    transaction.set(
                        occurrenceReference,
                        mapOf(
                            OCCURRENCE_ID to occurrence.id,
                            MEDICATION_DISPLAY_NAME to medication.medication.displayName,
                            SCHEDULED_AT to occurrence.scheduledAt.toTimestamp(),
                            SCHEDULED_LOCAL_TIME to OCCURRENCE_TIME_FORMAT.format(
                                occurrence.scheduledAt.atZone(timeProvider.currentZoneId())
                            ),
                            SCHEDULED_ZONE_ID to timeProvider.currentZoneId().id,
                            STATUS to occurrence.status.name,
                            ACKNOWLEDGED_AT to occurrence.acknowledgedAt?.toTimestamp(),
                            ACKNOWLEDGEMENT_ACTOR to occurrence.acknowledgementActor?.name,
                            LAST_ALERTED_AT to occurrence.lastAlertedAt?.toTimestamp(),
                            UPDATED_AT to occurrence.updatedAt.toTimestamp(),
                            VERSION to occurrence.version,
                            SOURCE_DEVICE_ID to deviceId,
                            SOURCE_EVENT_ID to remoteEventId,
                            SYNCED_AT to FieldValue.serverTimestamp(),
                        ),
                    )
                }

                transaction.set(
                    eventReference,
                    mapOf(
                        EVENT_ID to remoteEventId,
                        EVENT_TYPE to event.eventType.name,
                        AGGREGATE_ID to event.aggregateId,
                        AGGREGATE_VERSION to event.aggregateVersion,
                        PAYLOAD_VERSION to event.payloadVersion,
                        SOURCE_DEVICE_ID to deviceId,
                        CREATED_AT to event.createdAt.toTimestamp(),
                        DELIVERED_AT to FieldValue.serverTimestamp(),
                    ),
                )
                transaction.update(
                    deviceReference,
                    mapOf(
                        APP_VERSION to appVersion,
                        LAST_SEEN_AT to FieldValue.serverTimestamp(),
                        LAST_SUCCESSFUL_SYNC_AT to FieldValue.serverTimestamp(),
                        VERSION to ((device.getLong(VERSION) ?: 0L) + 1L),
                    ),
                )
                RemoteSyncDelivery.DELIVERED
            }.awaitFirebaseValue()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            RemoteSyncDelivery.RETRYABLE_FAILURE
        }
    }

    private fun scopedRemoteId(
        deviceId: String,
        localId: String,
    ): String {
        require(deviceId.isNotBlank()) {
            "Device ID cannot be blank."
        }
        require(localId.isNotBlank()) {
            "Local ID cannot be blank."
        }

        val remoteId = "$deviceId$REMOTE_ID_SEPARATOR$localId"

        require(remoteId.length <= MAX_REMOTE_ID_LENGTH) {
            "The remote sync identity is too long."
        }

        return remoteId
    }

    private fun Instant.toTimestamp(): Timestamp = Timestamp(Date.from(this))

    private companion object {
        const val FAMILIES = "families"
        const val DEVICES = "devices"
        const val OCCURRENCES = "occurrences"
        const val SYNC_EVENTS = "syncEvents"
        const val OWNER_UID = "ownerUid"
        const val APP_VERSION = "appVersion"
        const val LAST_SEEN_AT = "lastSeenAt"
        const val LAST_SUCCESSFUL_SYNC_AT = "lastSuccessfulSyncAt"
        const val VERSION = "version"
        const val OCCURRENCE_ID = "occurrenceId"
        const val MEDICATION_DISPLAY_NAME = "medicationDisplayName"
        const val SCHEDULED_AT = "scheduledAt"
        const val SCHEDULED_LOCAL_TIME = "scheduledLocalTime"
        const val SCHEDULED_ZONE_ID = "scheduledZoneId"
        const val STATUS = "status"
        const val ACKNOWLEDGED_AT = "acknowledgedAt"
        const val ACKNOWLEDGEMENT_ACTOR = "acknowledgementActor"
        const val LAST_ALERTED_AT = "lastAlertedAt"
        const val UPDATED_AT = "updatedAt"
        const val SOURCE_DEVICE_ID = "sourceDeviceId"
        const val SOURCE_EVENT_ID = "sourceEventId"
        const val SYNCED_AT = "syncedAt"
        const val EVENT_ID = "eventId"
        const val EVENT_TYPE = "eventType"
        const val AGGREGATE_ID = "aggregateId"
        const val AGGREGATE_VERSION = "aggregateVersion"
        const val PAYLOAD_VERSION = "payloadVersion"
        const val CREATED_AT = "createdAt"
        const val DELIVERED_AT = "deliveredAt"
        const val REMOTE_ID_SEPARATOR = "--"
        const val MAX_REMOTE_ID_LENGTH = 256
        val OCCURRENCE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
