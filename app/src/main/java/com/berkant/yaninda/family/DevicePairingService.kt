package com.berkant.yaninda.family

import com.berkant.yaninda.auth.FamilyAuthOperationResult
import com.berkant.yaninda.auth.FamilyAuthRepository
import com.berkant.yaninda.data.device.DeviceIdentityRepository
import com.berkant.yaninda.domain.family.DeviceRole
import com.berkant.yaninda.domain.family.FamilyMembership
import com.berkant.yaninda.domain.family.PairingInvitation

sealed interface DevicePairingResult<out T> {
    data class Success<T>(val value: T) : DevicePairingResult<T>

    data class Failure(val reason: DevicePairingFailure) : DevicePairingResult<Nothing>
}

enum class DevicePairingFailure {
    AUTHENTICATION_REQUIRED,
    INVALID_INPUT,
    INVITATION_INVALID,
    INVITATION_EXPIRED,
    INVITATION_ALREADY_USED,
    WRONG_DEVICE_ROLE,
    NETWORK_UNAVAILABLE,
    PERMISSION_DENIED,
    NOT_CONFIGURED,
    UNKNOWN,
}

class DevicePairingService(
    private val authRepository: FamilyAuthRepository,
    private val familyRepository: FamilyRepository,
    private val deviceIdentityRepository: DeviceIdentityRepository,
    private val appVersion: String,
) {
    suspend fun createFamily(
        familyName: String,
        caregiverDisplayName: String,
    ): DevicePairingResult<FamilyMembership> {
        val deviceId = deviceIdentityRepository.getOrCreateDeviceId()
        return when (
            val result = familyRepository.createFamily(
                familyName = familyName,
                caregiverDisplayName = caregiverDisplayName,
                deviceId = deviceId,
                appVersion = appVersion,
            )
        ) {
            is FamilyRepositoryResult.Success -> {
                deviceIdentityRepository.recordPairing(
                    com.berkant.yaninda.domain.family.FamilyPairing(
                        familyId = result.value.familyId,
                        deviceRole = DeviceRole.ADMIN_DEVICE,
                    )
                )
                DevicePairingResult.Success(result.value)
            }

            is FamilyRepositoryResult.Failure -> result.toDevicePairingFailure()
        }
    }

    suspend fun pairAlarmDevice(
        code: String,
        deviceDisplayName: String,
    ): DevicePairingResult<Unit> {
        if (authRepository.ensureAlarmDeviceSession() !is FamilyAuthOperationResult.Success) {
            return DevicePairingResult.Failure(DevicePairingFailure.AUTHENTICATION_REQUIRED)
        }
        return claim(
            code = code,
            role = DeviceRole.ALARM_DEVICE,
            deviceDisplayName = deviceDisplayName,
        )
    }

    suspend fun pairCaregiverDevice(
        code: String,
        deviceDisplayName: String,
    ): DevicePairingResult<Unit> = claim(
        code = code,
        role = DeviceRole.ADMIN_DEVICE,
        deviceDisplayName = deviceDisplayName,
    )

    suspend fun createInvitation(
        familyId: String,
        targetRole: DeviceRole,
    ): DevicePairingResult<PairingInvitation> = when (
        val result = familyRepository.createPairingInvitation(familyId, targetRole)
    ) {
        is FamilyRepositoryResult.Success -> DevicePairingResult.Success(result.value)
        is FamilyRepositoryResult.Failure -> result.toDevicePairingFailure()
    }

    private suspend fun claim(
        code: String,
        role: DeviceRole,
        deviceDisplayName: String,
    ): DevicePairingResult<Unit> {
        val deviceId = deviceIdentityRepository.getOrCreateDeviceId()
        return when (
            val result = familyRepository.claimPairingInvitation(
                code = code,
                expectedRole = role,
                deviceId = deviceId,
                deviceDisplayName = deviceDisplayName,
                appVersion = appVersion,
            )
        ) {
            is FamilyRepositoryResult.Success -> {
                deviceIdentityRepository.recordPairing(result.value)
                DevicePairingResult.Success(Unit)
            }

            is FamilyRepositoryResult.Failure -> result.toDevicePairingFailure()
        }
    }

    private fun FamilyRepositoryResult.Failure.toDevicePairingFailure() =
        DevicePairingResult.Failure(
            when (reason) {
                FamilyRepositoryFailure.NOT_AUTHENTICATED ->
                    DevicePairingFailure.AUTHENTICATION_REQUIRED

                FamilyRepositoryFailure.PERMISSION_DENIED ->
                    DevicePairingFailure.PERMISSION_DENIED

                FamilyRepositoryFailure.NETWORK_UNAVAILABLE ->
                    DevicePairingFailure.NETWORK_UNAVAILABLE

                FamilyRepositoryFailure.INVALID_INPUT -> DevicePairingFailure.INVALID_INPUT
                FamilyRepositoryFailure.INVITATION_INVALID ->
                    DevicePairingFailure.INVITATION_INVALID

                FamilyRepositoryFailure.INVITATION_EXPIRED ->
                    DevicePairingFailure.INVITATION_EXPIRED

                FamilyRepositoryFailure.INVITATION_ALREADY_USED ->
                    DevicePairingFailure.INVITATION_ALREADY_USED

                FamilyRepositoryFailure.ROLE_MISMATCH ->
                    DevicePairingFailure.WRONG_DEVICE_ROLE

                FamilyRepositoryFailure.NOT_CONFIGURED -> DevicePairingFailure.NOT_CONFIGURED
                FamilyRepositoryFailure.UNKNOWN -> DevicePairingFailure.UNKNOWN
            }
        )
}
