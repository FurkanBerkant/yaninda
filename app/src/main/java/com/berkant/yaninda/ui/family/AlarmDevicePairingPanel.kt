package com.berkant.yaninda.ui.family

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.berkant.yaninda.R
import com.berkant.yaninda.YanindaApplication
import com.berkant.yaninda.data.device.DeviceIdentityRepository
import com.berkant.yaninda.domain.family.FamilyPairing
import com.berkant.yaninda.family.DevicePairingFailure
import com.berkant.yaninda.family.DevicePairingResult
import com.berkant.yaninda.family.DevicePairingService
import com.berkant.yaninda.ui.components.YanindaCard
import com.berkant.yaninda.ui.components.YanindaIconType
import com.berkant.yaninda.ui.components.YanindaInfoRow
import com.berkant.yaninda.ui.components.YanindaPrimaryButton
import com.berkant.yaninda.ui.components.YanindaSectionTitle
import com.berkant.yaninda.ui.components.YanindaStatusTone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AlarmDevicePairingUiState(
    val pairing: FamilyPairing? = null,
    val isWorking: Boolean = false,
    val failure: DevicePairingFailure? = null,
)

class AlarmDevicePairingViewModel(
    private val deviceIdentityRepository: DeviceIdentityRepository,
    private val pairingService: DevicePairingService,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AlarmDevicePairingUiState())
    val state: StateFlow<AlarmDevicePairingUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            deviceIdentityRepository.pairing.collect { pairing ->
                mutableState.update { current -> current.copy(pairing = pairing) }
            }
        }
    }

    fun pair(code: String, deviceName: String) {
        if (mutableState.value.isWorking || mutableState.value.pairing != null) return
        mutableState.update { it.copy(isWorking = true, failure = null) }
        viewModelScope.launch {
            try {
                when (val result = pairingService.pairAlarmDevice(code, deviceName)) {
                    is DevicePairingResult.Success -> mutableState.update {
                        it.copy(isWorking = false, failure = null)
                    }

                    is DevicePairingResult.Failure -> mutableState.update {
                        it.copy(isWorking = false, failure = result.reason)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(isWorking = false, failure = DevicePairingFailure.UNKNOWN)
                }
            }
        }
    }

    class Factory(
        private val deviceIdentityRepository: DeviceIdentityRepository,
        private val pairingService: DevicePairingService,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AlarmDevicePairingViewModel::class.java))
            return AlarmDevicePairingViewModel(deviceIdentityRepository, pairingService) as T
        }
    }
}

@Composable
fun AlarmDevicePairingPanelRoute() {
    val application = LocalContext.current.applicationContext as YanindaApplication
    val factory = remember(application) {
        AlarmDevicePairingViewModel.Factory(
            deviceIdentityRepository = application.deviceIdentityRepository,
            pairingService = application.devicePairingService,
        )
    }
    val viewModel: AlarmDevicePairingViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    AlarmDevicePairingPanel(state, viewModel::pair)
}

@Composable
private fun AlarmDevicePairingPanel(
    state: AlarmDevicePairingUiState,
    onPair: (String, String) -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf("") }
    YanindaCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            YanindaSectionTitle(
                title = stringResource(R.string.primary_pairing_title),
                icon = YanindaIconType.FAMILY,
            )
            if (state.pairing != null) {
                YanindaInfoRow(
                    label = stringResource(R.string.primary_pairing_ready),
                    value = stringResource(R.string.primary_pairing_local_alarm_notice),
                    icon = YanindaIconType.CHECK,
                    tone = YanindaStatusTone.SUCCESS,
                )
            } else {
                Text(
                    text = stringResource(R.string.primary_pairing_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.take(24) },
                    label = { Text(stringResource(R.string.family_code_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it.take(80) },
                    label = { Text(stringResource(R.string.family_device_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                YanindaPrimaryButton(
                    text = stringResource(R.string.primary_pairing_action),
                    onClick = { onPair(code, deviceName) },
                    enabled = !state.isWorking,
                    modifier = Modifier.fillMaxWidth(),
                    icon = YanindaIconType.FAMILY,
                )
                state.failure?.let { failure ->
                    YanindaInfoRow(
                        label = stringResource(R.string.primary_pairing_title),
                        value = stringResource(failure.messageResource()),
                        icon = YanindaIconType.WARNING,
                        tone = YanindaStatusTone.ERROR,
                    )
                }
            }
        }
    }
}

private fun DevicePairingFailure.messageResource(): Int = when (this) {
    DevicePairingFailure.AUTHENTICATION_REQUIRED -> R.string.family_error_invalid_credentials
    DevicePairingFailure.INVALID_INPUT -> R.string.family_error_invalid_input
    DevicePairingFailure.INVITATION_INVALID -> R.string.family_error_invitation_invalid
    DevicePairingFailure.INVITATION_EXPIRED -> R.string.family_error_invitation_expired
    DevicePairingFailure.INVITATION_ALREADY_USED -> R.string.family_error_invitation_used
    DevicePairingFailure.WRONG_DEVICE_ROLE -> R.string.family_error_wrong_role
    DevicePairingFailure.NETWORK_UNAVAILABLE -> R.string.family_error_network
    DevicePairingFailure.PERMISSION_DENIED -> R.string.family_error_permission
    DevicePairingFailure.NOT_CONFIGURED -> R.string.family_error_not_configured
    DevicePairingFailure.UNKNOWN -> R.string.family_error_unknown
}
