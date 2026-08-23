package com.berkant.yaninda.schedule

import com.berkant.yaninda.domain.medication.DayOfWeekMask
import com.berkant.yaninda.domain.medication.ValidatedMedicationDraft
import com.berkant.yaninda.firebase.awaitFirebaseValue
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf

data class PublishedScheduleVersion(
    val familyId: String,
    val version: Long,
    val medications: List<PublishedMedication>,
    val publishedAt: Instant?,
    val publishedByUid: String?,
) {
    companion object {
        fun empty(familyId: String) = PublishedScheduleVersion(
            familyId = familyId,
            version = 0,
            medications = emptyList(),
            publishedAt = null,
            publishedByUid = null,
        )
    }
}

data class PublishedMedication(
    val medicationId: String,
    val displayName: String,
    val dosageText: String,
    val instructionText: String,
    val active: Boolean,
    val schedules: List<PublishedMedicationSchedule>,
)

data class PublishedMedicationSchedule(
    val scheduleId: String,
    val localTimeMinutes: Int,
    val daysOfWeekMask: Int,
    val snoozeEnabled: Boolean,
    val snoozeMinutes: Int,
    val maxSnoozes: Int,
)

enum class AdminScheduleFailure {
    NOT_AUTHENTICATED,
    PERMISSION_DENIED,
    NETWORK_UNAVAILABLE,
    INVALID_INPUT,
    UNKNOWN,
}

sealed interface AdminScheduleResult<out T> {
    data class Success<T>(
        val value: T,
    ) : AdminScheduleResult<T>

    data class Failure(
        val reason: AdminScheduleFailure,
    ) : AdminScheduleResult<Nothing>
}

interface AdminScheduleRepository {

    fun observeCurrentSchedule(
        familyId: String,
    ): Flow<PublishedScheduleVersion>

    suspend fun saveMedication(
        familyId: String,
        draft: ValidatedMedicationDraft,
    ): AdminScheduleResult<Long>

    suspend fun deleteMedication(
        familyId: String,
        medicationId: String,
    ): AdminScheduleResult<Long>
}

object UnavailableAdminScheduleRepository : AdminScheduleRepository {

    override fun observeCurrentSchedule(
        familyId: String,
    ): Flow<PublishedScheduleVersion> =
        flowOf(PublishedScheduleVersion.empty(familyId))

    override suspend fun saveMedication(
        familyId: String,
        draft: ValidatedMedicationDraft,
    ): AdminScheduleResult<Long> =
        AdminScheduleResult.Failure(
            AdminScheduleFailure.NETWORK_UNAVAILABLE
        )

    override suspend fun deleteMedication(
        familyId: String,
        medicationId: String,
    ): AdminScheduleResult<Long> = AdminScheduleResult.Failure(
        AdminScheduleFailure.NETWORK_UNAVAILABLE
    )
}

class FirestoreAdminScheduleRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : AdminScheduleRepository {

