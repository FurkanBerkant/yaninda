package com.berkant.yaninda.ui.family

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.berkant.yaninda.auth.FamilyAuthRepository
import com.berkant.yaninda.auth.FamilyAuthState
import com.berkant.yaninda.data.device.DeviceIdentityRepository
import com.berkant.yaninda.domain.family.DeviceRegistration
import com.berkant.yaninda.domain.family.DeviceRole
import com.berkant.yaninda.domain.family.FamilyConnectionFreshness
import com.berkant.yaninda.domain.family.FamilyConnectionStatus
import com.berkant.yaninda.domain.family.FamilyDoseOccurrence
import com.berkant.yaninda.domain.family.FamilyMembership
import com.berkant.yaninda.domain.family.PendingDeviceApproval
import com.berkant.yaninda.family.FamilyRepositoryResult
import com.berkant.yaninda.domain.family.FamilyMonitoringPolicy
import com.berkant.yaninda.family.FamilyRepository
import com.berkant.yaninda.push.FamilyPushRegistrationRepository
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FamilyAccessUiState(
    val authState: FamilyAuthState = FamilyAuthState.SignedOut,
    val memberships: List<FamilyMembership> = emptyList(),
    val devices: List<DeviceRegistration> = emptyList(),
    val occurrences: List<FamilyDoseOccurrence> = emptyList(),
    val pendingDeviceApprovals: List<PendingDeviceApproval> = emptyList(),
    val approvingDeviceUid: String? = null,
    val removingDeviceId: String? = null,
    val currentDeviceId: String? = null,
    val deviceApprovalMessage: String? = null,
    val connectionStatus: FamilyConnectionStatus = FamilyConnectionStatus(
        FamilyConnectionFreshness.ALARM_DEVICE_NOT_PAIRED,
        null,
    ),
)

@OptIn(ExperimentalCoroutinesApi::class)
class FamilyAccessViewModel(
    private val authRepository: FamilyAuthRepository,
    private val familyRepository: FamilyRepository,
    private val pushRegistrationRepository: FamilyPushRegistrationRepository,
    private val deviceIdentityRepository: DeviceIdentityRepository,
    private val monitoringPolicy: FamilyMonitoringPolicy = FamilyMonitoringPolicy(),
    private val now: () -> Instant = Instant::now,
    private val monitoringZoneId: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(FamilyAccessUiState())
    val state: StateFlow<FamilyAccessUiState> = mutableState.asStateFlow()

    private val occurrenceObservationDate = MutableStateFlow(
        now().atZone(monitoringZoneId).toLocalDate()
    )

    private val membershipsFlow = authRepository.state
        .flatMapLatest(::membershipFlow)
        .retryFamilyObservation()
        .distinctUntilChanged()
        .shareIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            replay = 1,
        )

    init {
        viewModelScope.launch {
            mutableState.update {
                it.copy(currentDeviceId = deviceIdentityRepository.getOrCreateDeviceId())
            }
        }
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
                    )
                }
            }
        }

        viewModelScope.launch {
            membershipsFlow
                .map { memberships -> memberships.firstOrNull()?.familyId }
                .distinctUntilChanged()
                .flatMapLatest { familyId ->
                    familyId?.let(familyRepository::observePendingDeviceApprovals)
                        ?: flowOf(emptyList())
                }
                .retryFamilyObservation()
                .collect { approvals ->
                    mutableState.update { current ->
                        current.copy(pendingDeviceApprovals = approvals)
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
                        Log.w(LOG_TAG, "Push registration will be retried later.")
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
                .retryFamilyObservation()
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
                .combine(occurrenceObservationDate) { familyId, observationDate ->
                    familyId to observationDate
                }
                .distinctUntilChanged()
                .flatMapLatest { (familyId, _) ->
                    familyId?.let { id ->
                        familyRepository.observeOccurrences(id).map { id to it }
                    } ?: flowOf(null to emptyList())
                }
                .retryFamilyObservation()
                .collect { (_, occurrences) ->
                    mutableState.update { current ->
                        current.copy(occurrences = occurrences)
                    }
                }
        }

        viewModelScope.launch {
            while (true) {
                delay(CONNECTION_REFRESH_INTERVAL_MILLIS)
                occurrenceObservationDate.value = now()
                    .atZone(monitoringZoneId)
                    .toLocalDate()
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

    fun approveDevice(approval: PendingDeviceApproval) {
        if (mutableState.value.approvingDeviceUid != null) return

        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    approvingDeviceUid = approval.uid,
                    deviceApprovalMessage = null,
                )
            }
            val result = familyRepository.approveDevice(approval)
            mutableState.update {
                it.copy(
                    approvingDeviceUid = null,
                    deviceApprovalMessage = when (result) {
                        is FamilyRepositoryResult.Success ->
                            "${approval.displayName} onaylandı."
                        is FamilyRepositoryResult.Failure ->
                            "Cihaz onaylanamadı. İnternet bağlantısını kontrol edip tekrar deneyin."
                    },
                )
            }
        }
    }

    fun removeDevice(device: DeviceRegistration) {
        if (
            mutableState.value.removingDeviceId != null ||
            device.deviceId == mutableState.value.currentDeviceId
        ) return

        viewModelScope.launch {
            mutableState.update {
                it.copy(removingDeviceId = device.deviceId, deviceApprovalMessage = null)
            }
            val result = familyRepository.removeDevice(device)
            mutableState.update {
                it.copy(
                    removingDeviceId = null,
                    deviceApprovalMessage = when (result) {
                        is FamilyRepositoryResult.Success -> "${device.displayName} kaldırıldı."
                        is FamilyRepositoryResult.Failure ->
                            "Cihaz kaldırılamadı. İnternet bağlantısını kontrol edip tekrar deneyin."
                    },
                )
            }
        }
    }

    private fun membershipFlow(authState: FamilyAuthState): Flow<List<FamilyMembership>> =
        if (authState is FamilyAuthState.SignedIn) {
            familyRepository.observeMemberships()
        } else {
            flowOf(emptyList())
        }

    private fun <T> Flow<T>.retryFamilyObservation(): Flow<T> =
        retryWhen { error, _ ->
            if (error is CancellationException) {
                return@retryWhen false
            }

            Log.w(
                LOG_TAG,
                "Family observation failed; retrying. error=${error::class.java.simpleName}",
            )
            delay(FAMILY_OBSERVER_RETRY_MILLIS)
            true
        }

    class Factory(
        private val authRepository: FamilyAuthRepository,
        private val familyRepository: FamilyRepository,
        private val pushRegistrationRepository: FamilyPushRegistrationRepository,
        private val deviceIdentityRepository: DeviceIdentityRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(FamilyAccessViewModel::class.java))
            return FamilyAccessViewModel(
                authRepository = authRepository,
                familyRepository = familyRepository,
                pushRegistrationRepository = pushRegistrationRepository,
                deviceIdentityRepository = deviceIdentityRepository,
            ) as T
        }
    }

    private companion object {
        const val CONNECTION_REFRESH_INTERVAL_MILLIS = 60_000L
        const val FAMILY_OBSERVER_RETRY_MILLIS = 3_000L
        const val LOG_TAG = "YanindaFamily"
    }
}
