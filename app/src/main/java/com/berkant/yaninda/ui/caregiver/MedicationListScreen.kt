package com.berkant.yaninda.ui.caregiver

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.berkant.yaninda.R
import com.berkant.yaninda.domain.medication.MedicationConfiguration
import com.berkant.yaninda.domain.medication.MedicationDraftValidator
import com.berkant.yaninda.ui.family.AlarmDevicePairingPanelRoute
import com.berkant.yaninda.reminder.ReminderRuntimeStatus
import com.berkant.yaninda.reliability.DeviceReliabilityStatus
import com.berkant.yaninda.sync.RemoteSyncReadiness
import com.berkant.yaninda.ui.components.YanindaBottomTabs
import com.berkant.yaninda.ui.components.YanindaIconType
import com.berkant.yaninda.ui.components.YanindaIcon
import com.berkant.yaninda.ui.components.YanindaIconBadge
import com.berkant.yaninda.ui.components.YanindaListRow
import com.berkant.yaninda.ui.components.YanindaOutlinedButton
import com.berkant.yaninda.ui.components.YanindaPrimaryButton
import com.berkant.yaninda.ui.components.YanindaStatusPill
import com.berkant.yaninda.ui.components.YanindaStatusTone
import com.berkant.yaninda.ui.components.YanindaTabItem
import java.time.DayOfWeek

