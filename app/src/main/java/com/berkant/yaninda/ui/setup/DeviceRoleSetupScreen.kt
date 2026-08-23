package com.berkant.yaninda.ui.setup

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.berkant.yaninda.family.private.PrivateDeviceProfile
import com.berkant.yaninda.family.private.PrivateFamilyProvisioningResult
import com.berkant.yaninda.family.private.PrivateFamilyProvisioningService
import com.berkant.yaninda.ui.components.YanindaCard
import com.berkant.yaninda.ui.components.YanindaIcon
import com.berkant.yaninda.ui.components.YanindaIconBadge
import com.berkant.yaninda.ui.components.YanindaIconType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DeviceRoleSetupUiState(
    val isWorking: Boolean = false,
    val operationFailed: Boolean = false,
)

class DeviceRoleSetupViewModel(
    private val provisioningService: PrivateFamilyProvisioningService,
) : ViewModel() {
    private val mutableState =
        MutableStateFlow(
            DeviceRoleSetupUiState()
        )

    val state: StateFlow<DeviceRoleSetupUiState> =
        mutableState.asStateFlow()

    fun select(
        profile: PrivateDeviceProfile,
    ) {
        if (mutableState.value.isWorking) return

        mutableState.value =
            DeviceRoleSetupUiState(
                isWorking = true
            )

        viewModelScope.launch {
            try {
                val result =
                    provisioningService.provision(
                        profile
                    )

                mutableState.value =
                    when (result) {
                        PrivateFamilyProvisioningResult.Success ->
                            DeviceRoleSetupUiState()

                        else ->
                            DeviceRoleSetupUiState(
                                operationFailed = true
                            )
                    }

            } catch (error: CancellationException) {
                throw error

            } catch (_: Exception) {
                mutableState.value =
                    DeviceRoleSetupUiState(
                        operationFailed = true
                    )
            }
        }
    }

    class Factory(
        private val provisioningService:
            PrivateFamilyProvisioningService,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: Class<T>,
        ): T {
            require(
                modelClass.isAssignableFrom(
                    DeviceRoleSetupViewModel::class.java
                )
            )

            return DeviceRoleSetupViewModel(
                provisioningService
            ) as T
        }
    }
}

@Composable
fun DeviceRoleSetupRoute() {
    val application =
        LocalContext.current.applicationContext
            as YanindaApplication

    val factory =
        remember(application) {
            DeviceRoleSetupViewModel.Factory(
                provisioningService =
                    application
                        .privateFamilyProvisioningService,
            )
        }

    val viewModel: DeviceRoleSetupViewModel =
        viewModel(factory = factory)

    val state by
        viewModel.state.collectAsStateWithLifecycle()

    DeviceRoleSetupScreen(
        state = state,
        onProfile = viewModel::select,
    )
}

@Composable
private fun DeviceRoleSetupScreen(
    state: DeviceRoleSetupUiState,
    onProfile: (PrivateDeviceProfile) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(
                        rememberScrollState()
                    )
                    .padding(
                        horizontal = 20.dp,
                        vertical = 20.dp,
                    ),
            verticalArrangement =
                Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                YanindaIconBadge(
                    icon = YanindaIconType.CHECK,
                    size = 44.dp,
                    containerColor = MaterialTheme.colorScheme.primary,
                    iconColor = MaterialTheme.colorScheme.onPrimary,
                )

                Spacer(
                    Modifier.width(12.dp)
                )

                Column {
                    Text(
                        text = "Yanında",
                        style = MaterialTheme.typography.titleLarge,
                    )

                    Text(
                        text = "İlk kurulum",
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
                    modifier =
                        Modifier.padding(
                            horizontal = 22.dp,
                            vertical = 24.dp,
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    YanindaIconBadge(
                        icon = YanindaIconType.FAMILY,
                        size = 84.dp,
                        containerColor = MaterialTheme.colorScheme.surface,
                        iconColor = MaterialTheme.colorScheme.primary,
                    )

                    Text(
                        text = "Bu telefon kimin?",
                        style = MaterialTheme.typography.headlineLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics {
                            heading()
                        },
                    )

                    Text(
                        text =
                            "Bir kez seçmen yeterli. Hesap, parola veya eşleştirme kodu gerekmiyor.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            FixedProfileCard(
                title = "Dede telefonu",
                body = "İlaç alarmlarını gösterir.",
                icon = YanindaIconType.ALARM,
                enabled = !state.isWorking,
                onClick = {
                    onProfile(
                        PrivateDeviceProfile.GRANDFATHER
                    )
                },
            )

            FixedProfileCard(
                title = "Anneanne telefonu",
                body = "İlaç alarmlarını gösterir.",
                icon = YanindaIconType.ALARM,
                enabled = !state.isWorking,
                onClick = {
                    onProfile(
                        PrivateDeviceProfile.GRANDMOTHER
                    )
                },
            )

            FixedProfileCard(
                title = "Berkant telefonu",
                body = "İlaç programını ve aile durumunu yönetir.",
                icon = YanindaIconType.FAMILY,
                enabled = !state.isWorking,
                onClick = {
                    onProfile(
                        PrivateDeviceProfile.BERKANT
                    )
                },
            )

            FixedProfileCard(
                title = "Anne telefonu",
                body = "İlaç programını ve aile durumunu yönetir.",
                icon = YanindaIconType.FAMILY,
                enabled = !state.isWorking,
                onClick = {
                    onProfile(
                        PrivateDeviceProfile.MOTHER
                    )
                },
            )

            if (state.isWorking) {
                Text(
                    text = "Telefon hazırlanıyor…",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (state.operationFailed) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        YanindaIcon(
                            type = YanindaIconType.WARNING,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                        )

                        Text(
                            text =
                                "Telefon hazırlanamadı. Firebase bağlantısını kontrol edip tekrar deneyin.",
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
private fun FixedProfileCard(
    title: String,
    body: String,
    icon: YanindaIconType,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    YanindaCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = enabled,
                    onClick = onClick,
                ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            YanindaIconBadge(
                icon = icon,
                size = 58.dp,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                )

                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
