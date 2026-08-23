package com.berkant.yaninda.ui.family

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import com.berkant.yaninda.R
import com.berkant.yaninda.YanindaApplication
import com.berkant.yaninda.auth.FamilyAuthState
import com.berkant.yaninda.domain.family.DeviceRole
import com.berkant.yaninda.domain.family.DeviceRegistration
import com.berkant.yaninda.domain.family.FamilyConnectionFreshness
import com.berkant.yaninda.domain.family.FamilyConnectionStatus
import com.berkant.yaninda.domain.family.FamilyDoseOccurrence
import com.berkant.yaninda.domain.family.FamilyMemberRole
import com.berkant.yaninda.domain.family.FamilyMembership
import com.berkant.yaninda.domain.family.PairingCodeNormalizer
import com.berkant.yaninda.domain.family.PairingInvitation
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.berkant.yaninda.domain.occurrence.DoseOccurrenceStatus
import com.berkant.yaninda.secondary.SecondaryReminderRuntimeStatus
import com.berkant.yaninda.ui.components.YanindaIcon
import com.berkant.yaninda.ui.components.YanindaIconBadge
import com.berkant.yaninda.ui.components.YanindaIconType
import com.berkant.yaninda.ui.components.YanindaOutlinedButton
import com.berkant.yaninda.ui.components.YanindaPrimaryButton
import com.berkant.yaninda.ui.components.YanindaStatusPill
import com.berkant.yaninda.ui.components.YanindaStatusTone
import androidx.compose.ui.res.pluralStringResource

@Composable
fun FamilyAccessRoute(
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val application = context.applicationContext as YanindaApplication
    val factory = remember(application) {
        FamilyAccessViewModel.Factory(
            authRepository = application.familyAuthRepository,
            familyRepository = application.familyRepository,
            pairingService = application.devicePairingService,
            pushRegistrationRepository = application.familyPushRegistrationRepository,
            secondaryReminderCoordinator = application.secondaryReminderCoordinator,
        )
    }
    val viewModel: FamilyAccessViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    var notificationsGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notificationsGranted = granted }
    FamilyAccessScreen(
        state = state,
        onBack = onBack,
        onCreateAccount = viewModel::createAccount,
        onSignIn = viewModel::signIn,
        onPasswordReset = viewModel::sendPasswordReset,
        onCreateFamily = viewModel::createFamily,
        onJoinFamily = viewModel::joinFamily,
        onCreatePrimaryInvitation = {
            viewModel.createInvitation(DeviceRole.ALARM_DEVICE)
        },
        onCreateCaregiverInvitation = {
            viewModel.createInvitation(DeviceRole.ADMIN_DEVICE)
        },
        onSignOut = viewModel::signOut,
        onSetSecondaryReminderEnabled = viewModel::setSecondaryReminderEnabled,
        onDismissMessage = viewModel::dismissMessage,
        onDismissInvitation = viewModel::dismissInvitation,
        notificationsGranted = notificationsGranted,
        onRequestNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
    )
}