@Composable
internal fun MedicationListScreen(
    configurations: List<MedicationConfiguration>,
    initialSetup: Boolean,
    isWorking: Boolean,
    isReminderWorking: Boolean,
    reminderStatus: ReminderRuntimeStatus,
    deviceReliabilityStatus: DeviceReliabilityStatus,
    reminderFeedback: ReminderFeedback?,
    pendingOutboxCount: Int,
    remoteSyncReadiness: RemoteSyncReadiness,
    caregiverPhoneNumber: String,
    caregiverPhoneInvalid: Boolean,
    caregiverPhoneSaved: Boolean,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
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
    onDismissReminderFeedback: () -> Unit,
    onCompleteInitialSetup: () -> Unit,
    onLock: () -> Unit,
) {
    val title = stringResource(R.string.medication_settings_title)
    var pendingDeactivation by remember { mutableStateOf<MedicationConfiguration?>(null) }
    var selectedTab by rememberSaveable { mutableStateOf(CaregiverMainTab.MEDICATIONS) }
    var selectedSettingsPanel by rememberSaveable {
        mutableStateOf<CaregiverSettingsPanel?>(null)
    }
    val settingsTitle = stringResource(R.string.caregiver_settings_title)
    val visibleTitle = if (selectedTab == CaregiverMainTab.MEDICATIONS) title else settingsTitle
    val tabs = listOf(
        YanindaTabItem(
            label = stringResource(R.string.caregiver_tab_medications),
            icon = YanindaIconType.MEDICATION,
        ),
        YanindaTabItem(
            label = stringResource(R.string.caregiver_tab_settings),
            icon = YanindaIconType.SETTINGS,
        ),
    )
    val canCompleteInitialSetup = configurations.any { configuration ->
        configuration.medication.active && configuration.schedules.isNotEmpty()
    }

    BackHandler(
        enabled = selectedSettingsPanel != null || selectedTab != CaregiverMainTab.MEDICATIONS,
    ) {
        if (selectedSettingsPanel != null) {
            selectedSettingsPanel = null
        } else {
            selectedTab = CaregiverMainTab.MEDICATIONS
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            YanindaBottomTabs(
                items = tabs,
                selectedIndex = selectedTab.ordinal,
                onSelected = { index ->
                    selectedTab = CaregiverMainTab.entries[index]
                    selectedSettingsPanel = null
                },
                modifier = Modifier.navigationBarsPadding(),
            )
        },
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(scaffoldPadding)
                .semantics { paneTitle = visibleTitle },
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (selectedTab == CaregiverMainTab.MEDICATIONS) {
                item {
                    CaregiverHeader(
                        title = title,
                        trailingLabel = if (initialSetup) {
                            null
                        } else {
                            stringResource(R.string.caregiver_lock)
                        },
                        onTrailing = if (initialSetup) null else onLock,
                        icon = YanindaIconType.MEDICATION,
                    )
                }
                if (initialSetup) {
                    item {
                        InitialSetupCard(
                            canComplete = canCompleteInitialSetup,
                            isWorking = isWorking,
                            onComplete = onCompleteInitialSetup,
                        )
                    }
                }
                item {
                    CaregiverNotice(
                        title = stringResource(R.string.fixed_only_banner_title),
                        body = stringResource(R.string.fixed_only_banner_body),
                        isError = true,
                    )
                }
                item {
                    YanindaPrimaryButton(
                        text = stringResource(R.string.add_medication),
                        onClick = onAdd,
                        modifier = Modifier.fillMaxWidth(),
                        icon = YanindaIconType.ADD,
                        enabled = !isWorking,
                    )
                }
                if (configurations.isEmpty()) {
                    item {
                        EmptyMedicationCard()
                    }
                } else {
                    items(
                        items = configurations,
                        key = { it.medication.id },
                    ) { configuration ->
                        MedicationConfigurationCard(
                            configuration = configuration,
                            enabled = !isWorking,
                            onEdit = { onEdit(configuration.medication.id) },
                            onDeactivate = { pendingDeactivation = configuration },
                        )
                    }
                }
            } else if (selectedSettingsPanel == null) {
                item {
                    CaregiverHeader(
                        title = settingsTitle,
                        trailingLabel = if (initialSetup) {
                            null
                        } else {
                            stringResource(R.string.caregiver_lock)
                        },
                        onTrailing = if (initialSetup) null else onLock,
                        icon = YanindaIconType.SETTINGS,
                    )
                }
                item {
                    Text(
                        text = stringResource(R.string.caregiver_settings_intro),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    YanindaListRow(
                        title = stringResource(R.string.caregiver_alarm_settings_title),
                        supportingText = stringResource(R.string.caregiver_alarm_settings_body),
                        icon = YanindaIconType.ALARM,
                        onClick = { selectedSettingsPanel = CaregiverSettingsPanel.REMINDER },
                    )
                }
                item {
                    YanindaListRow(
                        title = stringResource(R.string.caregiver_samsung_settings_title),
                        supportingText = stringResource(R.string.caregiver_samsung_settings_body),
                        icon = YanindaIconType.DEVICE,
                        onClick = { selectedSettingsPanel = CaregiverSettingsPanel.SAMSUNG },
                    )
                }
                item {
                    YanindaListRow(
                        title = stringResource(R.string.caregiver_sync_settings_title),
                        supportingText = stringResource(R.string.caregiver_sync_settings_body),
                        icon = YanindaIconType.SYNC,
                        onClick = { selectedSettingsPanel = CaregiverSettingsPanel.SYNC },
                    )
                }
                item {
                    YanindaListRow(
                        title = stringResource(R.string.caregiver_pairing_settings_title),
                        supportingText = stringResource(R.string.caregiver_pairing_settings_body),
                        icon = YanindaIconType.FAMILY,
                        onClick = { selectedSettingsPanel = CaregiverSettingsPanel.PAIRING },
                    )
                }
                item {
                    YanindaListRow(
                        title = stringResource(R.string.caregiver_contact_settings_title),
                        supportingText = stringResource(R.string.caregiver_contact_settings_body),
                        icon = YanindaIconType.PHONE,
                        onClick = { selectedSettingsPanel = CaregiverSettingsPanel.CONTACT },
                    )
                }
            } else {
                val panel = checkNotNull(selectedSettingsPanel)
                item {
                    CaregiverHeader(
                        title = stringResource(panel.titleResource()),
                        onBack = { selectedSettingsPanel = null },
                        icon = panel.icon,
                    )
                }
                when (panel) {
                    CaregiverSettingsPanel.REMINDER -> item {
                        LocalReminderPanel(
                            status = reminderStatus,
                            feedback = reminderFeedback,
                            isWorking = isReminderWorking,
                            onScheduleTest = onScheduleReminderTest,
                            onCancelTest = onCancelReminderTest,
                            onRequestNotificationPermission = onRequestNotificationPermission,
                            onOpenNotificationSettings = onOpenNotificationSettings,
                            onOpenExactAlarmSettings = onOpenExactAlarmSettings,
                            onOpenFullScreenSettings = onOpenFullScreenSettings,
                            onDismissFeedback = onDismissReminderFeedback,
                        )
                    }

                    CaregiverSettingsPanel.SAMSUNG -> item {
                        SamsungReliabilityPanel(
                            status = deviceReliabilityStatus,
                            onOpenAppBatterySettings = onOpenAppBatterySettings,
                            onOpenSamsungSleepingSettings = onOpenSamsungSleepingSettings,
                        )
                    }

                    CaregiverSettingsPanel.SYNC -> item {
                        LocalSyncPanel(
                            pendingOutboxCount = pendingOutboxCount,
                            remoteSyncReadiness = remoteSyncReadiness,
                        )
                    }

                    CaregiverSettingsPanel.PAIRING -> item { AlarmDevicePairingPanelRoute() }
                    CaregiverSettingsPanel.CONTACT -> item {
                        CaregiverContactPanel(
                            phoneNumber = caregiverPhoneNumber,
                            isInvalid = caregiverPhoneInvalid,
                            isSaved = caregiverPhoneSaved,
                            isWorking = isWorking,
                            onPhoneNumberChanged = onCaregiverPhoneChanged,
                            onSave = onSaveCaregiverPhone,
                        )
                    }
                }
            }
        }
    }

    pendingDeactivation?.let { configuration ->
        AlertDialog(
            onDismissRequest = { if (!isWorking) pendingDeactivation = null },
            title = { Text(stringResource(R.string.deactivate_medication_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.deactivate_medication_body,
                        configuration.medication.displayName,
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeactivate(configuration.medication.id)
                        pendingDeactivation = null
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                    enabled = !isWorking,
                ) {
                    Text(stringResource(R.string.deactivate_medication_confirm))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { pendingDeactivation = null },
                    modifier = Modifier.heightIn(min = 48.dp),
                    enabled = !isWorking,
                ) {
                    Text(stringResource(R.string.caregiver_cancel))
                }
            },
        )
    }
}

@Composable
private fun InitialSetupCard(
    canComplete: Boolean,
    isWorking: Boolean,
    onComplete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.alarm_device_setup_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.alarm_device_setup_body),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(
                    if (canComplete) {
                        R.string.alarm_device_setup_schedule_ready
                    } else {
                        R.string.alarm_device_setup_schedule_required
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            YanindaPrimaryButton(
                text = stringResource(R.string.alarm_device_setup_complete),
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth(),
                icon = YanindaIconType.CHECK,
                enabled = canComplete && !isWorking,
            )
            Text(
                text = stringResource(R.string.alarm_device_setup_complete),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun EmptyMedicationCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.no_medications_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.no_medications_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private enum class CaregiverMainTab {
    MEDICATIONS,
    SETTINGS,
}

private enum class CaregiverSettingsPanel(
    val icon: YanindaIconType,
) {
    REMINDER(YanindaIconType.ALARM),
    SAMSUNG(YanindaIconType.DEVICE),
    SYNC(YanindaIconType.SYNC),
    PAIRING(YanindaIconType.FAMILY),
    CONTACT(YanindaIconType.PHONE),
}

private fun CaregiverSettingsPanel.titleResource(): Int = when (this) {
    CaregiverSettingsPanel.REMINDER -> R.string.caregiver_alarm_settings_title
    CaregiverSettingsPanel.SAMSUNG -> R.string.caregiver_samsung_settings_title
    CaregiverSettingsPanel.SYNC -> R.string.caregiver_sync_settings_title
    CaregiverSettingsPanel.PAIRING -> R.string.caregiver_pairing_settings_title
    CaregiverSettingsPanel.CONTACT -> R.string.caregiver_contact_settings_title
}

@Composable
private fun MedicationConfigurationCard(
    configuration: MedicationConfiguration,
    enabled: Boolean,
    onEdit: () -> Unit,
    onDeactivate: () -> Unit,
) {
    val medication = configuration.medication
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (medication.active) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                YanindaIconBadge(
                    icon = YanindaIconType.MEDICATION,
                    size = 54.dp,
                    containerColor = if (medication.active) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = medication.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = medication.dosageText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                YanindaStatusPill(
                    text = stringResource(
                        if (medication.active) {
                            R.string.medication_status_active
                        } else {
                            R.string.medication_status_inactive
                        }
                    ),
                    tone = if (medication.active) {
                        YanindaStatusTone.SUCCESS
                    } else {
                        YanindaStatusTone.NEUTRAL
                    },
                )
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    text = medication.instructionText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                )
            }
            configuration.schedules.forEach { schedule ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    YanindaIcon(
                        type = YanindaIconType.CLOCK,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = MedicationDraftValidator.formatTime(schedule.localTime),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = schedule.daysOfWeek.toTurkishDaySummary(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            val firstSchedule = configuration.schedules.firstOrNull()
            if (firstSchedule != null) {
                Text(
                    text = if (firstSchedule.snoozeEnabled) {
                        stringResource(
                            R.string.medication_snooze_enabled,
                            firstSchedule.snoozeMinutes,
                            firstSchedule.maxSnoozes,
                        )
                    } else {
                        stringResource(R.string.medication_snooze_disabled)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                YanindaOutlinedButton(
                    text = stringResource(R.string.edit_medication),
                    onClick = onEdit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    icon = YanindaIconType.SETTINGS,
                    enabled = enabled,
                    minHeight = 52.dp,
                )
                if (medication.active) {
                    YanindaOutlinedButton(
                        text = stringResource(R.string.deactivate_medication),
                        onClick = onDeactivate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                        icon = YanindaIconType.WARNING,
                        enabled = enabled,
                        minHeight = 52.dp,
                    )
                }
            }
        }
    }
}

private fun Set<DayOfWeek>.toTurkishDaySummary(): String {
    if (size == DayOfWeek.entries.size) return "Her gün"
    val labels = mapOf(
        DayOfWeek.MONDAY to "Pzt",
        DayOfWeek.TUESDAY to "Sal",
        DayOfWeek.WEDNESDAY to "Çar",
        DayOfWeek.THURSDAY to "Per",
        DayOfWeek.FRIDAY to "Cum",
        DayOfWeek.SATURDAY to "Cmt",
        DayOfWeek.SUNDAY to "Paz",
    )
    return DayOfWeek.entries.filter { it in this }.joinToString(", ") { labels.getValue(it) }
}
