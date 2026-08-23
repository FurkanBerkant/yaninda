package com.berkant.yaninda.ui.caregiver

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.berkant.yaninda.R
import com.berkant.yaninda.YanindaApplication
import com.berkant.yaninda.notification.MedicationNotificationManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

const val CAREGIVER_PROTOTYPE_SCREEN = "caregiver"

@Composable
fun CaregiverConfigurationRoute(
    initialSetup: Boolean = false,
    onCompleteInitialSetup: suspend () -> Unit = {},
    onExitToGrandfatherHome: () -> Unit = {},
) {
    val application = LocalContext.current.applicationContext as YanindaApplication
    val factory = remember(application) {
        CaregiverViewModel.Factory(
            medicationRepository = application.medicationRepository,
            pinRepository = application.caregiverPinRepository,
            contactRepository = application.caregiverContactRepository,
            reminderCoordinator = application.reminderCoordinator,
            deviceReliabilityChecker = application.deviceReliabilityChecker,
            syncOutboxRepository = application.syncOutboxRepository,
            remoteSyncDataSource = application.remoteSyncDataSource,
        )
    }
    val viewModel: CaregiverViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    var setupCompletionFailed by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshReminderStatus()
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshReminderStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val lockAndReturnToGrandfatherHome = {
        viewModel.lock()
        onExitToGrandfatherHome()
    }

    BackHandler(enabled = state.unlocked) {
        when (
            resolveCaregiverBackAction(
                initialSetup = initialSetup,
                destination = state.destination,
            )
        ) {
            CaregiverBackAction.RETURN_TO_MEDICATION_LIST -> viewModel.returnToMedicationList()
            CaregiverBackAction.LOCK_TO_PIN -> viewModel.lock()
            CaregiverBackAction.LOCK_TO_GRANDFATHER_HOME -> lockAndReturnToGrandfatherHome()
        }
    }

    CaregiverConfigurationApp(
        state = state,
        initialSetup = initialSetup,
        onCompleteInitialSetup = {
            coroutineScope.launch {
                try {
                    onCompleteInitialSetup()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    setupCompletionFailed = true
                }
            }
        },
        onConfigurePin = viewModel::configurePin,
        onUnlock = viewModel::unlock,
        onLock = lockAndReturnToGrandfatherHome,
        onStartAdding = viewModel::startAddingMedication,
        onFixedScheduleConfirmed = viewModel::confirmFixedSchedule,
        onUnsupportedSchedule = viewModel::rejectUnsupportedSchedule,
        onEdit = viewModel::editMedication,
        onSave = viewModel::saveMedication,
        onDeactivate = viewModel::deactivateMedication,
        onCaregiverPhoneChanged = viewModel::updateCaregiverPhoneDraft,
        onSaveCaregiverPhone = viewModel::saveCaregiverPhone,
        onScheduleReminderTest = viewModel::scheduleOneMinuteTest,
        onCancelReminderTest = viewModel::cancelOneMinuteTest,
        onRequestNotificationPermission = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.openNotificationSettings()
            }
        },
        onOpenNotificationSettings = context::openNotificationSettings,
        onOpenExactAlarmSettings = context::openExactAlarmSettings,
        onOpenFullScreenSettings = context::openFullScreenSettings,
        onOpenAppBatterySettings = context::openAppBatterySettings,
        onOpenSamsungSleepingSettings = context::openSamsungSleepingSettings,
        onBackToList = viewModel::returnToMedicationList,
        onClearFormErrors = viewModel::clearFormErrors,
        onDismissReminderFeedback = viewModel::dismissReminderFeedback,
        onDismissMessage = viewModel::dismissOperationMessage,
    )

    if (setupCompletionFailed) {
        AlertDialog(
            onDismissRequest = { setupCompletionFailed = false },
            title = { Text(stringResource(R.string.caregiver_operation_error_title)) },
            text = { Text(stringResource(R.string.primary_setup_completion_failed)) },
            confirmButton = {
                Button(
                    onClick = { setupCompletionFailed = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                ) {
                    Text(stringResource(R.string.caregiver_ok))
                }
            },
        )
    }
}

