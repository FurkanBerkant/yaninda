package com.berkant.yaninda.family

import com.berkant.yaninda.domain.family.DeviceRegistration
import com.berkant.yaninda.domain.family.DeviceRole
import com.berkant.yaninda.domain.family.FamilyMember
import com.berkant.yaninda.domain.family.FamilyContact
import com.berkant.yaninda.domain.family.FamilyMemberRole
import com.berkant.yaninda.domain.family.FamilyDoseOccurrence
import com.berkant.yaninda.domain.family.FamilyMembership
import com.berkant.yaninda.domain.family.FamilyPairing
import com.berkant.yaninda.domain.family.PairingCodeGenerator
import com.berkant.yaninda.domain.family.PairingCodeNormalizer
import com.berkant.yaninda.domain.family.PairingInvitation
import com.berkant.yaninda.domain.occurrence.AcknowledgementActor
import com.berkant.yaninda.domain.occurrence.DoseOccurrenceStatus
import com.berkant.yaninda.firebase.awaitFirebaseCompletion
import com.berkant.yaninda.firebase.awaitFirebaseValue
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QuerySnapshot
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import android.util.Log
enum class FamilyRepositoryFailure {
    NOT_AUTHENTICATED,
    PERMISSION_DENIED,
    NETWORK_UNAVAILABLE,
    INVALID_INPUT,
    INVITATION_INVALID,
    INVITATION_EXPIRED,
    INVITATION_ALREADY_USED,
    ROLE_MISMATCH,
    NOT_CONFIGURED,
    UNKNOWN,
}

sealed interface FamilyRepositoryResult<out T> {
    data class Success<T>(val value: T) : FamilyRepositoryResult<T>

    data class Failure(val reason: FamilyRepositoryFailure) : FamilyRepositoryResult<Nothing>
}

interface FamilyRepository {
    fun observeMemberships(): Flow<List<FamilyMembership>>

    fun observeMembers(familyId: String): Flow<List<FamilyMember>>

    fun observeDevices(familyId: String): Flow<List<DeviceRegistration>>

    fun observeOccurrences(familyId: String): Flow<List<FamilyDoseOccurrence>>

    fun observeContacts(familyId: String): Flow<List<FamilyContact>>

    suspend fun saveContact(
        familyId: String,
        contact: FamilyContact,
    ): FamilyRepositoryResult<Unit>

    suspend fun deleteContact(
        familyId: String,
        contactId: String,
    ): FamilyRepositoryResult<Unit>

    suspend fun createFamily(
        familyName: String,
        caregiverDisplayName: String,
        deviceId: String,
        appVersion: String,
    ): FamilyRepositoryResult<FamilyMembership>

    suspend fun createPairingInvitation(
        familyId: String,
        targetRole: DeviceRole,
    ): FamilyRepositoryResult<PairingInvitation>

    suspend fun claimPairingInvitation(
        code: String,
        expectedRole: DeviceRole,
        deviceId: String,
        deviceDisplayName: String,
        appVersion: String,
    ): FamilyRepositoryResult<FamilyPairing>
}

object UnavailableFamilyRepository : FamilyRepository {
    override fun observeMemberships(): Flow<List<FamilyMembership>> = flowOf(emptyList())

    override fun observeMembers(familyId: String): Flow<List<FamilyMember>> = flowOf(emptyList())

    override fun observeDevices(familyId: String): Flow<List<DeviceRegistration>> =
        flowOf(emptyList())

    override fun observeOccurrences(familyId: String): Flow<List<FamilyDoseOccurrence>> =
        flowOf(emptyList())

    override fun observeContacts(familyId: String): Flow<List<FamilyContact>> =
        flowOf(emptyList())

    override suspend fun saveContact(
        familyId: String,
        contact: FamilyContact,
    ): FamilyRepositoryResult<Unit> = notConfigured()

    override suspend fun deleteContact(
        familyId: String,
        contactId: String,
    ): FamilyRepositoryResult<Unit> = notConfigured()

    override suspend fun createFamily(
        familyName: String,
        caregiverDisplayName: String,
        deviceId: String,
        appVersion: String,
    ): FamilyRepositoryResult<FamilyMembership> = notConfigured()

    override suspend fun createPairingInvitation(
        familyId: String,
        targetRole: DeviceRole,
    ): FamilyRepositoryResult<PairingInvitation> = notConfigured()

    override suspend fun claimPairingInvitation(
        code: String,
        expectedRole: DeviceRole,
        deviceId: String,
        deviceDisplayName: String,
        appVersion: String,
    ): FamilyRepositoryResult<FamilyPairing> = notConfigured()