    override fun observeCurrentSchedule(
        familyId: String,
    ): Flow<PublishedScheduleVersion> {
        if (!isValidFamilyId(familyId)) {
            return flowOf(
                PublishedScheduleVersion.empty(familyId)
            )
        }

        return callbackFlow {
            val stateReference = firestore
                .collection(FAMILIES)
                .document(familyId)
                .collection(SCHEDULE_STATE)
                .document(CURRENT)

            var versionRegistration: ListenerRegistration? = null
            var observedVersion: Long? = null

            val stateRegistration =
                stateReference.addSnapshotListener { snapshot, error ->

                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }

                    val desiredVersion =
                        snapshot?.getLong(DESIRED_VERSION) ?: 0L

                    if (desiredVersion <= 0L) {
                        observedVersion = 0L
                        versionRegistration?.remove()
                        versionRegistration = null

                        trySend(
                            PublishedScheduleVersion.empty(familyId)
                        )

                        return@addSnapshotListener
                    }

                    if (observedVersion == desiredVersion) {
                        return@addSnapshotListener
                    }

                    observedVersion = desiredVersion

                    versionRegistration?.remove()

                    val versionReference = firestore
                        .collection(FAMILIES)
                        .document(familyId)
                        .collection(SCHEDULE_VERSIONS)
                        .document(desiredVersion.toString())

                    versionRegistration =
                        versionReference.addSnapshotListener versionListener@{
                                versionSnapshot,
                                versionError,
                            ->

                            if (versionError != null) {
                                close(versionError)
                                return@versionListener
                            }

                            if (
                                versionSnapshot == null ||
                                !versionSnapshot.exists()
                            ) {
                                return@versionListener
                            }

                            try {
                                trySend(
                                    versionSnapshot
                                        .toPublishedScheduleVersion()
                                )
                            } catch (error: Exception) {
                                close(error)
                            }
                        }
                }

            awaitClose {
                stateRegistration.remove()
                versionRegistration?.remove()
            }
        }
    }

    override suspend fun saveMedication(
        familyId: String,
        draft: ValidatedMedicationDraft,
    ): AdminScheduleResult<Long> {

        val user = auth.currentUser
            ?: return AdminScheduleResult.Failure(
                AdminScheduleFailure.NOT_AUTHENTICATED
            )

        if (user.isAnonymous) {
            return AdminScheduleResult.Failure(
                AdminScheduleFailure.NOT_AUTHENTICATED
            )
        }

        if (!isValidFamilyId(familyId)) {
            return AdminScheduleResult.Failure(
                AdminScheduleFailure.INVALID_INPUT
            )
        }

        return try {
            val stateReference = firestore
                .collection(FAMILIES)
                .document(familyId)
                .collection(SCHEDULE_STATE)
                .document(CURRENT)

            val medicationId =
                draft.medicationId ?: UUID.randomUUID().toString()

            val nextVersion = firestore.runTransaction { transaction ->

                val stateSnapshot =
                    transaction.get(stateReference)

                val currentVersion =
                    stateSnapshot.getLong(DESIRED_VERSION) ?: 0L

                val currentMedications =
                    if (currentVersion > 0L) {

                        val currentVersionReference = firestore
                            .collection(FAMILIES)
                            .document(familyId)
                            .collection(SCHEDULE_VERSIONS)
                            .document(currentVersion.toString())

                        val currentVersionSnapshot =
                            transaction.get(currentVersionReference)

                        if (!currentVersionSnapshot.exists()) {
                            error(
                                "Current schedule version document is missing."
                            )
                        }

                        currentVersionSnapshot
                            .toPublishedScheduleVersion()
                            .medications

                    } else {
                        emptyList()
                    }

                val medication =
                    draft.toPublishedMedication(
                        medicationId = medicationId,
                    )

                val medications = currentMedications
                    .filterNot {
                        it.medicationId == medicationId
                    } + medication

                val version = currentVersion + 1L

                val versionReference = firestore
                    .collection(FAMILIES)
                    .document(familyId)
                    .collection(SCHEDULE_VERSIONS)
                    .document(version.toString())

                transaction.set(
                    versionReference,
                    mapOf(
                        FAMILY_ID to familyId,
                        SCHEDULE_VERSION to version,
                        SCHEMA_VERSION to CURRENT_SCHEMA_VERSION,
                        PUBLISHED_AT to FieldValue.serverTimestamp(),
                        PUBLISHED_BY_UID to user.uid,
                        MEDICATIONS to medications.map {
                            it.toFirestoreMap()
                        },
                    ),
                )

                transaction.set(
                    stateReference,
                    mapOf(
                        DESIRED_VERSION to version,
                        UPDATED_AT to FieldValue.serverTimestamp(),
                        UPDATED_BY_UID to user.uid,
                        SCHEMA_VERSION to CURRENT_SCHEMA_VERSION,
                    ),
                )

                version
            }.awaitFirebaseValue()

            AdminScheduleResult.Success(nextVersion)

        } catch (error: Exception) {
            AdminScheduleResult.Failure(
                error.toFailure()
            )
        }
    }

    override suspend fun deleteMedication(
        familyId: String,
        medicationId: String,
    ): AdminScheduleResult<Long> {
        val user = auth.currentUser
            ?: return AdminScheduleResult.Failure(AdminScheduleFailure.NOT_AUTHENTICATED)
        if (user.isAnonymous) {
            return AdminScheduleResult.Failure(AdminScheduleFailure.NOT_AUTHENTICATED)
        }
        if (!isValidFamilyId(familyId) || medicationId.isBlank()) {
            return AdminScheduleResult.Failure(AdminScheduleFailure.INVALID_INPUT)
        }

        return try {
            val stateReference = firestore.collection(FAMILIES).document(familyId)
                .collection(SCHEDULE_STATE).document(CURRENT)
            val nextVersion = firestore.runTransaction { transaction ->
                val stateSnapshot = transaction.get(stateReference)
                val currentVersion = stateSnapshot.getLong(DESIRED_VERSION) ?: 0L
                val currentVersionReference = firestore.collection(FAMILIES).document(familyId)
                    .collection(SCHEDULE_VERSIONS).document(currentVersion.toString())
                val currentMedications = if (currentVersion > 0L) {
                    transaction.get(currentVersionReference).toPublishedScheduleVersion().medications
                } else {
                    emptyList()
                }
                val medications = currentMedications.filterNot { it.medicationId == medicationId }
                if (medications.size == currentMedications.size) {
                    error("The medication being deleted does not exist.")
                }
                publishSchedule(transaction, stateReference, familyId, currentVersion, medications, user.uid)
            }.awaitFirebaseValue()
            AdminScheduleResult.Success(nextVersion)
        } catch (error: Exception) {
            AdminScheduleResult.Failure(error.toFailure())
        }
    }

    private fun publishSchedule(
        transaction: com.google.firebase.firestore.Transaction,
        stateReference: com.google.firebase.firestore.DocumentReference,
        familyId: String,
        currentVersion: Long,
        medications: List<PublishedMedication>,
        uid: String,
    ): Long {
        val version = currentVersion + 1L
        val versionReference = firestore.collection(FAMILIES).document(familyId)
            .collection(SCHEDULE_VERSIONS).document(version.toString())
        transaction.set(versionReference, mapOf(
            FAMILY_ID to familyId,
            SCHEDULE_VERSION to version,
            SCHEMA_VERSION to CURRENT_SCHEMA_VERSION,
            PUBLISHED_AT to FieldValue.serverTimestamp(),
            PUBLISHED_BY_UID to uid,
            MEDICATIONS to medications.map { it.toFirestoreMap() },
        ))
        transaction.set(stateReference, mapOf(
            DESIRED_VERSION to version,
            UPDATED_AT to FieldValue.serverTimestamp(),
            UPDATED_BY_UID to uid,
            SCHEMA_VERSION to CURRENT_SCHEMA_VERSION,
        ))
        return version
    }

    private fun ValidatedMedicationDraft.toPublishedMedication(
        medicationId: String,
    ): PublishedMedication {

        val daysMask =
            DayOfWeekMask.encode(daysOfWeek)

        return PublishedMedication(
            medicationId = medicationId,
            displayName = displayName,
            dosageText = dosageText,
            instructionText = instructionText,
            active = true,
            schedules = schedules.map { schedule ->
                PublishedMedicationSchedule(
                    scheduleId =
                        schedule.id ?: UUID.randomUUID().toString(),
                    localTimeMinutes =
                        schedule.localTime.toSecondOfDay() / 60,
                    daysOfWeekMask = daysMask,
                    snoozeEnabled = snoozeEnabled,
                    snoozeMinutes = snoozeMinutes,
                    maxSnoozes = maxSnoozes,
                )
            },
        )
    }

    private fun PublishedMedication.toFirestoreMap():
            Map<String, Any> =
        mapOf(
            MEDICATION_ID to medicationId,
            DISPLAY_NAME to displayName,
            DOSAGE_TEXT to dosageText,
            INSTRUCTION_TEXT to instructionText,
            ACTIVE to active,
            SCHEDULES to schedules.map {
                it.toFirestoreMap()
            },
        )

    private fun PublishedMedicationSchedule.toFirestoreMap():
            Map<String, Any> =
        mapOf(
            SCHEDULE_ID to scheduleId,
            LOCAL_TIME_MINUTES to localTimeMinutes,
            DAYS_OF_WEEK_MASK to daysOfWeekMask,
            SNOOZE_ENABLED to snoozeEnabled,
            SNOOZE_MINUTES to snoozeMinutes,
            MAX_SNOOZES to maxSnoozes,
        )

    private fun DocumentSnapshot.toPublishedScheduleVersion():
            PublishedScheduleVersion {

        val medications =
            (get(MEDICATIONS) as? List<*>)
                .orEmpty()
                .map { value ->
                    requireNotNull(
                        (value as? Map<*, *>)
                            ?.toPublishedMedication()
                    )
                }

        return PublishedScheduleVersion(
            familyId =
                requireNotNull(getString(FAMILY_ID)),
            version =
                requireNotNull(getLong(SCHEDULE_VERSION)),
            medications = medications,
            publishedAt = getTimestamp(
                PUBLISHED_AT,
                DocumentSnapshot
                    .ServerTimestampBehavior
                    .ESTIMATE,
            )
                ?.toDate()
                ?.toInstant(),
            publishedByUid =
                getString(PUBLISHED_BY_UID),
        )
    }

    private fun Map<*, *>.toPublishedMedication():
            PublishedMedication {

        val schedules =
            (this[SCHEDULES] as? List<*>)
                .orEmpty()
                .map { value ->
                    requireNotNull(
                        (value as? Map<*, *>)
                            ?.toPublishedMedicationSchedule()
                    )
                }

        return PublishedMedication(
            medicationId =
                requireNotNull(this[MEDICATION_ID] as? String),
            displayName =
                requireNotNull(this[DISPLAY_NAME] as? String),
            dosageText =
                requireNotNull(this[DOSAGE_TEXT] as? String),
            instructionText =
                requireNotNull(this[INSTRUCTION_TEXT] as? String),
            active =
                this[ACTIVE] as? Boolean ?: true,
            schedules = schedules,
        )
    }

    private fun Map<*, *>.toPublishedMedicationSchedule():
            PublishedMedicationSchedule =
        PublishedMedicationSchedule(
            scheduleId =
                requireNotNull(this[SCHEDULE_ID] as? String),
            localTimeMinutes =
                requireNotNull(
                    (this[LOCAL_TIME_MINUTES] as? Number)
                        ?.toInt()
                ),
            daysOfWeekMask =
                requireNotNull(
                    (this[DAYS_OF_WEEK_MASK] as? Number)
                        ?.toInt()
                ),
            snoozeEnabled =
                this[SNOOZE_ENABLED] as? Boolean ?: false,
            snoozeMinutes =
                requireNotNull(
                    (this[SNOOZE_MINUTES] as? Number)
                        ?.toInt()
                ),
            maxSnoozes =
                requireNotNull(
                    (this[MAX_SNOOZES] as? Number)
                        ?.toInt()
                ),
        )

    private fun Exception.toFailure():
            AdminScheduleFailure =
        when (this) {
            is FirebaseNetworkException ->
                AdminScheduleFailure.NETWORK_UNAVAILABLE

            is FirebaseFirestoreException ->
                when (code) {
                    FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                        AdminScheduleFailure.PERMISSION_DENIED

                    FirebaseFirestoreException.Code.UNAVAILABLE,
                    FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
                        ->
                        AdminScheduleFailure.NETWORK_UNAVAILABLE

                    else ->
                        AdminScheduleFailure.UNKNOWN
                }

            else ->
                AdminScheduleFailure.UNKNOWN
        }

    private fun isValidFamilyId(
        value: String,
    ): Boolean =
        value.isNotBlank() &&
                value.length <= 128 &&
                '/' !in value

    private companion object {
        const val CURRENT_SCHEMA_VERSION = 1L

        const val FAMILIES = "families"

        const val SCHEDULE_STATE = "scheduleState"
        const val CURRENT = "current"

        const val SCHEDULE_VERSIONS = "scheduleVersions"

        const val DESIRED_VERSION = "desiredVersion"
        const val UPDATED_AT = "updatedAt"
        const val UPDATED_BY_UID = "updatedByUid"

        const val FAMILY_ID = "familyId"
        const val SCHEDULE_VERSION = "scheduleVersion"
        const val SCHEMA_VERSION = "schemaVersion"
        const val PUBLISHED_AT = "publishedAt"
        const val PUBLISHED_BY_UID = "publishedByUid"

        const val MEDICATIONS = "medications"

        const val MEDICATION_ID = "medicationId"
        const val DISPLAY_NAME = "displayName"
        const val DOSAGE_TEXT = "dosageText"
        const val INSTRUCTION_TEXT = "instructionText"
        const val ACTIVE = "active"

        const val SCHEDULES = "schedules"

        const val SCHEDULE_ID = "scheduleId"
        const val LOCAL_TIME_MINUTES = "localTimeMinutes"
        const val DAYS_OF_WEEK_MASK = "daysOfWeekMask"
        const val SNOOZE_ENABLED = "snoozeEnabled"
        const val SNOOZE_MINUTES = "snoozeMinutes"
        const val MAX_SNOOZES = "maxSnoozes"
    }
}