@Composable
private fun CaregiverConfigurationApp(
    state: CaregiverUiState,
    initialSetup: Boolean,
    onCompleteInitialSetup: () -> Unit,
    onConfigurePin: (String, String) -> Unit,
    onUnlock: (String) -> Unit,
    onLock: () -> Unit,
    onStartAdding: () -> Unit,
    onFixedScheduleConfirmed: () -> Unit,
    onUnsupportedSchedule: () -> Unit,
    onEdit: (String) -> Unit,
    onSave: (com.berkant.yaninda.domain.medication.MedicationDraft) -> Unit,
    onDeactivate: (String) -> Unit,
    onCaregiverPhoneChanged: (String) -> Unit,
    onSaveCaregiverPhone: () -> Unit,
    onScheduleReminderTest: () -> Unit,
    onCancelReminderTest: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenFullScreenSettings: () -> Unit,
    onOpenAppBatterySettings: () -> Unit,
    onOpenSamsungSleepingSettings: () -> Unit,
    onBackToList: () -> Unit,
    onClearFormErrors: () -> Unit,
    onDismissReminderFeedback: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        when {
            state.pinConfigured == null -> CaregiverLoadingScreen()

            !state.unlocked -> CaregiverPinScreen(
                pinConfigured = state.pinConfigured,
                pinError = state.pinError,
                isWorking = state.isWorking,
                onConfigurePin = onConfigurePin,
                onUnlock = onUnlock,
            )

            state.destination == CaregiverDestination.MEDICATION_LIST -> MedicationListScreen(
                configurations = state.configurations,
                initialSetup = initialSetup,
                isWorking = state.isWorking,
                isReminderWorking = state.isReminderWorking,
                reminderStatus = state.reminderStatus,
                deviceReliabilityStatus = state.deviceReliabilityStatus,
                reminderFeedback = state.reminderFeedback,
                pendingOutboxCount = state.pendingOutboxCount,
                remoteSyncReadiness = state.remoteSyncReadiness,
                caregiverPhoneNumber = state.caregiverPhoneDraft,
                caregiverPhoneInvalid = state.caregiverPhoneInvalid,
                caregiverPhoneSaved = state.caregiverPhoneSaved,
                onAdd = onStartAdding,
                onEdit = onEdit,
                onDeactivate = onDeactivate,
                onCaregiverPhoneChanged = onCaregiverPhoneChanged,
                onSaveCaregiverPhone = onSaveCaregiverPhone,
                onScheduleReminderTest = onScheduleReminderTest,
                onCancelReminderTest = onCancelReminderTest,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onOpenNotificationSettings = onOpenNotificationSettings,
                onOpenExactAlarmSettings = onOpenExactAlarmSettings,
                onOpenFullScreenSettings = onOpenFullScreenSettings,
                onOpenAppBatterySettings = onOpenAppBatterySettings,
                onOpenSamsungSleepingSettings = onOpenSamsungSleepingSettings,
                onDismissReminderFeedback = onDismissReminderFeedback,
                onCompleteInitialSetup = onCompleteInitialSetup,
                onLock = onLock,
            )

            state.destination == CaregiverDestination.FIXED_SCHEDULE_CHECK ->
                FixedScheduleSafetyGateScreen(
                    onFixedSchedule = onFixedScheduleConfirmed,
                    onUnsupported = onUnsupportedSchedule,
                    onBack = onBackToList,
                )

            state.destination == CaregiverDestination.UNSUPPORTED_SCHEDULE ->
                UnsupportedScheduleScreen(onBack = onBackToList)

            state.destination == CaregiverDestination.MEDICATION_FORM -> {
                val selected = state.selectedMedicationId?.let { selectedId ->
                    state.configurations.firstOrNull { it.medication.id == selectedId }
                }
                if (state.selectedMedicationId != null && selected == null) {
                    MissingMedicationScreen(onBack = onBackToList)
                } else {
                    MedicationFormScreen(
                        configuration = selected,
                        errors = state.formErrors,
                        isWorking = state.isWorking,
                        onSave = onSave,
                        onBack = onBackToList,
                        onInputChanged = onClearFormErrors,
                    )
                }
            }
        }
    }

    state.operationMessage?.let { message ->
        AlertDialog(
            onDismissRequest = onDismissMessage,
            title = { Text(stringResource(R.string.caregiver_operation_error_title)) },
            text = { Text(message) },
            confirmButton = {
                Button(
                    onClick = onDismissMessage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                ) {
                    Text(stringResource(R.string.caregiver_ok))
                }
            },
        )
    }
}

private fun Context.openExactAlarmSettings() {
    val packageUri = "package:$packageName".toUri()
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
    }
    openSettings(intent, packageUri)
}

private fun Context.openNotificationSettings() {
    val packageUri = "package:$packageName".toUri()
    val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        putExtra(Settings.EXTRA_CHANNEL_ID, MedicationNotificationManager.CHANNEL_ID)
    }
    openSettings(intent, packageUri)
}

private fun Context.openFullScreenSettings() {
    val packageUri = "package:$packageName".toUri()
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
    }
    openSettings(intent, packageUri)
}

private fun Context.openAppBatterySettings() {
    val packageUri = "package:$packageName".toUri()
    openSettings(
        primaryIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri),
        packageUri = packageUri,
    )
}

private fun Context.openSamsungSleepingSettings() {
    val packageUri = "package:$packageName".toUri()
    val intent = Intent(SAMSUNG_SLEEPING_APPS_ACTION).apply {
        setPackage(SAMSUNG_DEVICE_CARE_PACKAGE)
        putExtra(SAMSUNG_ACTIVITY_TYPE_EXTRA, SAMSUNG_NEVER_SLEEPING_ACTIVITY_TYPE)
    }
    openSettings(intent, packageUri)
}

private fun Context.openSettings(
    primaryIntent: Intent,
    packageUri: Uri,
) {
    try {
        startActivity(primaryIntent)
    } catch (_: RuntimeException) {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
    }
}

private const val SAMSUNG_SLEEPING_APPS_ACTION =
    "com.samsung.android.sm.ACTION_OPEN_CHECKABLE_LISTACTIVITY"
private const val SAMSUNG_DEVICE_CARE_PACKAGE = "com.samsung.android.lool"
private const val SAMSUNG_ACTIVITY_TYPE_EXTRA = "activity_type"
private const val SAMSUNG_NEVER_SLEEPING_ACTIVITY_TYPE = 2

@Composable
private fun CaregiverLoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator()
            Text(stringResource(R.string.caregiver_loading))
        }
    }
}
