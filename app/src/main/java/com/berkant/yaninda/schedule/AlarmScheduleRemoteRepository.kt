package com.berkant.yaninda.schedule

import com.berkant.yaninda.firebase.awaitFirebaseValue
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import com.google.firebase.firestore.Source
interface AlarmScheduleRemoteRepository {

    suspend fun ensureScheduleAccess(
        familyId: String,
        deviceId: String,
    )

    fun observeDesiredSchedule(
        familyId: String,
    ): Flow<PublishedScheduleVersion>

    suspend fun fetchDesiredSchedule(
        familyId: String,
    ): PublishedScheduleVersion
    suspend fun markScheduleApplied(
        familyId: String,
        deviceId: String,
        scheduleVersion: Long,
    )
}

class FirestoreAlarmScheduleRemoteRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val appVersion: String,
) : AlarmScheduleRemoteRepository {

    override suspend fun ensureScheduleAccess(
        familyId: String,
        deviceId: String,
    ) {
        val user =
            requireNotNull(auth.currentUser) {
                "Alarm device authentication is unavailable."
            }

        val deviceReference =
            firestore
                .collection(FAMILIES)
                .document(familyId)
                .collection(DEVICES)
                .document(deviceId)

        val accessReference =
            firestore
                .collection(DEVICE_ACCESS)
                .document(user.uid)

        firestore.runTransaction { transaction ->

            val device =
                transaction.get(deviceReference)

            check(device.exists()) {
                "Alarm device registration does not exist."
            }

            check(
                device.getString(OWNER_UID) ==
                        user.uid
            ) {
                "Alarm device owner does not match."
            }

            check(
                device.getString(ROLE) ==
                        ALARM_DEVICE
            ) {
                "Registered device is not an alarm device."
            }

            transaction.set(
                accessReference,
                mapOf(
                    UID to user.uid,
                    FAMILY_ID to familyId,
                    DEVICE_ID to deviceId,
                    ROLE to ALARM_DEVICE,
                    UPDATED_AT to
                            FieldValue.serverTimestamp(),
                ),
            )

            Unit
        }.awaitFirebaseValue()
    }

    override suspend fun fetchDesiredSchedule(
        familyId: String,
    ): PublishedScheduleVersion {

        val stateSnapshot =
            firestore
                .collection(FAMILIES)
                .document(familyId)
                .collection(SCHEDULE_STATE)
                .document(CURRENT)
                .get(Source.SERVER)
                .awaitFirebaseValue()

        val desiredVersion =
            stateSnapshot.getLong(
                DESIRED_VERSION
            ) ?: 0L

        if (desiredVersion <= 0L) {
            return PublishedScheduleVersion.empty(
                familyId
            )
        }

        val versionSnapshot =
            firestore
                .collection(FAMILIES)
                .document(familyId)
                .collection(SCHEDULE_VERSIONS)
                .document(
                    desiredVersion.toString()
                )
                .get(Source.SERVER)
                .awaitFirebaseValue()

        check(versionSnapshot.exists()) {
            "Desired schedule version does not exist."
        }

        return versionSnapshot
            .toPublishedSchedule()
    }

    override fun observeDesiredSchedule(
        familyId: String,
    ): Flow<PublishedScheduleVersion> =
        callbackFlow {

            val stateReference =
                firestore
                    .collection(FAMILIES)
                    .document(familyId)
                    .collection(SCHEDULE_STATE)
                    .document(CURRENT)

            var versionRegistration:
                    ListenerRegistration? = null

            var observedVersion: Long? = null

            val stateRegistration =
                stateReference
                    .addSnapshotListener {
                            snapshot,
                            error,
                        ->

                        if (error != null) {
                            close(error)
                            return@addSnapshotListener
                        }

                        val desiredVersion =
                            snapshot
                                ?.getLong(
                                    DESIRED_VERSION
                                )
                                ?: 0L

                        if (desiredVersion <= 0L) {
                            observedVersion = 0L

                            versionRegistration
                                ?.remove()

                            versionRegistration = null

                            trySend(
                                PublishedScheduleVersion
                                    .empty(familyId)
                            )

                            return@addSnapshotListener
                        }

                        if (
                            observedVersion ==
                            desiredVersion
                        ) {
                            return@addSnapshotListener
                        }

                        observedVersion =
                            desiredVersion

                        versionRegistration
                            ?.remove()

                        val versionReference =
                            firestore
                                .collection(FAMILIES)
                                .document(familyId)
                                .collection(
                                    SCHEDULE_VERSIONS
                                )
                                .document(
                                    desiredVersion
                                        .toString()
                                )

                        versionRegistration =
                            versionReference
                                .addSnapshotListener {
                                        versionSnapshot,
                                        versionError,
                                    ->

                                    if (
                                        versionError != null
                                    ) {
                                        close(
                                            versionError
                                        )
                                        return@addSnapshotListener
                                    }

                                    if (
                                        versionSnapshot ==
                                        null ||
                                        !versionSnapshot
                                            .exists()
                                    ) {
                                        return@addSnapshotListener
                                    }

                                    trySend(
                                        versionSnapshot
                                            .toPublishedSchedule()
                                    )
                                }
                    }

            awaitClose {
                stateRegistration.remove()
                versionRegistration?.remove()
            }
        }

    override suspend fun markScheduleApplied(
        familyId: String,
        deviceId: String,
        scheduleVersion: Long,
    ) {
        val user =
            requireNotNull(auth.currentUser)

        val deviceReference =
            firestore
                .collection(FAMILIES)
                .document(familyId)
                .collection(DEVICES)
                .document(deviceId)

        firestore.runTransaction { transaction ->

            val device =
                transaction.get(deviceReference)

            check(device.exists())

            check(
                device.getString(OWNER_UID) ==
                        user.uid
            )

            check(
                device.getString(ROLE) ==
                        ALARM_DEVICE
            )

            val currentDeviceVersion =
                device.getLong(VERSION) ?: 1L

            transaction.update(
                deviceReference,
                mapOf(
                    APP_VERSION to appVersion,
                    LAST_SEEN_AT to
                            FieldValue.serverTimestamp(),
                    LAST_SUCCESSFUL_SYNC_AT to
                            FieldValue.serverTimestamp(),
                    APPLIED_SCHEDULE_VERSION to
                            scheduleVersion,
                    VERSION to
                            currentDeviceVersion + 1L,
                ),
            )

            Unit
        }.awaitFirebaseValue()
    }

    private fun DocumentSnapshot
            .toPublishedSchedule():
            PublishedScheduleVersion {

        val medications =
            (get(MEDICATIONS) as? List<*>)
                .orEmpty()
                .map { value ->

                    val map =
                        requireNotNull(
                            value as? Map<*, *>
                        )

                    map.toPublishedMedication()
                }

        return PublishedScheduleVersion(
            familyId =
                requireNotNull(
                    getString(FAMILY_ID)
                ),
            version =
                requireNotNull(
                    getLong(SCHEDULE_VERSION)
                ),
            medications = medications,
            publishedAt =
                getTimestamp(
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

    private fun Map<*, *>
            .toPublishedMedication():
            PublishedMedication {

        val schedules =
            (this[SCHEDULES] as? List<*>)
                .orEmpty()
                .map { value ->
                    requireNotNull(
                        value as? Map<*, *>
                    ).toPublishedScheduleItem()
                }

        return PublishedMedication(
            medicationId =
                requireNotNull(
                    this[MEDICATION_ID]
                            as? String
                ),
            displayName =
                requireNotNull(
                    this[DISPLAY_NAME]
                            as? String
                ),
            dosageText =
                requireNotNull(
                    this[DOSAGE_TEXT]
                            as? String
                ),
            instructionText =
                requireNotNull(
                    this[INSTRUCTION_TEXT]
                            as? String
                ),
            active =
                this[ACTIVE] as? Boolean
                    ?: true,
            schedules = schedules,
        )
    }

    private fun Map<*, *>
            .toPublishedScheduleItem():
            PublishedMedicationSchedule =
        PublishedMedicationSchedule(
            scheduleId =
                requireNotNull(
                    this[SCHEDULE_ID]
                            as? String
                ),
            localTimeMinutes =
                requireNotNull(
                    (
                            this[
                                LOCAL_TIME_MINUTES
                            ] as? Number
                            )?.toInt()
                ),
            daysOfWeekMask =
                requireNotNull(
                    (
                            this[
                                DAYS_OF_WEEK_MASK
                            ] as? Number
                            )?.toInt()
                ),
            snoozeEnabled =
                this[SNOOZE_ENABLED]
                        as? Boolean
                    ?: false,
            snoozeMinutes =
                requireNotNull(
                    (
                            this[
                                SNOOZE_MINUTES
                            ] as? Number
                            )?.toInt()
                ),
            maxSnoozes =
                requireNotNull(
                    (
                            this[
                                MAX_SNOOZES
                            ] as? Number
                            )?.toInt()
                ),
        )

    private companion object {

        const val FAMILIES = "families"
        const val DEVICES = "devices"

        const val DEVICE_ACCESS =
            "deviceAccess"

        const val SCHEDULE_STATE =
            "scheduleState"

        const val SCHEDULE_VERSIONS =
            "scheduleVersions"

        const val CURRENT = "current"

        const val UID = "uid"
        const val FAMILY_ID = "familyId"
        const val DEVICE_ID = "deviceId"
        const val ROLE = "role"

        const val ALARM_DEVICE =
            "ALARM_DEVICE"

        const val OWNER_UID = "ownerUid"

        const val UPDATED_AT = "updatedAt"

        const val DESIRED_VERSION =
            "desiredVersion"

        const val SCHEDULE_VERSION =
            "scheduleVersion"

        const val PUBLISHED_AT =
            "publishedAt"

        const val PUBLISHED_BY_UID =
            "publishedByUid"

        const val MEDICATIONS = "medications"

        const val MEDICATION_ID =
            "medicationId"

        const val DISPLAY_NAME =
            "displayName"

        const val DOSAGE_TEXT =
            "dosageText"

        const val INSTRUCTION_TEXT =
            "instructionText"

        const val ACTIVE = "active"

        const val SCHEDULES = "schedules"

        const val SCHEDULE_ID =
            "scheduleId"

        const val LOCAL_TIME_MINUTES =
            "localTimeMinutes"

        const val DAYS_OF_WEEK_MASK =
            "daysOfWeekMask"

        const val SNOOZE_ENABLED =
            "snoozeEnabled"

        const val SNOOZE_MINUTES =
            "snoozeMinutes"

        const val MAX_SNOOZES =
            "maxSnoozes"

        const val APP_VERSION = "appVersion"

        const val LAST_SEEN_AT =
            "lastSeenAt"

        const val LAST_SUCCESSFUL_SYNC_AT =
            "lastSuccessfulSyncAt"

        const val APPLIED_SCHEDULE_VERSION =
            "appliedScheduleVersion"

        const val VERSION = "version"
    }
}