package com.berkant.yaninda.family

import android.util.Log
import com.berkant.yaninda.domain.family.DeviceRegistration
import com.berkant.yaninda.domain.family.DeviceRole
import com.berkant.yaninda.domain.family.FamilyContact
import com.berkant.yaninda.domain.family.FamilyMemberRole
import com.berkant.yaninda.domain.family.FamilyDoseOccurrence
import com.berkant.yaninda.domain.family.FamilyMembership
import com.berkant.yaninda.domain.family.PendingDeviceApproval
import com.berkant.yaninda.domain.occurrence.AcknowledgementActor
import com.berkant.yaninda.domain.occurrence.DoseOccurrenceStatus
import com.berkant.yaninda.firebase.awaitFirebaseCompletion
import com.berkant.yaninda.firebase.awaitFirebaseValue
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QuerySnapshot
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.combine

enum class FamilyRepositoryFailure {
    NOT_AUTHENTICATED,
    PERMISSION_DENIED,
    NETWORK_UNAVAILABLE,
    INVALID_INPUT,
    NOT_CONFIGURED,
    UNKNOWN,
}

sealed interface FamilyRepositoryResult<out T> {
    data class Success<T>(val value: T) : FamilyRepositoryResult<T>

    data class Failure(val reason: FamilyRepositoryFailure) : FamilyRepositoryResult<Nothing>
}

interface FamilyRepository {
    fun observeMemberships(): Flow<List<FamilyMembership>>

    fun observeDevices(familyId: String): Flow<List<DeviceRegistration>>

    fun observeOccurrences(familyId: String): Flow<List<FamilyDoseOccurrence>>

    fun observeContacts(familyId: String): Flow<List<FamilyContact>>

    fun observePendingDeviceApprovals(familyId: String): Flow<List<PendingDeviceApproval>>

    suspend fun approveDevice(
        approval: PendingDeviceApproval,
    ): FamilyRepositoryResult<Unit>

    suspend fun removeDevice(
        device: DeviceRegistration,
    ): FamilyRepositoryResult<Unit>

    suspend fun saveContact(
        familyId: String,
        contact: FamilyContact,
    ): FamilyRepositoryResult<Unit>

    suspend fun deleteContact(
        familyId: String,
        contactId: String,
    ): FamilyRepositoryResult<Unit>
}

object UnavailableFamilyRepository : FamilyRepository {
    override fun observeMemberships(): Flow<List<FamilyMembership>> = flowOf(emptyList())

    override fun observeDevices(familyId: String): Flow<List<DeviceRegistration>> =
        flowOf(emptyList())

    override fun observeOccurrences(familyId: String): Flow<List<FamilyDoseOccurrence>> =
        flowOf(emptyList())

    override fun observeContacts(familyId: String): Flow<List<FamilyContact>> =
        flowOf(emptyList())

    override fun observePendingDeviceApprovals(
        familyId: String,
    ): Flow<List<PendingDeviceApproval>> = flowOf(emptyList())

    override suspend fun approveDevice(
        approval: PendingDeviceApproval,
    ): FamilyRepositoryResult<Unit> = notConfigured()

    override suspend fun removeDevice(
        device: DeviceRegistration,
    ): FamilyRepositoryResult<Unit> = notConfigured()

    override suspend fun saveContact(
        familyId: String,
        contact: FamilyContact,
    ): FamilyRepositoryResult<Unit> = notConfigured()

    override suspend fun deleteContact(
        familyId: String,
        contactId: String,
    ): FamilyRepositoryResult<Unit> = notConfigured()

    private fun notConfigured() = FamilyRepositoryResult.Failure(
        FamilyRepositoryFailure.NOT_CONFIGURED
    )
}

class FirestoreFamilyRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val now: () -> Instant = Instant::now,
    private val monitoringZoneId: ZoneId = ZoneId.systemDefault(),
) : FamilyRepository {
    override fun observeMemberships(): Flow<List<FamilyMembership>> {
        val userId = auth.currentUser?.uid ?: return flowOf(emptyList())
        return snapshotsFlow(
            firestore.collection(USERS)
                .document(userId)
                .collection(MEMBERSHIPS),
        ) { snapshot ->
            snapshot.documents.map { document -> document.toFamilyMembership() }
                .sortedBy(FamilyMembership::familyName)
        }
    }

    override fun observeDevices(familyId: String): Flow<List<DeviceRegistration>> {
        if (!isValidId(familyId)) return flowOf(emptyList())
        return snapshotsFlow(
            firestore.collection(FAMILIES)
                .document(familyId)
                .collection(DEVICES),
        ) { snapshot ->
            snapshot.documents.map { document -> document.toDeviceRegistration() }
                .sortedWith(compareBy(DeviceRegistration::role, DeviceRegistration::displayName))
        }
    }

    override fun observeOccurrences(familyId: String): Flow<List<FamilyDoseOccurrence>> {
        if (!isValidId(familyId)) return flowOf(emptyList())
        val window = familyOccurrenceObservationWindow(
            now = now(),
            zoneId = monitoringZoneId,
        )
        return snapshotsFlow(
            firestore.collection(FAMILIES)
                .document(familyId)
                .collection(OCCURRENCES)
                .whereGreaterThanOrEqualTo(
                    SCHEDULED_AT,
                    Date.from(window.startInclusive),
                )
                .whereLessThan(
                    SCHEDULED_AT,
                    Date.from(window.endExclusive),
                )
                .orderBy(SCHEDULED_AT, com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(MAX_MONITORING_OCCURRENCES),
        ) { snapshot ->
            snapshot.documents.map { document -> document.toFamilyDoseOccurrence() }
        }
    }

    override fun observeContacts(familyId: String): Flow<List<FamilyContact>> {
        if (!isValidId(familyId)) return flowOf(emptyList())
        return snapshotsFlow(
            firestore.collection(FAMILIES).document(familyId).collection(CONTACTS),
        ) { snapshot ->
            snapshot.documents.map { it.toFamilyContact() }
                .sortedWith(compareByDescending<FamilyContact> { it.isDefault }
                    .thenBy(FamilyContact::displayName))
        }
    }

    override fun observePendingDeviceApprovals(
        familyId: String,
    ): Flow<List<PendingDeviceApproval>> {
        if (!isValidId(familyId)) return flowOf(emptyList())

        val requests = snapshotsFlow(
            firestore.collection(APPROVAL_REQUESTS)
                .whereEqualTo(FAMILY_ID, familyId),
        ) { snapshot ->
            snapshot.documents.mapNotNull { document ->
                runCatching { document.toPendingDeviceApproval() }
                    .onFailure { error ->
                        Log.w(
                            "YanindaFirestore",
                            "Malformed device approval request ignored. " +
                                "error=${error::class.java.simpleName}",
                        )
                    }
                    .getOrNull()
            }
        }
        val authorizations = snapshotsFlow(
            firestore.collection(DEVICE_AUTHORIZATIONS)
                .whereEqualTo(FAMILY_ID, familyId),
        ) { snapshot ->
            snapshot.documents
                .filter { it.getBoolean(ACTIVE) == true }
                .map(DocumentSnapshot::getId)
                .toSet()
        }

        return combine(requests, authorizations) { pending, approvedUids ->
            pending
                .filterNot { it.uid in approvedUids }
                .sortedBy(PendingDeviceApproval::requestedAt)
        }
    }

    override suspend fun approveDevice(
        approval: PendingDeviceApproval,
    ): FamilyRepositoryResult<Unit> {
        val approverUid = auth.currentUser?.uid ?: return notAuthenticated()
        if (
            !isValidId(approval.uid) ||
            !isValidId(approval.familyId) ||
            !isValidId(approval.deviceId)
        ) {
            return invalidInput()
        }

        return runFirestoreOperation {
            val requestReference = firestore.collection(APPROVAL_REQUESTS)
                .document(approval.uid)
            val authorizationReference = firestore.collection(DEVICE_AUTHORIZATIONS)
                .document(approval.uid)

            firestore.runTransaction { transaction ->
                val request = transaction.get(requestReference)
                check(request.exists()) { "Device approval request no longer exists." }
                check(request.getString(FAMILY_ID) == approval.familyId)
                check(request.getString(DEVICE_ID) == approval.deviceId)
                check(request.getString(REQUESTED_ROLE) == approval.requestedRole.name)

                transaction.set(
                    authorizationReference,
                    mapOf(
                        UID to approval.uid,
                        FAMILY_ID to approval.familyId,
                        DEVICE_ID to approval.deviceId,
                        ROLE to approval.requestedRole.name,
                        ACTIVE to true,
                        APPROVED_AT to FieldValue.serverTimestamp(),
                        APPROVED_BY_UID to approverUid,
                    ),
                )
            }.awaitFirebaseValue()
        }
    }

    override suspend fun removeDevice(
        device: DeviceRegistration,
    ): FamilyRepositoryResult<Unit> {
        val currentUid = auth.currentUser?.uid ?: return notAuthenticated()
        if (
            !isValidId(device.familyId) ||
            !isValidId(device.deviceId) ||
            !isValidId(device.ownerUid) ||
            device.ownerUid == currentUid
        ) {
            return invalidInput()
        }

        return runFirestoreOperation {
            val family = firestore.collection(FAMILIES).document(device.familyId)
            val deviceReference = family.collection(DEVICES).document(device.deviceId)
            val remoteDevice = deviceReference.get().awaitFirebaseValue()
            check(remoteDevice.exists()) { "Device no longer exists." }
            check(remoteDevice.getString(OWNER_UID) == device.ownerUid)
            check(remoteDevice.getString(ROLE) == device.role.name)

            val pushRegistrations = family.collection(PUSH_REGISTRATIONS)
                .whereEqualTo(DEVICE_ID, device.deviceId)
                .get()
                .awaitFirebaseValue()

            val batch = firestore.batch()
            pushRegistrations.documents.forEach { batch.delete(it.reference) }
            batch.delete(deviceReference)
            batch.delete(firestore.collection(DEVICE_AUTHORIZATIONS).document(device.ownerUid))
            if (device.role == DeviceRole.ALARM_DEVICE) {
                batch.delete(firestore.collection(DEVICE_ACCESS).document(device.ownerUid))
            }
            batch.delete(firestore.collection(APPROVAL_REQUESTS).document(device.ownerUid))
            batch.commit().awaitFirebaseCompletion()
        }
    }

    override suspend fun saveContact(
        familyId: String,
        contact: FamilyContact,
    ): FamilyRepositoryResult<Unit> {
        if (auth.currentUser == null) return notAuthenticated()
        if (!isValidId(familyId)) return invalidInput()
        if (contact.displayName.isBlank() || contact.displayName.length > MAX_DISPLAY_NAME_LENGTH) {
            return invalidInput()
        }
        if (!PHONE_PATTERN.matches(contact.phoneNumber)) return invalidInput()
        return runFirestoreOperation {
            val contacts = firestore.collection(FAMILIES).document(familyId).collection(CONTACTS)
            val existing = contacts.get().awaitFirebaseValue().documents
                .map { it.toFamilyContact() }
            val batch = firestore.batch()
            if (contact.isDefault) {
                existing.filter { it.contactId != contact.contactId }.forEach { old ->
                    batch.update(contacts.document(old.contactId), IS_DEFAULT, false, UPDATED_AT, FieldValue.serverTimestamp())
                }
            }
            batch.set(
                contacts.document(contact.contactId),
                mapOf(
                    CONTACT_ID to contact.contactId,
                    FAMILY_ID to familyId,
                    DISPLAY_NAME to contact.displayName.trim(),
                    PHONE_NUMBER to contact.phoneNumber,
                    IS_DEFAULT to contact.isDefault,
                    UPDATED_AT to FieldValue.serverTimestamp(),
                ),
            )
            batch.commit().awaitFirebaseCompletion()
        }
    }

    override suspend fun deleteContact(
        familyId: String,
        contactId: String,
    ): FamilyRepositoryResult<Unit> {
        if (auth.currentUser == null) return notAuthenticated()
        if (!isValidId(familyId) || contactId.isBlank()) {
            return invalidInput()
        }
        return runFirestoreOperation {
            firestore.collection(FAMILIES).document(familyId).collection(CONTACTS)
                .document(contactId).delete().awaitFirebaseCompletion()
        }
    }

    private fun <T> snapshotsFlow(
        query: com.google.firebase.firestore.Query,
        mapper: (QuerySnapshot) -> T,
    ): Flow<T> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot, error ->
            when {
                error != null -> close(error)
                snapshot != null -> runCatching { mapper(snapshot) }
                    .onSuccess(::trySend)
                    .onFailure(::close)
            }
        }
        awaitClose { registration.remove() }
    }

    private suspend fun <T> runFirestoreOperation(
        block: suspend () -> T,
    ): FamilyRepositoryResult<T> = try {

        Log.d(
            "YanindaFirestore",
            "Firestore operation started."
        )

        val result = block()

        Log.d(
            "YanindaFirestore",
            "Firestore operation completed successfully."
        )

        FamilyRepositoryResult.Success(result)

    } catch (error: Exception) {
        val failureCode =
            (error as? FirebaseFirestoreException)
                ?.code
                ?.name
                ?: error::class.java.simpleName

        Log.e(
            "YanindaFirestore",
            "Firestore operation failed. error=$failureCode",
        )

        FamilyRepositoryResult.Failure(
            error.toRepositoryFailure()
        )
    }

    private fun DocumentSnapshot.toFamilyMembership(): FamilyMembership =
        FamilyMembership(
            familyId = requireNotNull(getString(FAMILY_ID)),
            familyName = requireNotNull(getString(FAMILY_NAME)),
            role = FamilyMemberRole.valueOf(
                requireNotNull(getString(ROLE))
            ),
            displayName = requireNotNull(getString(DISPLAY_NAME)),
            joinedAt = requireNotNull(
                getTimestamp(
                    JOINED_AT,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                )
            ).toDate().toInstant(),
        )

    private fun DocumentSnapshot.toFamilyContact(): FamilyContact = FamilyContact(
        contactId = requireNotNull(getString(CONTACT_ID)),
        familyId = requireNotNull(getString(FAMILY_ID)),
        displayName = requireNotNull(getString(DISPLAY_NAME)),
        phoneNumber = requireNotNull(getString(PHONE_NUMBER)),
        isDefault = getBoolean(IS_DEFAULT) ?: false,
        updatedAt = requireNotNull(
            getTimestamp(UPDATED_AT, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        ).toDate().toInstant(),
    )

    private fun DocumentSnapshot.toDeviceRegistration(): DeviceRegistration = DeviceRegistration(
        deviceId = requireNotNull(getString(DEVICE_ID)),
        familyId = requireNotNull(getString(FAMILY_ID)),
        ownerUid = requireNotNull(getString(OWNER_UID)),
        role = DeviceRole.valueOf(requireNotNull(getString(ROLE))),
        displayName = requireNotNull(getString(DISPLAY_NAME)),
        appVersion = requireNotNull(getString(APP_VERSION)),
        lastSeenAt = getTimestamp(LAST_SEEN_AT)?.toDate()?.toInstant(),
        lastSuccessfulSyncAt = getTimestamp(LAST_SUCCESSFUL_SYNC_AT)?.toDate()?.toInstant(),
        version = requireNotNull(getLong(VERSION)),
    )

    private fun DocumentSnapshot.toPendingDeviceApproval(): PendingDeviceApproval =
        PendingDeviceApproval(
            uid = requireNotNull(getString(UID)),
            familyId = requireNotNull(getString(FAMILY_ID)),
            deviceId = requireNotNull(getString(DEVICE_ID)),
            requestedRole = DeviceRole.valueOf(requireNotNull(getString(REQUESTED_ROLE))),
            displayName = requireNotNull(getString(DISPLAY_NAME)),
            appVersion = requireNotNull(getString(APP_VERSION)),
            requestedAt = requireNotNull(getTimestamp(REQUESTED_AT)).toDate().toInstant(),
        )

    private fun DocumentSnapshot.toFamilyDoseOccurrence(): FamilyDoseOccurrence =
        FamilyDoseOccurrence(
            occurrenceId = requireNotNull(getString(OCCURRENCE_ID)),
            medicationDisplayName = requireNotNull(getString(MEDICATION_DISPLAY_NAME)),
            scheduledAt = requireNotNull(getTimestamp(SCHEDULED_AT)).toDate().toInstant(),
            status = DoseOccurrenceStatus.valueOf(requireNotNull(getString(STATUS))),
            acknowledgedAt = getTimestamp(ACKNOWLEDGED_AT)?.toDate()?.toInstant(),
            acknowledgementActor = getString(ACKNOWLEDGEMENT_ACTOR)
                ?.let(AcknowledgementActor::valueOf),
            lastAlertedAt = getTimestamp(LAST_ALERTED_AT)?.toDate()?.toInstant(),
            updatedAt = requireNotNull(getTimestamp(UPDATED_AT)).toDate().toInstant(),
            syncedAt = requireNotNull(getTimestamp(SYNCED_AT)).toDate().toInstant(),
            version = requireNotNull(getLong(VERSION)),
            sourceDeviceId = requireNotNull(getString(SOURCE_DEVICE_ID)),
        )

    private fun Exception.toRepositoryFailure(): FamilyRepositoryFailure = when (this) {
        is FirebaseNetworkException -> FamilyRepositoryFailure.NETWORK_UNAVAILABLE
        is FirebaseFirestoreException -> when (code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                FamilyRepositoryFailure.PERMISSION_DENIED

            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
            -> FamilyRepositoryFailure.NETWORK_UNAVAILABLE

            else -> FamilyRepositoryFailure.UNKNOWN
        }

        else -> FamilyRepositoryFailure.UNKNOWN
    }

    private fun isValidId(value: String): Boolean =
        value.isNotBlank() && value.length <= MAX_ID_LENGTH && '/' !in value

    private fun notAuthenticated() = FamilyRepositoryResult.Failure(
        FamilyRepositoryFailure.NOT_AUTHENTICATED
    )

    private fun invalidInput() = FamilyRepositoryResult.Failure(
        FamilyRepositoryFailure.INVALID_INPUT
    )

    private companion object {
        const val MAX_DISPLAY_NAME_LENGTH = 80
        const val MAX_ID_LENGTH = 128
        const val MAX_MONITORING_OCCURRENCES = 500L

        const val FAMILIES = "families"
        const val USERS = "users"
        const val MEMBERSHIPS = "memberships"
        const val CONTACTS = "contacts"
        const val DEVICES = "devices"
        const val OCCURRENCES = "occurrences"
        const val APPROVAL_REQUESTS = "deviceApprovalRequests"
        const val DEVICE_AUTHORIZATIONS = "deviceAuthorizations"
        const val DEVICE_ACCESS = "deviceAccess"
        const val PUSH_REGISTRATIONS = "pushRegistrations"

        const val FAMILY_ID = "familyId"
        const val FAMILY_NAME = "familyName"
        const val ROLE = "role"
        const val DISPLAY_NAME = "displayName"
        const val CONTACT_ID = "contactId"
        const val PHONE_NUMBER = "phoneNumber"
        const val IS_DEFAULT = "isDefault"
        const val JOINED_AT = "joinedAt"
        const val VERSION = "version"
        const val DEVICE_ID = "deviceId"
        const val OWNER_UID = "ownerUid"
        const val APP_VERSION = "appVersion"
        const val LAST_SEEN_AT = "lastSeenAt"
        const val LAST_SUCCESSFUL_SYNC_AT = "lastSuccessfulSyncAt"
        const val OCCURRENCE_ID = "occurrenceId"
        const val MEDICATION_DISPLAY_NAME = "medicationDisplayName"
        const val SCHEDULED_AT = "scheduledAt"
        const val STATUS = "status"
        const val ACKNOWLEDGED_AT = "acknowledgedAt"
        const val ACKNOWLEDGEMENT_ACTOR = "acknowledgementActor"
        const val LAST_ALERTED_AT = "lastAlertedAt"
        const val UPDATED_AT = "updatedAt"
        const val SYNCED_AT = "syncedAt"
        const val SOURCE_DEVICE_ID = "sourceDeviceId"
        const val UID = "uid"
        const val REQUESTED_ROLE = "requestedRole"
        const val REQUESTED_AT = "requestedAt"
        const val ACTIVE = "active"
        const val APPROVED_AT = "approvedAt"
        const val APPROVED_BY_UID = "approvedByUid"
        val PHONE_PATTERN = Regex("^\\+?[0-9]{7,15}$")
    }
}

internal data class FamilyOccurrenceObservationWindow(
    val startInclusive: Instant,
    val endExclusive: Instant,
)

internal fun familyOccurrenceObservationWindow(
    now: Instant,
    zoneId: ZoneId,
    historyDays: Long = 90L,
): FamilyOccurrenceObservationWindow {
    require(historyDays > 0L) {
        "History day count must be positive."
    }

    val today = now.atZone(zoneId).toLocalDate()
    return FamilyOccurrenceObservationWindow(
        startInclusive = today
            .minusDays(historyDays - 1L)
            .atStartOfDay(zoneId)
            .toInstant(),
        endExclusive = today
            .plusDays(1L)
            .atStartOfDay(zoneId)
            .toInstant(),
    )
}
