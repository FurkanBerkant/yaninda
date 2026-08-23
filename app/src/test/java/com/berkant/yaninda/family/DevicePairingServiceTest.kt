package com.berkant.yaninda.family

import com.berkant.yaninda.auth.FamilyAuthOperationResult
import com.berkant.yaninda.auth.FamilyAuthRepository
import com.berkant.yaninda.auth.FamilyAuthState
import com.berkant.yaninda.data.device.DeviceIdentityRepository
import com.berkant.yaninda.domain.family.DeviceRegistration
import com.berkant.yaninda.domain.family.DeviceRole
import com.berkant.yaninda.domain.family.FamilyMember
import com.berkant.yaninda.domain.family.FamilyContact
import com.berkant.yaninda.domain.family.FamilyDoseOccurrence
import com.berkant.yaninda.domain.family.FamilyMemberRole
import com.berkant.yaninda.domain.family.FamilyMembership
import com.berkant.yaninda.domain.family.FamilyPairing
import com.berkant.yaninda.domain.family.PairingInvitation
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicePairingServiceTest {
    private val membership = FamilyMembership(
        familyId = "family-1",
        familyName = "Aile",
        role = FamilyMemberRole.ADMIN,
        displayName = "Bakıcı",
        joinedAt = Instant.parse("2026-08-21T12:00:00Z"),
    )

    @Test
    fun pairPrimary_establishesAnonymousSessionBeforeClaimAndStoresPairing() = runBlocking {
        val actions = mutableListOf<String>()
        val auth = FakeAuthRepository(actions)
        val family = FakeFamilyRepository(actions).apply {
            claimResult = FamilyRepositoryResult.Success(
                FamilyPairing("family-1", DeviceRole.ALARM_DEVICE)
            )
        }
        val identity = FakeDeviceIdentityRepository(actions)
        val service = DevicePairingService(auth, family, identity, "1.0")

        val result = service.pairPrimaryDevice("2345-6789-ABCD-EFGH", "Dede telefonu")

        assertTrue(result is DevicePairingResult.Success)
        assertEquals(
            listOf("auth-primary", "device-id", "claim-primary", "record-primary"),
            actions,
        )
        assertEquals(DeviceRole.ALARM_DEVICE, identity.pairing.value?.deviceRole)
    }

    @Test
    fun createFamily_storesCaregiverPairingAfterCloudSuccess() = runBlocking {
        val actions = mutableListOf<String>()
        val family = FakeFamilyRepository(actions).apply {
            createResult = FamilyRepositoryResult.Success(membership)
        }
        val identity = FakeDeviceIdentityRepository(actions)
        val service = DevicePairingService(
            FakeAuthRepository(actions),
            family,
            identity,
            "1.0",
        )

        val result = service.createFamily("Aile", "Bakıcı")

        assertEquals(DevicePairingResult.Success(membership), result)
        assertEquals(DeviceRole.ADMIN_DEVICE, identity.pairing.value?.deviceRole)
    }

    @Test
    fun invitationFailure_isMappedWithoutRecordingLocalPairing() = runBlocking {
        val actions = mutableListOf<String>()
        val family = FakeFamilyRepository(actions).apply {
            claimResult = FamilyRepositoryResult.Failure(
                FamilyRepositoryFailure.INVITATION_EXPIRED
            )
        }
        val identity = FakeDeviceIdentityRepository(actions)
        val service = DevicePairingService(
            FakeAuthRepository(actions),
            family,
            identity,
            "1.0",
        )

        val result = service.pairCaregiverDevice("2345-6789-ABCD-EFGH", "Aile telefonu")

        assertEquals(
            DevicePairingResult.Failure(DevicePairingFailure.INVITATION_EXPIRED),
            result,
        )
        assertEquals(null, identity.pairing.value)
    }
}

