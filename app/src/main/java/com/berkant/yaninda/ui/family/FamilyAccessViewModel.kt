package com.berkant.yaninda.ui.family

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.berkant.yaninda.auth.FamilyAuthFailure
import com.berkant.yaninda.auth.FamilyAuthOperationResult
import com.berkant.yaninda.auth.FamilyAuthRepository
import com.berkant.yaninda.auth.FamilyAuthState
import com.berkant.yaninda.domain.family.DeviceRole
import com.berkant.yaninda.domain.family.DeviceRegistration
import com.berkant.yaninda.domain.family.FamilyConnectionStatus
import com.berkant.yaninda.domain.family.FamilyConnectionFreshness
import com.berkant.yaninda.domain.family.FamilyDoseOccurrence
import com.berkant.yaninda.domain.family.FamilyMembership
import com.berkant.yaninda.domain.family.FamilyMonitoringPolicy
import com.berkant.yaninda.domain.family.PairingInvitation
import com.berkant.yaninda.family.DevicePairingFailure
import com.berkant.yaninda.family.DevicePairingResult
import com.berkant.yaninda.family.DevicePairingService
import com.berkant.yaninda.family.FamilyRepository
import com.berkant.yaninda.push.FamilyPushRegistrationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FamilyAccessMessage {
    INVALID_INPUT,
    WEAK_PASSWORD,
    INVALID_CREDENTIALS,
    ACCOUNT_UNAVAILABLE,
    NETWORK_UNAVAILABLE,
    PERMISSION_DENIED,
    INVITATION_INVALID,
    INVITATION_EXPIRED,
    INVITATION_ALREADY_USED,
    WRONG_DEVICE_ROLE,
    FIREBASE_NOT_CONFIGURED,
    PASSWORD_RESET_SENT,
    UNKNOWN_FAILURE,
}