    private fun notConfigured() = FamilyRepositoryResult.Failure(
        FamilyRepositoryFailure.NOT_CONFIGURED
    )
}

class FirestoreFamilyRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val pairingCodeGenerator: PairingCodeGenerator = PairingCodeGenerator(),
    private val now: () -> Instant = Instant::now,
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

    override fun observeMembers(familyId: String): Flow<List<FamilyMember>> {
        if (!isValidId(familyId)) return flowOf(emptyList())
        return snapshotsFlow(
            firestore.collection(FAMILIES)
                .document(familyId)
                .collection(MEMBERS),
        ) { snapshot ->
            snapshot.documents.map { document -> document.toFamilyMember() }
                .sortedWith(compareBy(FamilyMember::role, FamilyMember::displayName))
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
        return snapshotsFlow(
            firestore.collection(FAMILIES)
                .document(familyId)
                .collection(OCCURRENCES)
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

    override suspend fun saveContact(
        familyId: String,
        contact: FamilyContact,
    ): FamilyRepositoryResult<Unit> {
        val user = auth.currentUser ?: return notAuthenticated()
        if (user.isAnonymous || !isValidId(familyId)) return notAuthenticated()
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
        val user = auth.currentUser ?: return notAuthenticated()
        if (user.isAnonymous || !isValidId(familyId) || contactId.isBlank()) {
            return notAuthenticated()
        }
        return runFirestoreOperation {
            firestore.collection(FAMILIES).document(familyId).collection(CONTACTS)
                .document(contactId).delete().awaitFirebaseCompletion()
        }
    }

    override suspend fun createFamily(
        familyName: String,
        caregiverDisplayName: String,
        deviceId: String,
        appVersion: String,
    ): FamilyRepositoryResult<FamilyMembership> {
        val user = auth.currentUser ?: return notAuthenticated()
        if (user.isAnonymous) return notAuthenticated()
        val normalizedFamilyName = normalizeLabel(familyName, MAX_FAMILY_NAME_LENGTH)
            ?: return invalidInput()
        val normalizedDisplayName = normalizeLabel(
            caregiverDisplayName,
            MAX_DISPLAY_NAME_LENGTH,
        ) ?: return invalidInput()
        if (!isValidId(deviceId) || !isValidAppVersion(appVersion)) return invalidInput()

        return runFirestoreOperation {
            val familyReference = firestore.collection(FAMILIES).document()
            val familyId = familyReference.id
            val memberReference = familyReference.collection(MEMBERS).document(user.uid)
            val membershipReference = firestore.collection(USERS)
                .document(user.uid)
                .collection(MEMBERSHIPS)
                .document(familyId)
            val deviceReference = familyReference.collection(DEVICES).document(deviceId)
            val serverTime = FieldValue.serverTimestamp()
            firestore.batch().apply {
                set(
                    familyReference,
                    mapOf(
                        FAMILY_ID to familyId,
                        NAME to normalizedFamilyName,
                        CREATED_BY_UID to user.uid,
                        CREATED_AT to serverTime,
                        VERSION to 1L,
                    ),
                )
                set(
                    memberReference,
                    memberDocument(
                        userId = user.uid,
                        familyId = familyId,
                        role = FamilyMemberRole.ADMIN,
                        displayName = normalizedDisplayName,
                        pairingInviteId = null,
                        deviceId = deviceId,
                        serverTime = serverTime,
                    ),
                )
                set(
                    membershipReference,
                    membershipDocument(
                        familyId = familyId,
                        familyName = normalizedFamilyName,
                        role = FamilyMemberRole.ADMIN,
                        displayName = normalizedDisplayName,
                        serverTime = serverTime,
                    ),
                )
                set(
                    deviceReference,
                    deviceDocument(
                        deviceId = deviceId,
                        familyId = familyId,
                        ownerUid = user.uid,
                        role = DeviceRole.ADMIN_DEVICE,
                        displayName = normalizedDisplayName,
                        appVersion = appVersion,
                        pairingInviteId = null,
                        serverTime = serverTime,
                    ),
                )
            }.commit().awaitFirebaseCompletion()
            FamilyMembership(
                familyId = familyId,
                familyName = normalizedFamilyName,
                role = FamilyMemberRole.ADMIN,
                displayName = normalizedDisplayName,
                joinedAt = now(),
            )
        }
    }

    override suspend fun createPairingInvitation(
        familyId: String,
        targetRole: DeviceRole,
    ): FamilyRepositoryResult<PairingInvitation> {
        val user = auth.currentUser ?: return notAuthenticated()
        if (!isValidId(familyId)) return invalidInput()
        val createdAt = now()
        val expiresAt = createdAt.plus(INVITATION_LIFETIME)
        return runFirestoreOperation {
            val code = pairingCodeGenerator.create()
            val reference = firestore.collection(PAIRING_INVITES).document(code)
            reference.set(
                mapOf(
                    INVITE_ID to code,
                    FAMILY_ID to familyId,
                    TARGET_ROLE to targetRole.name,
                    CREATED_BY_UID to user.uid,
                    CREATED_AT to FieldValue.serverTimestamp(),
                    EXPIRES_AT to Timestamp(Date.from(expiresAt)),
                    CLAIMED_BY_UID to null,
                    CLAIMED_DEVICE_ID to null,
                    CLAIMED_AT to null,
                    VERSION to 1L,
                )
            ).awaitFirebaseCompletion()
            PairingInvitation(
                code = code,
                familyId = familyId,
                targetRole = targetRole,
                expiresAt = expiresAt,
            )
        }
    }

    override suspend fun claimPairingInvitation(
        code: String,
        expectedRole: DeviceRole,
        deviceId: String,
        deviceDisplayName: String,
        appVersion: String,
    ): FamilyRepositoryResult<FamilyPairing> {
        val user = auth.currentUser ?: return notAuthenticated()

        val normalizedCode = PairingCodeNormalizer.normalize(code)
            ?: return FamilyRepositoryResult.Failure(
                FamilyRepositoryFailure.INVITATION_INVALID
            )

        val normalizedDisplayName = normalizeLabel(
            deviceDisplayName,
            MAX_DISPLAY_NAME_LENGTH,
        ) ?: return invalidInput()

        if (!isValidId(deviceId) || !isValidAppVersion(appVersion)) {
            return invalidInput()
        }

        /*
         * ALARM_DEVICE may use an anonymous Firebase session.
         *
         * ADMIN_DEVICE pairing requires a normal authenticated user.
         */
        if (
            expectedRole == DeviceRole.ADMIN_DEVICE &&
            user.isAnonymous
        ) {
            return notAuthenticated()
        }

        return try {
            val pairing = firestore.runTransaction { transaction ->

                val inviteReference = firestore
                    .collection(PAIRING_INVITES)
                    .document(normalizedCode)

                val invite = transaction.get(inviteReference)

                if (!invite.exists()) {
                    throw PairingClaimException.Invalid
                }

                if (invite.getString(CLAIMED_BY_UID) != null) {
                    throw PairingClaimException.AlreadyUsed
                }

                val expiresAt = invite
                    .getTimestamp(EXPIRES_AT)
                    ?.toDate()
                    ?.toInstant()
                    ?: throw PairingClaimException.Invalid

                if (!expiresAt.isAfter(now())) {
                    throw PairingClaimException.Expired
                }

                val targetRole = invite
                    .getString(TARGET_ROLE)
                    ?.let { value ->
                        runCatching {
                            DeviceRole.valueOf(value)
                        }.getOrNull()
                    }
                    ?: throw PairingClaimException.Invalid

                if (targetRole != expectedRole) {
                    throw PairingClaimException.RoleMismatch
                }

                val familyId = invite
                    .getString(FAMILY_ID)
                    ?.takeIf(::isValidId)
                    ?: throw PairingClaimException.Invalid

                val familyReference = firestore
                    .collection(FAMILIES)
                    .document(familyId)

                /*
                 * IMPORTANT V2 RULE
                 *
                 * ALARM_DEVICE does NOT need to read the family document
                 * while claiming an invitation.
                 *
                 * Before pairing, an ALARM_DEVICE is intentionally not a
                 * family member and Firestore rules should not expose the
                 * family document to it.
                 *
                 * ADMIN_DEVICE currently still needs the family name because
                 * its membership projection stores that value.
                 */
                val familyName = if (targetRole == DeviceRole.ADMIN_DEVICE) {
                    val family = transaction.get(familyReference)

                    family.getString(NAME)
                        ?.takeIf {
                            normalizeLabel(
                                it,
                                MAX_FAMILY_NAME_LENGTH,
                            ) != null
                        }
                        ?: throw PairingClaimException.Invalid
                } else {
                    null
                }

                val serverTime = FieldValue.serverTimestamp()

                /*
                 * Mark this one-time invitation as claimed.
                 */
                transaction.update(
                    inviteReference,
                    mapOf(
                        CLAIMED_BY_UID to user.uid,
                        CLAIMED_DEVICE_ID to deviceId,
                        CLAIMED_AT to serverTime,
                        VERSION to 2L,
                    ),
                )

                /*
                 * Register the physical device inside the family.
                 */
                transaction.set(
                    familyReference
                        .collection(DEVICES)
                        .document(deviceId),
                    deviceDocument(
                        deviceId = deviceId,
                        familyId = familyId,
                        ownerUid = user.uid,
                        role = targetRole,
                        displayName = normalizedDisplayName,
                        appVersion = appVersion,
                        pairingInviteId = normalizedCode,
                        serverTime = serverTime,
                    ),
                )

                /*
                 * ALARM_DEVICE is a device, not a human family member.
                 *
                 * Therefore it does NOT receive:
                 * - a FamilyMember record
                 * - a user membership projection
                 *
                 * ADMIN_DEVICE does.
                 */
                if (targetRole == DeviceRole.ADMIN_DEVICE) {
                    val memberRole =
                        FamilyMemberRole.CAREGIVER_VIEWER

                    transaction.set(
                        familyReference
                            .collection(MEMBERS)
                            .document(user.uid),
                        memberDocument(
                            userId = user.uid,
                            familyId = familyId,
                            role = memberRole,
                            displayName = normalizedDisplayName,
                            pairingInviteId = normalizedCode,
                            deviceId = deviceId,
                            serverTime = serverTime,
                        ),
                    )

                    transaction.set(
                        firestore
                            .collection(USERS)
                            .document(user.uid)
                            .collection(MEMBERSHIPS)
                            .document(familyId),
                        membershipDocument(
                            familyId = familyId,
                            familyName = requireNotNull(familyName),
                            role = memberRole,
                            displayName = normalizedDisplayName,
                            serverTime = serverTime,
                        ),
                    )
                }

                FamilyPairing(
                    familyId = familyId,
                    deviceRole = targetRole,
                )
            }.awaitFirebaseValue()

            FamilyRepositoryResult.Success(pairing)

        } catch (_: PairingClaimException.Invalid) {
            FamilyRepositoryResult.Failure(
                FamilyRepositoryFailure.INVITATION_INVALID
            )

        } catch (_: PairingClaimException.Expired) {
            FamilyRepositoryResult.Failure(
                FamilyRepositoryFailure.INVITATION_EXPIRED
            )

        } catch (_: PairingClaimException.AlreadyUsed) {
            FamilyRepositoryResult.Failure(
                FamilyRepositoryFailure.INVITATION_ALREADY_USED
            )

        } catch (_: PairingClaimException.RoleMismatch) {
            FamilyRepositoryResult.Failure(
                FamilyRepositoryFailure.ROLE_MISMATCH
            )

        } catch (error: Exception) {
            FamilyRepositoryResult.Failure(
                error.toRepositoryFailure()
            )
        }
    }

    private fun memberDocument(
        userId: String,
        familyId: String,
        role: FamilyMemberRole,
        displayName: String,
        pairingInviteId: String?,
        deviceId: String,
        serverTime: FieldValue,
    ): Map<String, Any?> = mapOf(
        UID to userId,
        FAMILY_ID to familyId,
        ROLE to role.name,
        DISPLAY_NAME to displayName,
        JOINED_AT to serverTime,
        PAIRING_INVITE_ID to pairingInviteId,
        DEVICE_ID to deviceId,
        VERSION to 1L,
    )

    private fun membershipDocument(
        familyId: String,
        familyName: String,
        role: FamilyMemberRole,
        displayName: String,
        serverTime: FieldValue,
    ): Map<String, Any> = mapOf(
        FAMILY_ID to familyId,
        FAMILY_NAME to familyName,
        ROLE to role.name,
        DISPLAY_NAME to displayName,
        JOINED_AT to serverTime,
        VERSION to 1L,
    )

    private fun deviceDocument(
        deviceId: String,
        familyId: String,
        ownerUid: String,
        role: DeviceRole,
        displayName: String,
        appVersion: String,
        pairingInviteId: String?,
        serverTime: FieldValue,
    ): Map<String, Any?> = mapOf(
        DEVICE_ID to deviceId,
        FAMILY_ID to familyId,
        OWNER_UID to ownerUid,
        ROLE to role.name,
        DISPLAY_NAME to displayName,
        APP_VERSION to appVersion,
        LAST_SEEN_AT to serverTime,
        LAST_SUCCESSFUL_SYNC_AT to null,
        PAIRING_INVITE_ID to pairingInviteId,
        VERSION to 1L,
    )

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

        if (error is FirebaseFirestoreException) {
            Log.e(
                "YanindaFirestore",
                """
            Firestore operation FAILED
            code=${error.code}
            message=${error.message}
            cause=${error.cause}
            """.trimIndent(),
                error,
            )
        } else {
            Log.e(
                "YanindaFirestore",
                """
            Firebase operation FAILED
            type=${error::class.java.name}
            message=${error.message}
            cause=${error.cause}
            """.trimIndent(),
                error,
            )
        }

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

    private fun DocumentSnapshot.toFamilyMember(): FamilyMember =
        FamilyMember(
            uid = requireNotNull(getString(UID)),
            familyId = requireNotNull(getString(FAMILY_ID)),
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
            version = requireNotNull(getLong(VERSION)),
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
        PairingClaimException.Invalid -> FamilyRepositoryFailure.INVITATION_INVALID
        PairingClaimException.Expired -> FamilyRepositoryFailure.INVITATION_EXPIRED
        PairingClaimException.AlreadyUsed -> FamilyRepositoryFailure.INVITATION_ALREADY_USED
        PairingClaimException.RoleMismatch -> FamilyRepositoryFailure.ROLE_MISMATCH
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

    private fun normalizeLabel(value: String, maxLength: Int): String? = value
        .trim()
        .replace(Regex("\\s+"), " ")
        .takeIf { it.isNotEmpty() && it.length <= maxLength }

    private fun isValidId(value: String): Boolean =
        value.isNotBlank() && value.length <= MAX_ID_LENGTH && '/' !in value

    private fun isValidAppVersion(value: String): Boolean =
        value.isNotBlank() && value.length <= MAX_APP_VERSION_LENGTH

    private fun notAuthenticated() = FamilyRepositoryResult.Failure(
        FamilyRepositoryFailure.NOT_AUTHENTICATED
    )

    private fun invalidInput() = FamilyRepositoryResult.Failure(
        FamilyRepositoryFailure.INVALID_INPUT
    )

    private sealed class PairingClaimException : RuntimeException() {
        data object Invalid : PairingClaimException()
        data object Expired : PairingClaimException()
        data object AlreadyUsed : PairingClaimException()
        data object RoleMismatch : PairingClaimException()
    }

    private companion object {
        val INVITATION_LIFETIME: Duration = Duration.ofMinutes(15)
        const val MAX_FAMILY_NAME_LENGTH = 80
        const val MAX_DISPLAY_NAME_LENGTH = 80
        const val MAX_APP_VERSION_LENGTH = 40
        const val MAX_ID_LENGTH = 128
        const val MAX_MONITORING_OCCURRENCES = 50L

        const val FAMILIES = "families"
        const val USERS = "users"
        const val MEMBERSHIPS = "memberships"
        const val MEMBERS = "members"
        const val CONTACTS = "contacts"
        const val DEVICES = "devices"
        const val PAIRING_INVITES = "pairingInvites"
        const val OCCURRENCES = "occurrences"

        const val UID = "uid"
        const val FAMILY_ID = "familyId"
        const val FAMILY_NAME = "familyName"
        const val NAME = "name"
        const val ROLE = "role"
        const val DISPLAY_NAME = "displayName"
        const val CONTACT_ID = "contactId"
        const val PHONE_NUMBER = "phoneNumber"
        const val IS_DEFAULT = "isDefault"
        const val CREATED_BY_UID = "createdByUid"
        const val CREATED_AT = "createdAt"
        const val JOINED_AT = "joinedAt"
        const val VERSION = "version"
        const val DEVICE_ID = "deviceId"
        const val OWNER_UID = "ownerUid"
        const val APP_VERSION = "appVersion"
        const val LAST_SEEN_AT = "lastSeenAt"
        const val LAST_SUCCESSFUL_SYNC_AT = "lastSuccessfulSyncAt"
        const val PAIRING_INVITE_ID = "pairingInviteId"
        const val INVITE_ID = "inviteId"
        const val TARGET_ROLE = "targetRole"
        const val EXPIRES_AT = "expiresAt"
        const val CLAIMED_BY_UID = "claimedByUid"
        const val CLAIMED_DEVICE_ID = "claimedDeviceId"
        const val CLAIMED_AT = "claimedAt"
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
        val PHONE_PATTERN = Regex("^\\+?[0-9]{7,15}$")
    }
}
