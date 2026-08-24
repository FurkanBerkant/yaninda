package com.berkant.yaninda.family.private

import android.util.Log
import com.berkant.yaninda.auth.FamilyAuthOperationResult
import com.berkant.yaninda.auth.FamilyAuthRepository
import com.berkant.yaninda.data.device.DeviceIdentityRepository
import com.berkant.yaninda.domain.family.FamilyPairing
import com.berkant.yaninda.firebase.awaitFirebaseValue
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CancellationException

sealed interface PrivateFamilyProvisioningResult {
    data object Success : PrivateFamilyProvisioningResult

    data object AuthenticationFailed : PrivateFamilyProvisioningResult

    data object BackendUnavailable : PrivateFamilyProvisioningResult

    data object AuthorizationDenied : PrivateFamilyProvisioningResult

    data object ProvisioningFailed : PrivateFamilyProvisioningResult
}

class PrivateFamilyProvisioningService(
    private val authRepository: FamilyAuthRepository,
    private val functions: FirebaseFunctions?,
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

        val firebaseFunctions =
            functions ?: run {
                Log.e(
                    LOG_TAG,
                    "Provision failed: FirebaseFunctions is null.",
                )

                return PrivateFamilyProvisioningResult.BackendUnavailable
            }

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

    private companion object {

        const val PROVISION_FUNCTION =
            "provisionPrivateFamilyDevice"

        const val LOG_TAG =
            "YanindaPrivateFamily"
    }
}