@Composable
private fun FamilyAccessScreen(
    state: FamilyAccessUiState,
    onBack: (() -> Unit)?,
    onCreateAccount: (String, String) -> Unit,
    onSignIn: (String, String) -> Unit,
    onPasswordReset: (String) -> Unit,
    onCreateFamily: (String, String) -> Unit,
    onJoinFamily: (String, String) -> Unit,
    onCreatePrimaryInvitation: () -> Unit,
    onCreateCaregiverInvitation: () -> Unit,
    onSignOut: () -> Unit,
    onSetSecondaryReminderEnabled: (Boolean) -> Unit,
    onDismissMessage: () -> Unit,
    onDismissInvitation: () -> Unit,
    notificationsGranted: Boolean,
    onRequestNotifications: () -> Unit,
) {
    val title = stringResource(R.string.family_title)
    BackHandler(enabled = onBack != null) {
        onBack?.invoke()
    }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .semantics { paneTitle = title },
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            onBack?.let { goBack ->
                YanindaOutlinedButton(
                    text = "GERİ",
                    onClick = goBack,
                    modifier = Modifier.fillMaxWidth(),
                    icon = YanindaIconType.BACK,
                )
            }
            FamilyHeroHeader(
                title = title,
                subtitle = stringResource(R.string.family_read_only_notice),
            )
            when (val authState = state.authState) {
                FamilyAuthState.Unavailable -> FamilyUnavailableCard()
                FamilyAuthState.SignedOut -> FamilyAuthForm(
                    isWorking = state.isWorking,
                    onCreateAccount = onCreateAccount,
                    onSignIn = onSignIn,
                    onPasswordReset = onPasswordReset,
                )

                is FamilyAuthState.SignedIn -> if (authState.isAnonymous) {
                    FamilyAnonymousSessionCard(onSignOut)
                } else if (state.memberships.isEmpty()) {
                    FamilyOnboardingForms(
                        isWorking = state.isWorking,
                        onCreateFamily = onCreateFamily,
                        onJoinFamily = onJoinFamily,
                        onSignOut = onSignOut,
                    )
                } else {
                    ConnectedFamilyCard(
                        membership = state.memberships.first(),
                        devices = state.devices,
                        occurrences = state.occurrences,
                        connectionStatus = state.connectionStatus,
                        email = authState.email,
                        isWorking = state.isWorking,
                        onCreatePrimaryInvitation = onCreatePrimaryInvitation,
                        onCreateCaregiverInvitation = onCreateCaregiverInvitation,
                        onSignOut = onSignOut,
                        secondaryReminderStatus = state.secondaryReminderStatus,
                        onSetSecondaryReminderEnabled = onSetSecondaryReminderEnabled,
                        notificationsGranted = notificationsGranted,
                        onRequestNotifications = onRequestNotifications,
                    )
                }
            }
        }
    }

    state.invitation?.let { invitation ->
        PairingInvitationDialog(invitation, onDismissInvitation)
    }
    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = onDismissMessage,
            title = { Text(stringResource(R.string.family_message_title)) },
            text = { Text(stringResource(message.stringResource())) },
            confirmButton = {
                Button(
                    onClick = onDismissMessage,
                    modifier = Modifier.heightIn(min = 52.dp),
                ) {
                    Text(stringResource(R.string.caregiver_ok))
                }
            },
        )
    }
}

