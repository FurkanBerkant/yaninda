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
        val firebaseFunctions =
            functions
                ?: return PrivateFamilyProvisioningResult.BackendUnavailable

        return try {
            /*
             * Kullanıcıya hesap ekranı göstermiyoruz.
             * Her kurulumun yine de kendine ait Firebase UID'si olur.
             */
            val authResult =
                authRepository.ensureAlarmDeviceSession()

            if (authResult !is FamilyAuthOperationResult.Success) return PrivateFamilyProvisioningResult.AuthenticationFailed

            val deviceId =
                deviceIdentityRepository.getOrCreateDeviceId()

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

            val data =
                (response.data as? Map<*, *>)
                    ?: return PrivateFamilyProvisioningResult.ProvisioningFailed

            val returnedFamilyId =
                data["familyId"] as? String
                    ?: return PrivateFamilyProvisioningResult.ProvisioningFailed

            val returnedRole =
                data["role"] as? String
                    ?: return PrivateFamilyProvisioningResult.ProvisioningFailed

            if (
                returnedFamilyId !=
                    PrivateFamilyConfig.FAMILY_ID ||
                returnedRole != profile.role.name
            ) {
                return PrivateFamilyProvisioningResult.ProvisioningFailed
            }

            /*
             * Cloud provisioning tamamlandıktan sonra local pairing yazılır.
             * Böylece MainActivity yarım kurulmuş role geçmez.
             */
            deviceIdentityRepository.recordPairing(
                FamilyPairing(
                    familyId = returnedFamilyId,
                    deviceRole = profile.role,
                )
            )

            PrivateFamilyProvisioningResult.Success

        } catch (error: CancellationException) {
            throw error

        } catch (error: Exception) {
            Log.e(
                LOG_TAG,
                "Private family provisioning failed.",
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
