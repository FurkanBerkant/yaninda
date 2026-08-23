package com.berkant.yaninda.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.berkant.yaninda.R
import com.berkant.yaninda.YanindaApplication
import com.berkant.yaninda.auth.FamilyAuthRepository
import com.berkant.yaninda.data.device.DeviceIdentityRepository
import com.berkant.yaninda.domain.family.DeviceRole
import com.berkant.yaninda.ui.components.YanindaCard
import com.berkant.yaninda.ui.components.YanindaIcon
import com.berkant.yaninda.ui.components.YanindaIconBadge
import com.berkant.yaninda.ui.components.YanindaIconType
import com.berkant.yaninda.ui.components.YanindaOutlinedButton
import com.berkant.yaninda.ui.components.YanindaPrimaryButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DeviceRoleSetupUiState(
    val isWorking: Boolean = false,
    val operationFailed: Boolean = false,
)

class DeviceRoleSetupViewModel(
    private val deviceIdentityRepository: DeviceIdentityRepository,
    private val authRepository: FamilyAuthRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DeviceRoleSetupUiState())
    val state: StateFlow<DeviceRoleSetupUiState> = mutableState.asStateFlow()

    fun selectAlarmDevice() = select(DeviceRole.ALARM_DEVICE) {
        // Network availability must not block local medication-device setup.
        authRepository.ensureAlarmDeviceSession()
    }

    fun selectAdminDevice() = select(DeviceRole.ADMIN_DEVICE)

    private fun select(
        role: DeviceRole,
        afterSelection: suspend () -> Unit = {},
    ) {
        if (mutableState.value.isWorking) return
        mutableState.value = DeviceRoleSetupUiState(isWorking = true)
        viewModelScope.launch {
            try {
                deviceIdentityRepository.selectRole(role)
                afterSelection()
                mutableState.value = DeviceRoleSetupUiState()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update { it.copy(isWorking = false, operationFailed = true) }
            }
        }
    }

    class Factory(
        private val deviceIdentityRepository: DeviceIdentityRepository,
        private val authRepository: FamilyAuthRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(DeviceRoleSetupViewModel::class.java))
            return DeviceRoleSetupViewModel(deviceIdentityRepository, authRepository) as T
        }
    }
}

@Composable
fun DeviceRoleSetupRoute() {
    val application = LocalContext.current.applicationContext as YanindaApplication
    val factory = remember(application) {
        DeviceRoleSetupViewModel.Factory(
            deviceIdentityRepository = application.deviceIdentityRepository,
            authRepository = application.familyAuthRepository,
        )
    }
    val viewModel: DeviceRoleSetupViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    DeviceRoleSetupScreen(
        state = state,
        onAlarmDevice = viewModel::selectAlarmDevice,
        onCaregiver = viewModel::selectAdminDevice,
    )
}

@Composable
private fun DeviceRoleSetupScreen(
    state: DeviceRoleSetupUiState,
    onAlarmDevice: () -> Unit,
    onCaregiver: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                YanindaIconBadge(
                    icon = YanindaIconType.CHECK,
                    size = 44.dp,
                    containerColor = MaterialTheme.colorScheme.primary,
                    iconColor = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = stringResource(R.string.setup_step_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    YanindaIconBadge(
                        icon = YanindaIconType.FAMILY,
                        size = 84.dp,
                        containerColor = MaterialTheme.colorScheme.surface,
                        iconColor = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.setup_welcome),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(R.string.setup_role_question),
                        style = MaterialTheme.typography.headlineLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(R.string.setup_role_explanation),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            DeviceRoleCard(
                icon = YanindaIconType.ALARM,
                title = stringResource(R.string.setup_primary_action),
                badge = stringResource(R.string.setup_primary_badge),
                body = stringResource(R.string.setup_primary_body),
                enabled = !state.isWorking,
                isAlarmDevice = true,
                onClick = onAlarmDevice,
            )
            DeviceRoleCard(
                icon = YanindaIconType.FAMILY,
                title = stringResource(R.string.setup_caregiver_action),
                badge = stringResource(R.string.setup_caregiver_badge),
                body = stringResource(R.string.setup_caregiver_body),
                enabled = !state.isWorking,
                isAlarmDevice = false,
                onClick = onCaregiver,
            )
            if (state.operationFailed) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        YanindaIcon(
                            type = YanindaIconType.WARNING,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                        )
                        Text(
                            text = stringResource(R.string.setup_failed),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRoleCard(
    icon: YanindaIconType,
    title: String,
    badge: String,
    body: String,
    enabled: Boolean,
    isAlarmDevice: Boolean,
    onClick: () -> Unit,
) {
    YanindaCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (isAlarmDevice) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                YanindaIconBadge(icon = icon, size = 52.dp)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(text = title, style = MaterialTheme.typography.titleLarge)
                }
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isAlarmDevice) {
                YanindaPrimaryButton(
                    text = title,
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth(),
                    icon = YanindaIconType.CHEVRON,
                    enabled = enabled,
                )
            } else {
                YanindaOutlinedButton(
                    text = title,
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth(),
                    icon = YanindaIconType.CHEVRON,
                    enabled = enabled,
                    minHeight = 64.dp,
                )
            }
        }
    }
}
