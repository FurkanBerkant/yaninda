package com.berkant.yaninda.family.private

import android.util.Log
import com.berkant.yaninda.auth.FamilyAuthOperationResult
import com.berkant.yaninda.auth.FamilyAuthRepository
import com.berkant.yaninda.data.device.DeviceIdentityRepository
import com.berkant.yaninda.domain.family.FamilyPairing
import com.berkant.yaninda.firebase.awaitFirebaseCompletion
import com.berkant.yaninda.firebase.awaitFirebaseValue
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CancellationException

sealed interface PrivateFamilyProvisioningResult {
    data object Success : PrivateFamilyProvisioningResult

    data object AuthenticationFailed : PrivateFamilyProvisioningResult

    data object BackendUnavailable : PrivateFamilyProvisioningResult

    data object AuthorizationDenied : PrivateFamilyProvisioningResult

    data class ApprovalRequired(
        val deviceId: String,
    ) : PrivateFamilyProvisioningResult

    data object ProvisioningFailed : PrivateFamilyProvisioningResult
}

class PrivateFamilyProvisioningService(
    private val authRepository: FamilyAuthRepository,
    private val auth: FirebaseAuth?,
    private val firestore: FirebaseFirestore?,
    private val functions: FirebaseFunctions?,
    private val usesLocalEmulators: Boolean,
    private val deviceIdentityRepository: DeviceIdentityRepository,
    private val profileRepository: PrivateDeviceProfileRepository,
    private val appVersion: String,
) {

    suspend fun provision(
        profile: PrivateDeviceProfile,
    ): PrivateFamilyProvisioningResult {

        Log.d(
            LOG_TAG,
            "Provision started. role=${profile.role}",
        )

        return try {
            Log.d(
                LOG_TAG,
                "Ensuring Firebase device session...",
            )

            val authResult =
                authRepository.ensureDeviceSession()

            Log.d(
                LOG_TAG,
                "Auth result=$authResult",
            )

            if (authResult !is FamilyAuthOperationResult.Success) {
                Log.e(
                    LOG_TAG,
                    "Provision failed at authentication stage.",
                )

                return PrivateFamilyProvisioningResult.AuthenticationFailed
            }

            val deviceId =
                deviceIdentityRepository.getOrCreateDeviceId()

            Log.d(
                LOG_TAG,
                "Authenticated device session is ready.",
            )

            if (!usesLocalEmulators) {
                return provisionWithManualApproval(
                    profile = profile,
                    deviceId = deviceId,
                )
            }

            val firebaseFunctions =
                functions ?: run {
                    Log.e(
                        LOG_TAG,
                        "Provision failed: FirebaseFunctions is null.",
                    )

                    return PrivateFamilyProvisioningResult.BackendUnavailable
                }

            Log.d(
                LOG_TAG,
                "Calling $PROVISION_FUNCTION...",
            )

            val response =
                firebaseFunctions
                    .getHttpsCallable(
                        PROVISION_FUNCTION,
                    )
                    .call(
                        mapOf(
                            "familyId" to
                                    PrivateFamilyConfig.FAMILY_ID,
                            "role" to
                                    profile.role.name,
                            "deviceId" to
                                    deviceId,
                            "displayName" to
                                    profile.displayName,
                            "appVersion" to
                                    appVersion,
                        )
                    )
                    .awaitFirebaseValue()

            Log.d(
                LOG_TAG,
                "Provision function completed.",
            )

            val data =
                response.data as? Map<*, *>

            if (data == null) {
                Log.e(
                    LOG_TAG,
                    "Provision failed: response data is invalid.",
                )

                return PrivateFamilyProvisioningResult.ProvisioningFailed
            }

            val returnedFamilyId =
                data["familyId"] as? String

            if (returnedFamilyId == null) {
                Log.e(
                    LOG_TAG,
                    "Provision failed: family identity is missing.",
                )

                return PrivateFamilyProvisioningResult.ProvisioningFailed
            }

            val returnedRole =
                data["role"] as? String

            if (returnedRole == null) {
                Log.e(
                    LOG_TAG,
                    "Provision failed: device role is missing.",
                )

                return PrivateFamilyProvisioningResult.ProvisioningFailed
            }

            val returnedDeviceId =
                data["deviceId"] as? String

            if (returnedDeviceId == null) {
                Log.e(
                    LOG_TAG,
                    "Provision failed: device identity is missing.",
                )

                return PrivateFamilyProvisioningResult.ProvisioningFailed
            }

            Log.d(
                LOG_TAG,
                "Provision response validated. role=$returnedRole",
            )

            if (
                returnedFamilyId !=
                PrivateFamilyConfig.FAMILY_ID
            ) {
                Log.e(
                    LOG_TAG,
                    "Provision failed: family identity mismatch.",
                )

                return PrivateFamilyProvisioningResult.ProvisioningFailed
            }

            if (
                returnedRole !=
                profile.role.name
            ) {
                Log.e(
                    LOG_TAG,
                    "Provision failed: device role mismatch.",
                )

                return PrivateFamilyProvisioningResult.ProvisioningFailed
            }

            if (returnedDeviceId != deviceId) {
                Log.e(
                    LOG_TAG,
                    "Provision failed: device identity mismatch.",
                )

                return PrivateFamilyProvisioningResult.ProvisioningFailed
            }

            Log.d(
                LOG_TAG,
                "Recording local family pairing...",
            )

            deviceIdentityRepository.recordPairing(
                FamilyPairing(
                    familyId = returnedFamilyId,
                    deviceRole = profile.role,
                )
            )

            profileRepository.save(profile)

            Log.d(
                LOG_TAG,
                "Provision SUCCESS.",
            )

            PrivateFamilyProvisioningResult.Success

        } catch (error: CancellationException) {
            throw error

        } catch (error: FirebaseFunctionsException) {
            Log.e(
                LOG_TAG,
                "Private family provisioning function failed. code=${error.code}",
            )

            when (error.code) {
                FirebaseFunctionsException.Code.PERMISSION_DENIED ->
                    PrivateFamilyProvisioningResult.AuthorizationDenied

                FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                    PrivateFamilyProvisioningResult.AuthenticationFailed

                FirebaseFunctionsException.Code.UNAVAILABLE,
                FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
                -> PrivateFamilyProvisioningResult.BackendUnavailable

                else -> PrivateFamilyProvisioningResult.ProvisioningFailed
            }

        } catch (error: Exception) {
            Log.e(
                LOG_TAG,
                "Private family provisioning failed. error=${error::class.java.simpleName}",
            )

            PrivateFamilyProvisioningResult.ProvisioningFailed
        }
    }

    private suspend fun provisionWithManualApproval(
        profile: PrivateDeviceProfile,
        deviceId: String,
    ): PrivateFamilyProvisioningResult {
        val firebaseAuth =
            auth ?: return PrivateFamilyProvisioningResult.BackendUnavailable
        val database =
            firestore ?: return PrivateFamilyProvisioningResult.BackendUnavailable
        val uid =
            firebaseAuth.currentUser?.uid
                ?: return PrivateFamilyProvisioningResult.AuthenticationFailed

        val requestReference =
            database
                .collection(APPROVAL_REQUESTS)
                .document(uid)

        val existingRequest =
            requestReference
                .get()
                .awaitFirebaseValue()

        val requestedAt =
            existingRequest.getTimestamp(REQUESTED_AT)
                ?: FieldValue.serverTimestamp()

        requestReference
            .set(
                mapOf(
                    UID to uid,
                    FAMILY_ID to PrivateFamilyConfig.FAMILY_ID,
                    DEVICE_ID to deviceId,
                    REQUESTED_ROLE to profile.role.name,
                    DISPLAY_NAME to profile.displayName,
                    APP_VERSION to appVersion,
                    STATUS to PENDING,
                    REQUESTED_AT to requestedAt,
                    UPDATED_AT to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            )
            .awaitFirebaseCompletion()

        val authorization =
            database
                .collection(DEVICE_AUTHORIZATIONS)
                .document(uid)
                .get()
                .awaitFirebaseValue()

        if (!authorization.exists()) {
            return PrivateFamilyProvisioningResult.ApprovalRequired(
                deviceId = deviceId,
            )
        }

        val authorizationMatches =
            authorization.getString(UID) == uid &&
                authorization.getString(FAMILY_ID) == PrivateFamilyConfig.FAMILY_ID &&
                authorization.getString(DEVICE_ID) == deviceId &&
                authorization.getString(ROLE) == profile.role.name &&
                authorization.getBoolean(ACTIVE) == true

        if (!authorizationMatches) {
            return PrivateFamilyProvisioningResult.AuthorizationDenied
        }

        val family =
            database
                .collection(FAMILIES)
                .document(PrivateFamilyConfig.FAMILY_ID)
        val device =
            family
                .collection(DEVICES)
                .document(deviceId)
        val existingDevice =
            device.get().awaitFirebaseValue()

        if (existingDevice.exists()) {
            val existingDeviceMatches =
                existingDevice.getString(OWNER_UID) == uid &&
                    existingDevice.getString(ROLE) == profile.role.name

            if (!existingDeviceMatches) {
                return PrivateFamilyProvisioningResult.AuthorizationDenied
            }

            recordLocalProvisioning(profile)
            return PrivateFamilyProvisioningResult.Success
        }

        val batch = database.batch()

        if (profile.role.name == ADMIN_DEVICE) {
            val familySnapshot =
                family.get().awaitFirebaseValue()

            if (!familySnapshot.exists()) {
                batch.set(
                    family,
                    mapOf(
                        FAMILY_ID to PrivateFamilyConfig.FAMILY_ID,
                        NAME to PrivateFamilyConfig.FAMILY_NAME,
                        CREATED_BY_UID to uid,
                        CREATED_AT to FieldValue.serverTimestamp(),
                        VERSION to 1,
                    ),
                )
            }

            batch.set(
                family.collection(MEMBERS).document(uid),
                mapOf(
                    UID to uid,
                    FAMILY_ID to PrivateFamilyConfig.FAMILY_ID,
                    ROLE to ADMIN,
                    DISPLAY_NAME to profile.displayName,
                    JOINED_AT to FieldValue.serverTimestamp(),
                    DEVICE_ID to deviceId,
                    VERSION to 1,
                ),
            )

            batch.set(
                database
                    .collection(USERS)
                    .document(uid)
                    .collection(MEMBERSHIPS)
                    .document(PrivateFamilyConfig.FAMILY_ID),
                mapOf(
                    FAMILY_ID to PrivateFamilyConfig.FAMILY_ID,
                    FAMILY_NAME to PrivateFamilyConfig.FAMILY_NAME,
                    ROLE to ADMIN,
                    DISPLAY_NAME to profile.displayName,
                    JOINED_AT to FieldValue.serverTimestamp(),
                    VERSION to 1,
                ),
            )
        }

        batch.set(
            device,
            mapOf(
                DEVICE_ID to deviceId,
                FAMILY_ID to PrivateFamilyConfig.FAMILY_ID,
                OWNER_UID to uid,
                ROLE to profile.role.name,
                DISPLAY_NAME to profile.displayName,
                APP_VERSION to appVersion,
                LAST_SEEN_AT to FieldValue.serverTimestamp(),
                LAST_SUCCESSFUL_SYNC_AT to null,
                VERSION to 1,
            ),
        )

        if (profile.role.name == ALARM_DEVICE) {
            batch.set(
                database.collection(DEVICE_ACCESS).document(uid),
                mapOf(
                    UID to uid,
                    FAMILY_ID to PrivateFamilyConfig.FAMILY_ID,
                    DEVICE_ID to deviceId,
                    ROLE to ALARM_DEVICE,
                    UPDATED_AT to FieldValue.serverTimestamp(),
                ),
            )
        }

        batch.commit().awaitFirebaseCompletion()
        recordLocalProvisioning(profile)

        return PrivateFamilyProvisioningResult.Success
    }

    private suspend fun recordLocalProvisioning(
        profile: PrivateDeviceProfile,
    ) {
        deviceIdentityRepository.recordPairing(
            FamilyPairing(
                familyId = PrivateFamilyConfig.FAMILY_ID,
                deviceRole = profile.role,
            )
        )
        profileRepository.save(profile)
    }

    private companion object {

        const val PROVISION_FUNCTION =
            "provisionPrivateFamilyDevice"

        const val LOG_TAG =
            "YanindaPrivateFamily"

        const val APPROVAL_REQUESTS = "deviceApprovalRequests"
        const val DEVICE_AUTHORIZATIONS = "deviceAuthorizations"
        const val DEVICE_ACCESS = "deviceAccess"
        const val FAMILIES = "families"
        const val MEMBERS = "members"
        const val DEVICES = "devices"
        const val USERS = "users"
        const val MEMBERSHIPS = "memberships"
        const val UID = "uid"
        const val FAMILY_ID = "familyId"
        const val FAMILY_NAME = "familyName"
        const val DEVICE_ID = "deviceId"
        const val REQUESTED_ROLE = "requestedRole"
        const val ROLE = "role"
        const val DISPLAY_NAME = "displayName"
        const val APP_VERSION = "appVersion"
        const val STATUS = "status"
        const val PENDING = "PENDING"
        const val ACTIVE = "active"
        const val REQUESTED_AT = "requestedAt"
        const val UPDATED_AT = "updatedAt"
        const val NAME = "name"
        const val CREATED_BY_UID = "createdByUid"
        const val CREATED_AT = "createdAt"
        const val JOINED_AT = "joinedAt"
        const val OWNER_UID = "ownerUid"
        const val LAST_SEEN_AT = "lastSeenAt"
        const val LAST_SUCCESSFUL_SYNC_AT = "lastSuccessfulSyncAt"
        const val VERSION = "version"
        const val ADMIN = "ADMIN"
        const val ADMIN_DEVICE = "ADMIN_DEVICE"
        const val ALARM_DEVICE = "ALARM_DEVICE"
    }
}