data class FamilyAccessUiState(
    val authState: FamilyAuthState = FamilyAuthState.SignedOut,
    val memberships: List<FamilyMembership> = emptyList(),
    val devices: List<DeviceRegistration> = emptyList(),
    val occurrences: List<FamilyDoseOccurrence> = emptyList(),
    val connectionStatus: FamilyConnectionStatus = FamilyConnectionStatus(
        FamilyConnectionFreshness.ALARM_DEVICE_NOT_PAIRED,
        null,
    ),
    val isWorking: Boolean = false,
    val invitation: PairingInvitation? = null,
    val message: FamilyAccessMessage? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class FamilyAccessViewModel(
    private val authRepository: FamilyAuthRepository,
    private val familyRepository: FamilyRepository,
    private val pairingService: DevicePairingService,
    private val pushRegistrationRepository: FamilyPushRegistrationRepository,
    private val monitoringPolicy: FamilyMonitoringPolicy = FamilyMonitoringPolicy(),
    private val now: () -> Instant = Instant::now,
) : ViewModel() {
    private val mutableState = MutableStateFlow(FamilyAccessUiState())
    val state: StateFlow<FamilyAccessUiState> = mutableState.asStateFlow()
    private val membershipsFlow = authRepository.state
        .flatMapLatest(::membershipFlow)
        .catch {
            mutableState.update { current ->
                current.copy(message = FamilyAccessMessage.NETWORK_UNAVAILABLE)
            }
            emit(emptyList())
        }

    init {
        viewModelScope.launch {
            authRepository.state.collect { authState ->
                mutableState.update { current ->
                    current.copy(
                        authState = authState,
                        memberships = if (authState is FamilyAuthState.SignedIn) {
                            current.memberships
                        } else {
                            emptyList()
                        },
                        invitation = if (authState is FamilyAuthState.SignedIn) {
                            current.invitation
                        } else {
                            null
                        },
                    )
                }
            }
        }
        viewModelScope.launch {
            membershipsFlow.collect { memberships ->
                    mutableState.update { current ->
                        current.copy(
                            memberships = memberships,
                            devices = if (memberships.isEmpty()) emptyList() else current.devices,
                            occurrences = if (memberships.isEmpty()) {
                                emptyList()
                            } else {
                                current.occurrences
                            },
                        )
                    }
                if (memberships.isNotEmpty()) {
                    try {
                        pushRegistrationRepository.requestRegistration()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        // Registration is retried by FCM and future membership updates.
                    }
                }
            }
        }
        viewModelScope.launch {
            membershipsFlow
                .map { memberships -> memberships.firstOrNull()?.familyId }
                .distinctUntilChanged()
                .flatMapLatest { familyId ->
                    familyId?.let(familyRepository::observeDevices) ?: flowOf(emptyList())
                }
                .catch {
                    mutableState.update { current ->
                        current.copy(message = FamilyAccessMessage.NETWORK_UNAVAILABLE)
                    }
                    emit(emptyList())
                }
                .collect { devices ->
                    mutableState.update { current ->
                        current.copy(
                            devices = devices,
                            connectionStatus = monitoringPolicy.evaluate(
                                device = devices.firstOrNull {
                                    it.role == DeviceRole.ALARM_DEVICE
                                },
                                now = now(),
                            ),
                        )
                    }
                }
        }
        viewModelScope.launch {
            membershipsFlow
                .map { memberships -> memberships.firstOrNull()?.familyId }
                .distinctUntilChanged()
                .flatMapLatest { familyId ->
                    familyId?.let { id ->
                        familyRepository.observeOccurrences(id).map { id to it }
                    } ?: flowOf(null to emptyList())
                }
                .catch {
                    mutableState.update { current ->
                        current.copy(message = FamilyAccessMessage.NETWORK_UNAVAILABLE)
                    }
                    emit(null to emptyList())
                }
                .collect { (familyId, occurrences) ->
                    mutableState.update { current -> current.copy(occurrences = occurrences) }
                }
        }
        viewModelScope.launch {
            while (true) {
                delay(CONNECTION_REFRESH_INTERVAL_MILLIS)
                mutableState.update { current ->
                    current.copy(
                        connectionStatus = monitoringPolicy.evaluate(
                            device = current.devices.firstOrNull {
                                it.role == DeviceRole.ALARM_DEVICE
                            },
                            now = now(),
                        )
                    )
                }
            }
        }
    }

    fun createAccount(email: String, password: String) = runAuthOperation {
        authRepository.createCaregiverAccount(email, password)
    }

    fun signIn(email: String, password: String) = runAuthOperation {
        authRepository.signInCaregiver(email, password)
    }

    fun sendPasswordReset(email: String) = runAuthOperation(
        successMessage = FamilyAccessMessage.PASSWORD_RESET_SENT,
    ) {
        authRepository.sendPasswordReset(email)
    }

    fun createFamily(familyName: String, displayName: String) = runPairingOperation {
        pairingService.createFamily(familyName, displayName)
    }

    fun joinFamily(
        invitationCode: String,
        deviceDisplayName: String,
    ) = runPairingOperation {
        pairingService.pairCaregiverDevice(invitationCode, deviceDisplayName)
    }

    fun createInvitation(targetRole: DeviceRole) {
        val familyId = mutableState.value.memberships.firstOrNull()?.familyId ?: run {
            mutableState.update { it.copy(message = FamilyAccessMessage.PERMISSION_DENIED) }
            return
        }
        runPairingOperation(updateInvitation = true) {
            pairingService.createInvitation(familyId, targetRole)
        }
    }

    fun signOut() {
        if (mutableState.value.isWorking) return
        mutableState.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            try {
                try {
                    pushRegistrationRepository.unregisterCurrentInstallation()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    Log.e(LOG_TAG, "Push registration cleanup failed during sign-out.")
                }
            } finally {
                authRepository.signOut()
                mutableState.value = FamilyAccessUiState(
                    authState = FamilyAuthState.SignedOut
                )
            }
        }
    }

    fun dismissMessage() {
        mutableState.update { it.copy(message = null) }
    }

    fun dismissInvitation() {
        mutableState.update { it.copy(invitation = null) }
    }

    private fun membershipFlow(authState: FamilyAuthState): Flow<List<FamilyMembership>> =
        if (authState is FamilyAuthState.SignedIn) {
            familyRepository.observeMemberships()
        } else {
            flowOf(emptyList())
        }

    private fun runAuthOperation(
        successMessage: FamilyAccessMessage? = null,
        operation: suspend () -> FamilyAuthOperationResult,
    ) {
        if (mutableState.value.isWorking) return
        mutableState.update { it.copy(isWorking = true, message = null) }
        viewModelScope.launch {
            try {
                when (val result = operation()) {
                    FamilyAuthOperationResult.Success -> mutableState.update {
                        it.copy(isWorking = false, message = successMessage)
                    }

                    is FamilyAuthOperationResult.Failure -> mutableState.update {
                        it.copy(isWorking = false, message = result.reason.toMessage())
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(isWorking = false, message = FamilyAccessMessage.UNKNOWN_FAILURE)
                }
            }
        }
    }

    private fun <T> runPairingOperation(
        updateInvitation: Boolean = false,
        operation: suspend () -> DevicePairingResult<T>,
    ) {
        if (mutableState.value.isWorking) return
        mutableState.update { it.copy(isWorking = true, message = null) }
        viewModelScope.launch {
            try {
                when (val result = operation()) {
                    is DevicePairingResult.Success -> mutableState.update { current ->
                        current.copy(
                            isWorking = false,
                            invitation = if (updateInvitation) {
                                result.value as? PairingInvitation
                            } else {
                                current.invitation
                            },
                        )
                    }

                    is DevicePairingResult.Failure -> mutableState.update {
                        it.copy(isWorking = false, message = result.reason.toMessage())
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(isWorking = false, message = FamilyAccessMessage.UNKNOWN_FAILURE)
                }
            }
        }
    }

    private fun FamilyAuthFailure.toMessage(): FamilyAccessMessage = when (this) {
        FamilyAuthFailure.INVALID_CREDENTIALS -> FamilyAccessMessage.INVALID_CREDENTIALS
        FamilyAuthFailure.WEAK_PASSWORD -> FamilyAccessMessage.WEAK_PASSWORD
        FamilyAuthFailure.NETWORK_UNAVAILABLE -> FamilyAccessMessage.NETWORK_UNAVAILABLE
        FamilyAuthFailure.ACCOUNT_UNAVAILABLE -> FamilyAccessMessage.ACCOUNT_UNAVAILABLE
        FamilyAuthFailure.NOT_CONFIGURED -> FamilyAccessMessage.FIREBASE_NOT_CONFIGURED
        FamilyAuthFailure.UNKNOWN -> FamilyAccessMessage.UNKNOWN_FAILURE
    }

    private fun DevicePairingFailure.toMessage(): FamilyAccessMessage = when (this) {
        DevicePairingFailure.AUTHENTICATION_REQUIRED -> FamilyAccessMessage.INVALID_CREDENTIALS
        DevicePairingFailure.INVALID_INPUT -> FamilyAccessMessage.INVALID_INPUT
        DevicePairingFailure.INVITATION_INVALID -> FamilyAccessMessage.INVITATION_INVALID
        DevicePairingFailure.INVITATION_EXPIRED -> FamilyAccessMessage.INVITATION_EXPIRED
        DevicePairingFailure.INVITATION_ALREADY_USED ->
            FamilyAccessMessage.INVITATION_ALREADY_USED

        DevicePairingFailure.WRONG_DEVICE_ROLE -> FamilyAccessMessage.WRONG_DEVICE_ROLE
        DevicePairingFailure.NETWORK_UNAVAILABLE -> FamilyAccessMessage.NETWORK_UNAVAILABLE
        DevicePairingFailure.PERMISSION_DENIED -> FamilyAccessMessage.PERMISSION_DENIED
        DevicePairingFailure.NOT_CONFIGURED -> FamilyAccessMessage.FIREBASE_NOT_CONFIGURED
        DevicePairingFailure.UNKNOWN -> FamilyAccessMessage.UNKNOWN_FAILURE
    }

    class Factory(
        private val authRepository: FamilyAuthRepository,
        private val familyRepository: FamilyRepository,
        private val pairingService: DevicePairingService,
        private val pushRegistrationRepository: FamilyPushRegistrationRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(FamilyAccessViewModel::class.java))
            return FamilyAccessViewModel(
                authRepository = authRepository,
                familyRepository = familyRepository,
                pairingService = pairingService,
                pushRegistrationRepository = pushRegistrationRepository,
            ) as T
        }
    }

    private companion object {
        const val CONNECTION_REFRESH_INTERVAL_MILLIS = 60_000L
        const val LOG_TAG = "YanindaFamily"
    }
}
