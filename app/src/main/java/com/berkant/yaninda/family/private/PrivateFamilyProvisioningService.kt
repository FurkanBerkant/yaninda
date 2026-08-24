package com.berkant.yaninda.family.private

import android.util.Log
import com.berkant.yaninda.auth.FamilyAuthOperationResult
import com.berkant.yaninda.auth.FamilyAuthRepository
import com.berkant.yaninda.data.device.DeviceIdentityRepository
import com.berkant.yaninda.domain.family.FamilyPairing
import com.berkant.yaninda.firebase.awaitFirebaseValue
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.CancellationException

sealed interface PrivateFamilyProvisioningResult {
    data object Success : PrivateFamilyProvisioningResult

    data object AuthenticationFailed : PrivateFamilyProvisioningResult

    data object BackendUnavailable : PrivateFamilyProvisioningResult

    data object ProvisioningFailed : PrivateFamilyProvisioningResult
}

class PrivateFamilyProvisioningService(
    private val authRepository: FamilyAuthRepository,
    private val functions: FirebaseFunctions?,
    private val deviceIdentityRepository: DeviceIdentityRepository,
    private val appVersion: String,
) {

    suspend fun provision(
        profile: PrivateDeviceProfile,
    ): PrivateFamilyProvisioningResult {

        Log.d(
            LOG_TAG,
            "Provision started. profile=${profile.displayName}, role=${profile.role}",
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
                authRepository.ensureAlarmDeviceSession()

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
                "Auth ready. deviceId=$deviceId",
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
                "Function completed. rawData=${response.data}",
            )

            val data =
                response.data as? Map<*, *>

            if (data == null) {
                Log.e(
                    LOG_TAG,
                    "Provision failed: response.data is not a Map. value=${response.data}",
                )

                return PrivateFamilyProvisioningResult.ProvisioningFailed
            }

            val returnedFamilyId =
                data["familyId"] as? String

            if (returnedFamilyId == null) {
                Log.e(
                    LOG_TAG,
                    "Provision failed: familyId missing. data=$data",
                )

                return PrivateFamilyProvisioningResult.ProvisioningFailed
            }

            val returnedRole =
                data["role"] as? String

            if (returnedRole == null) {
                Log.e(
                    LOG_TAG,
                    "Provision failed: role missing. data=$data",
                )

                return PrivateFamilyProvisioningResult.ProvisioningFailed
            }

            Log.d(
                LOG_TAG,
                "Function response familyId=$returnedFamilyId role=$returnedRole",
            )

            if (
                returnedFamilyId !=
                PrivateFamilyConfig.FAMILY_ID
            ) {
                Log.e(
                    LOG_TAG,
                    "Provision failed: familyId mismatch. expected=${PrivateFamilyConfig.FAMILY_ID}, actual=$returnedFamilyId",
                )

                return PrivateFamilyProvisioningResult.ProvisioningFailed
            }

            if (
                returnedRole !=
                profile.role.name
            ) {
                Log.e(
                    LOG_TAG,
                    "Provision failed: role mismatch. expected=${profile.role.name}, actual=$returnedRole",
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

            Log.d(
                LOG_TAG,
                "Provision SUCCESS.",
            )

            PrivateFamilyProvisioningResult.Success

        } catch (error: CancellationException) {
            throw error

        } catch (error: Exception) {
            Log.e(
                LOG_TAG,
                "Private family provisioning failed with exception.",
                error,
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