@Composable
private fun FamilyHeroHeader(
    title: String,
    subtitle: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            YanindaIconBadge(
                icon = YanindaIconType.FAMILY,
                size = 62.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                iconColor = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FamilyAuthForm(
    isWorking: Boolean,
    onCreateAccount: (String, String) -> Unit,
    onSignIn: (String, String) -> Unit,
    onPasswordReset: (String) -> Unit,
) {
    var createMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    FamilyCard {
        Text(
            text = stringResource(
                if (createMode) R.string.family_create_account_title
                else R.string.family_sign_in_title
            ),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it.take(MAX_EMAIL_INPUT) },
            label = { Text(stringResource(R.string.family_email_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it.take(MAX_PASSWORD_INPUT) },
            label = { Text(stringResource(R.string.family_password_label)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                if (createMode) onCreateAccount(email, password) else onSignIn(email, password)
            },
            enabled = !isWorking,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp),
        ) {
            Text(
                stringResource(
                    if (createMode) R.string.family_create_account_action
                    else R.string.family_sign_in_action
                )
            )
        }
        OutlinedButton(
            onClick = { createMode = !createMode },
            enabled = !isWorking,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
        ) {
            Text(
                stringResource(
                    if (createMode) R.string.family_have_account
                    else R.string.family_need_account
                )
            )
        }
        if (!createMode) {
            OutlinedButton(
                onClick = { onPasswordReset(email) },
                enabled = !isWorking,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Text(stringResource(R.string.family_password_reset))
            }
        }
    }
}

@Composable
private fun FamilyOnboardingForms(
    isWorking: Boolean,
    onCreateFamily: (String, String) -> Unit,
    onJoinFamily: (String, String) -> Unit,
    onSignOut: () -> Unit,
) {
    var familyName by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf("") }
    FamilyCard {
        Text(
            text = stringResource(R.string.family_create_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        OutlinedTextField(
            value = familyName,
            onValueChange = { familyName = it.take(MAX_LABEL_INPUT) },
            label = { Text(stringResource(R.string.family_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it.take(MAX_LABEL_INPUT) },
            label = { Text(stringResource(R.string.family_display_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onCreateFamily(familyName, displayName) },
            enabled = !isWorking,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
            Text(stringResource(R.string.family_create_action))
        }
    }
    FamilyCard {
        Text(
            text = stringResource(R.string.family_join_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.family_join_body),
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.take(MAX_CODE_INPUT) },
            label = { Text(stringResource(R.string.family_code_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = deviceName,
            onValueChange = { deviceName = it.take(MAX_LABEL_INPUT) },
            label = { Text(stringResource(R.string.family_device_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onJoinFamily(code, deviceName) },
            enabled = !isWorking,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
            Text(stringResource(R.string.family_join_action))
        }
    }
    OutlinedButton(
        onClick = onSignOut,
        enabled = !isWorking,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
    ) {
        Text(stringResource(R.string.family_sign_out))
    }
}

@Composable
private fun ConnectedFamilyCard(
    membership: FamilyMembership,
    devices: List<DeviceRegistration>,
    occurrences: List<FamilyDoseOccurrence>,
    connectionStatus: FamilyConnectionStatus,
    email: String?,
    isWorking: Boolean,
    onCreatePrimaryInvitation: () -> Unit,
    onCreateCaregiverInvitation: () -> Unit,
    onSignOut: () -> Unit,
    secondaryReminderStatus: SecondaryReminderRuntimeStatus,
    onSetSecondaryReminderEnabled: (Boolean) -> Unit,
    notificationsGranted: Boolean,
    onRequestNotifications: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                YanindaIconBadge(
                    icon = YanindaIconType.PERSON,
                    size = 64.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    iconColor = MaterialTheme.colorScheme.primary,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = membership.familyName,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(
                            R.string.family_connected_as,
                            membership.displayName,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    email?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier.padding(15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                YanindaIcon(
                    type = YanindaIconType.INFO,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.family_dashboard_local_alarm_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        FamilyConnectionCard(connectionStatus, devices)
        FamilyOccurrenceTimeline(
            occurrences = occurrences,
            isStale = connectionStatus.freshness == FamilyConnectionFreshness.STALE,
        )

        if (!notificationsGranted) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.large,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.family_notification_permission_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(stringResource(R.string.family_notification_permission_body))
                    Button(
                        onClick = onRequestNotifications,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                    ) {
                        Text(stringResource(R.string.allow_notifications))
                    }
                }
            }
        }
        if (membership.role == FamilyMemberRole.ADMIN) {
            YanindaPrimaryButton(
                text = stringResource(R.string.family_invite_primary),
                onClick = onCreatePrimaryInvitation,
                enabled = !isWorking,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                icon = YanindaIconType.DEVICE,
                minHeight = 56.dp,
            )
            YanindaOutlinedButton(
                text = stringResource(R.string.family_invite_caregiver),
                onClick = onCreateCaregiverInvitation,
                enabled = !isWorking,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                icon = YanindaIconType.FAMILY,
                minHeight = 52.dp,
            )
        }
        YanindaOutlinedButton(
            text = stringResource(R.string.family_sign_out),
            onClick = onSignOut,
            enabled = !isWorking,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            icon = YanindaIconType.LOCK,
            minHeight = 52.dp,
        )
    }
}

@Composable
private fun SecondaryReminderCard(
    status: SecondaryReminderRuntimeStatus,
    onEnabledChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.secondary_reminder_card_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.secondary_reminder_primary_notice),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.secondary_reminder_card_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.secondary_reminder_toggle),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                val switchDescription = stringResource(R.string.secondary_reminder_toggle)
                Switch(
                    checked = status.enabled,
                    onCheckedChange = onEnabledChange,
                    modifier = Modifier.semantics {
                        contentDescription = switchDescription
                    },
                )
            }
            Text(
                text = pluralStringResource(
                    R.plurals.secondary_reminder_cached_count,
                    status.cachedOccurrenceCount,
                    status.cachedOccurrenceCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            status.nextReminderAt?.let { nextReminderAt ->
                Text(
                    text = stringResource(
                        R.string.secondary_reminder_next,
                        FAMILY_DATE_TIME_FORMAT.format(
                            nextReminderAt.atZone(ZoneId.systemDefault())
                        ),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (status.approximateOccurrenceCount > 0) {
                Text(
                    text = stringResource(R.string.secondary_reminder_approximate_notice),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (status.hasSchedulingFailure) {
                Text(
                    text = stringResource(R.string.secondary_reminder_failure),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun FamilyConnectionCard(
    status: FamilyConnectionStatus,
    devices: List<DeviceRegistration>,
) {
    val isAttention = status.freshness != FamilyConnectionFreshness.CURRENT
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isAttention) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        contentColor = if (isAttention) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            YanindaIconBadge(
                icon = YanindaIconType.DEVICE,
                size = 48.dp,
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                iconColor = if (isAttention) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                YanindaStatusPill(
                    text = stringResource(status.freshness.titleResource()),
                    tone = if (isAttention) YanindaStatusTone.ERROR else YanindaStatusTone.SUCCESS,
                )
                Text(
                    text = stringResource(status.freshness.bodyResource()),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.semantics { heading() },
                )
                status.lastSuccessfulSyncAt?.let { lastSync ->
                    Text(
                        text = stringResource(
                            R.string.family_last_sync_value,
                            FAMILY_DATE_TIME_FORMAT.format(lastSync.atZone(ZoneId.systemDefault())),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                devices.firstOrNull {
                    it.role == DeviceRole.ALARM_DEVICE
                }?.let { device ->
                    Text(
                        text = stringResource(
                            R.string.family_primary_device_value,
                            device.displayName,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun FamilyOccurrenceTimeline(
    occurrences: List<FamilyDoseOccurrence>,
    isStale: Boolean,
) {
    Text(
        text = stringResource(
            if (isStale) R.string.family_last_known_records
            else R.string.family_occurrence_timeline_title
        ),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics { heading() },
    )
    if (occurrences.isEmpty()) {
        Text(
            text = stringResource(R.string.family_no_occurrences),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        occurrences.take(MAX_VISIBLE_OCCURRENCES).forEach { occurrence ->
            val statusTone = when (occurrence.status) {
                DoseOccurrenceStatus.ACKNOWLEDGED_TAKEN -> YanindaStatusTone.SUCCESS
                DoseOccurrenceStatus.DUE,
                DoseOccurrenceStatus.NO_CONFIRMATION,
                -> YanindaStatusTone.WARNING
                DoseOccurrenceStatus.SNOOZED -> YanindaStatusTone.INFO
                DoseOccurrenceStatus.SCHEDULED,
                DoseOccurrenceStatus.CANCELLED,
                -> YanindaStatusTone.NEUTRAL
            }
            val statusIcon = when (occurrence.status) {
                DoseOccurrenceStatus.ACKNOWLEDGED_TAKEN -> YanindaIconType.CHECK
                DoseOccurrenceStatus.DUE,
                DoseOccurrenceStatus.NO_CONFIRMATION,
                -> YanindaIconType.ALARM
                DoseOccurrenceStatus.SNOOZED,
                DoseOccurrenceStatus.SCHEDULED,
                DoseOccurrenceStatus.CANCELLED,
                -> YanindaIconType.CLOCK
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large,
                shadowElevation = 1.dp,
            ) {
                Row(
                    modifier = Modifier.padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    YanindaIconBadge(icon = statusIcon, size = 48.dp)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = FAMILY_OCCURRENCE_TIME_FORMAT.format(
                                occurrence.scheduledAt.atZone(ZoneId.systemDefault())
                            ),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = occurrence.medicationDisplayName,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        YanindaStatusPill(
                            text = stringResource(occurrence.status.statusResource()),
                            tone = statusTone,
                        )
                        occurrence.statusTimestamp()?.let { timestamp ->
                            Text(
                                text = stringResource(
                                    R.string.family_status_time_value,
                                    FAMILY_DATE_TIME_FORMAT.format(
                                        timestamp.atZone(ZoneId.systemDefault())
                                    ),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun FamilyConnectionFreshness.titleResource(): Int = when (this) {
    FamilyConnectionFreshness.PRIMARY_NOT_PAIRED -> R.string.family_primary_not_paired_title
    FamilyConnectionFreshness.WAITING_FOR_FIRST_SYNC -> R.string.family_waiting_sync_title
    FamilyConnectionFreshness.CURRENT -> R.string.family_connection_current_title
    FamilyConnectionFreshness.STALE -> R.string.family_connection_stale_title
}

private fun FamilyConnectionFreshness.bodyResource(): Int = when (this) {
    FamilyConnectionFreshness.PRIMARY_NOT_PAIRED -> R.string.family_primary_not_paired_body
    FamilyConnectionFreshness.WAITING_FOR_FIRST_SYNC -> R.string.family_waiting_sync_body
    FamilyConnectionFreshness.CURRENT -> R.string.family_connection_current_body
    FamilyConnectionFreshness.STALE -> R.string.family_connection_stale_body
}

private fun DoseOccurrenceStatus.statusResource(): Int = when (this) {
    DoseOccurrenceStatus.SCHEDULED -> R.string.family_occurrence_scheduled
    DoseOccurrenceStatus.DUE -> R.string.family_occurrence_due
    DoseOccurrenceStatus.SNOOZED -> R.string.family_occurrence_snoozed
    DoseOccurrenceStatus.ACKNOWLEDGED_TAKEN -> R.string.family_occurrence_acknowledged
    DoseOccurrenceStatus.NO_CONFIRMATION -> R.string.family_occurrence_no_confirmation
    DoseOccurrenceStatus.CANCELLED -> R.string.family_occurrence_cancelled
}

private fun FamilyDoseOccurrence.statusTimestamp() = when (status) {
    DoseOccurrenceStatus.ACKNOWLEDGED_TAKEN -> acknowledgedAt
    DoseOccurrenceStatus.DUE,
    DoseOccurrenceStatus.SNOOZED,
    DoseOccurrenceStatus.NO_CONFIRMATION,
    -> updatedAt

    DoseOccurrenceStatus.SCHEDULED,
    DoseOccurrenceStatus.CANCELLED,
    -> null
}

@Composable
private fun PairingInvitationDialog(
    invitation: PairingInvitation,
    onDismiss: () -> Unit,
) {
    val expiration = INVITATION_TIME_FORMAT.format(
        invitation.expiresAt.atZone(ZoneId.systemDefault())
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.family_invitation_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.family_invitation_body))
                Text(
                    text = PairingCodeNormalizer.display(invitation.code),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(stringResource(R.string.family_invitation_expires, expiration))
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 52.dp),
            ) {
                Text(stringResource(R.string.caregiver_ok))
            }
        },
    )
}

@Composable
private fun FamilyUnavailableCard() {
    FamilyCard {
        Text(
            text = stringResource(R.string.family_unavailable_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.family_unavailable_body))
    }
}

@Composable
private fun FamilyAnonymousSessionCard(onSignOut: () -> Unit) {
    FamilyCard {
        Text(stringResource(R.string.family_anonymous_session))
        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
        ) {
            Text(stringResource(R.string.family_sign_out))
        }
    }
}

@Composable
private fun FamilyCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

private fun FamilyAccessMessage.stringResource(): Int = when (this) {
    FamilyAccessMessage.INVALID_INPUT -> R.string.family_error_invalid_input
    FamilyAccessMessage.WEAK_PASSWORD -> R.string.family_error_weak_password
    FamilyAccessMessage.INVALID_CREDENTIALS -> R.string.family_error_invalid_credentials
    FamilyAccessMessage.ACCOUNT_UNAVAILABLE -> R.string.family_error_account_unavailable
    FamilyAccessMessage.NETWORK_UNAVAILABLE -> R.string.family_error_network
    FamilyAccessMessage.PERMISSION_DENIED -> R.string.family_error_permission
    FamilyAccessMessage.INVITATION_INVALID -> R.string.family_error_invitation_invalid
    FamilyAccessMessage.INVITATION_EXPIRED -> R.string.family_error_invitation_expired
    FamilyAccessMessage.INVITATION_ALREADY_USED -> R.string.family_error_invitation_used
    FamilyAccessMessage.WRONG_DEVICE_ROLE -> R.string.family_error_wrong_role
    FamilyAccessMessage.FIREBASE_NOT_CONFIGURED -> R.string.family_error_not_configured
    FamilyAccessMessage.PASSWORD_RESET_SENT -> R.string.family_password_reset_sent
    FamilyAccessMessage.UNKNOWN_FAILURE -> R.string.family_error_unknown
}

private val INVITATION_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val FAMILY_DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern(
    "d MMMM HH:mm",
    Locale.forLanguageTag("tr-TR"),
)
private val FAMILY_OCCURRENCE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern(
    "d MMM · HH:mm",
    Locale.forLanguageTag("tr-TR"),
)
private const val MAX_VISIBLE_OCCURRENCES = 12
private const val MAX_EMAIL_INPUT = 254
private const val MAX_PASSWORD_INPUT = 128
private const val MAX_LABEL_INPUT = 80
private const val MAX_CODE_INPUT = 24