private class FakeAuthRepository(
    private val actions: MutableList<String>,
) : FamilyAuthRepository {
    override val state: Flow<FamilyAuthState> = flowOf(
        FamilyAuthState.SignedIn("user-1", null, true, false)
    )

    override suspend fun createCaregiverAccount(
        email: String,
        password: String,
    ) = FamilyAuthOperationResult.Success

    override suspend fun signInCaregiver(
        email: String,
        password: String,
    ) = FamilyAuthOperationResult.Success

    override suspend fun ensurePrimaryDeviceSession(): FamilyAuthOperationResult {
        actions += "auth-primary"
        return FamilyAuthOperationResult.Success
    }

    override suspend fun sendPasswordReset(email: String) = FamilyAuthOperationResult.Success

    override fun signOut() = Unit
}

private class FakeDeviceIdentityRepository(
    private val actions: MutableList<String>,
) : DeviceIdentityRepository {
    override val selectedRole = MutableStateFlow<DeviceRole?>(null)
    override val pairing = MutableStateFlow<FamilyPairing?>(null)

    override suspend fun selectRole(role: DeviceRole) {
        selectedRole.value = role
    }

    override suspend fun getOrCreateDeviceId(): String {
        actions += "device-id"
        return "device-1"
    }

    override suspend fun recordPairing(pairing: FamilyPairing) {
            actions += if (pairing.deviceRole == DeviceRole.ALARM_DEVICE) {
            "record-primary"
        } else {
            "record-caregiver"
        }
        this.pairing.value = pairing
        selectedRole.value = pairing.deviceRole
    }

    override suspend fun clearPairing() {
        pairing.value = null
    }
}

private class FakeFamilyRepository(
    private val actions: MutableList<String>,
) : FamilyRepository {
    var createResult: FamilyRepositoryResult<FamilyMembership> =
        FamilyRepositoryResult.Failure(FamilyRepositoryFailure.UNKNOWN)
    var claimResult: FamilyRepositoryResult<FamilyPairing> =
        FamilyRepositoryResult.Failure(FamilyRepositoryFailure.UNKNOWN)

    override fun observeMemberships(): Flow<List<FamilyMembership>> = flowOf(emptyList())
    override fun observeMembers(familyId: String): Flow<List<FamilyMember>> = flowOf(emptyList())
    override fun observeDevices(
        familyId: String,
    ): Flow<List<DeviceRegistration>> = flowOf(emptyList())
    override fun observeOccurrences(
        familyId: String,
    ): Flow<List<FamilyDoseOccurrence>> = flowOf(emptyList())

    override fun observeContacts(
        familyId: String,
    ): Flow<List<FamilyContact>> = flowOf(emptyList())

    override suspend fun saveContact(
        familyId: String,
        contact: FamilyContact,
    ): FamilyRepositoryResult<Unit> =
        FamilyRepositoryResult.Failure(FamilyRepositoryFailure.UNKNOWN)

    override suspend fun deleteContact(
        familyId: String,
        contactId: String,
    ): FamilyRepositoryResult<Unit> =
        FamilyRepositoryResult.Failure(FamilyRepositoryFailure.UNKNOWN)

    override suspend fun createFamily(
        familyName: String,
        caregiverDisplayName: String,
        deviceId: String,
        appVersion: String,
    ): FamilyRepositoryResult<FamilyMembership> {
        actions += "create-family"
        return createResult
    }

    override suspend fun createPairingInvitation(
        familyId: String,
        targetRole: DeviceRole,
    ): FamilyRepositoryResult<PairingInvitation> =
        FamilyRepositoryResult.Failure(FamilyRepositoryFailure.UNKNOWN)

    override suspend fun claimPairingInvitation(
        code: String,
        expectedRole: DeviceRole,
        deviceId: String,
        deviceDisplayName: String,
        appVersion: String,
    ): FamilyRepositoryResult<FamilyPairing> {
            actions += if (expectedRole == DeviceRole.ALARM_DEVICE) {
            "claim-primary"
        } else {
            "claim-caregiver"
        }
        return claimResult
    }
}
