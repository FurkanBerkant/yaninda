package com.berkant.yaninda.ui.caregiver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.berkant.yaninda.R
import com.berkant.yaninda.reliability.BatteryOptimizationState
import com.berkant.yaninda.reliability.DeviceReliabilityStatus
import com.berkant.yaninda.reliability.ReliabilityCheckState
import com.berkant.yaninda.ui.components.YanindaCard
import com.berkant.yaninda.ui.components.YanindaIconType
import com.berkant.yaninda.ui.components.YanindaInfoRow
import com.berkant.yaninda.ui.components.YanindaOutlinedButton
import com.berkant.yaninda.ui.components.YanindaSectionTitle
import com.berkant.yaninda.ui.components.YanindaStatusTone

@Composable
internal fun SamsungReliabilityPanel(
    status: DeviceReliabilityStatus,
    onOpenAppBatterySettings: () -> Unit,
    onOpenSamsungSleepingSettings: () -> Unit,
) {
    YanindaCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            YanindaSectionTitle(
                title = stringResource(R.string.samsung_reliability_title),
                icon = YanindaIconType.DEVICE,
            )
            Text(
                text = stringResource(R.string.samsung_reliability_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            YanindaInfoRow(
                label = stringResource(R.string.device_manufacturer_status_label),
                value = when (status.isSamsungDevice) {
                    null -> stringResource(R.string.reminder_status_checking)
                    true -> stringResource(R.string.device_manufacturer_samsung)
                    false -> stringResource(R.string.device_manufacturer_not_samsung)
                },
                icon = YanindaIconType.DEVICE,
                tone = when (status.isSamsungDevice) {
                    true -> YanindaStatusTone.SUCCESS
                    false -> YanindaStatusTone.NEUTRAL
                    null -> YanindaStatusTone.INFO
                },
            )
            YanindaInfoRow(
                label = stringResource(R.string.power_save_status_label),
                value = powerSaveStatusText(status.powerSaveMode),
                icon = YanindaIconType.ALARM,
                tone = status.powerSaveMode.statusTone(),
            )
            YanindaInfoRow(
                label = stringResource(R.string.battery_optimization_status_label),
                value = batteryOptimizationStatusText(status.batteryOptimization),
                icon = YanindaIconType.DEVICE,
                tone = status.batteryOptimization.statusTone(),
            )

            if (status.powerSaveMode == ReliabilityCheckState.ENABLED) {
                Text(
                    text = stringResource(R.string.power_save_test_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Text(
                text = stringResource(R.string.samsung_sleeping_guidance),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.samsung_sleeping_manual_notice),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (status.isSamsungDevice == true) {
                YanindaOutlinedButton(
                    text = stringResource(R.string.open_samsung_sleeping_settings),
                    onClick = onOpenSamsungSleepingSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    icon = YanindaIconType.SETTINGS,
                )
            }
            YanindaOutlinedButton(
                text = stringResource(R.string.open_app_battery_settings),
                onClick = onOpenAppBatterySettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                icon = YanindaIconType.DEVICE,
            )
        }
    }
}

private fun ReliabilityCheckState.statusTone(): YanindaStatusTone = when (this) {
    ReliabilityCheckState.DISABLED -> YanindaStatusTone.SUCCESS
    ReliabilityCheckState.ENABLED -> YanindaStatusTone.WARNING
    ReliabilityCheckState.NOT_CHECKED -> YanindaStatusTone.INFO
    ReliabilityCheckState.CHECK_FAILED -> YanindaStatusTone.ERROR
}

private fun BatteryOptimizationState.statusTone(): YanindaStatusTone = when (this) {
    BatteryOptimizationState.EXEMPT -> YanindaStatusTone.SUCCESS
    BatteryOptimizationState.SYSTEM_MANAGED -> YanindaStatusTone.WARNING
    BatteryOptimizationState.NOT_CHECKED -> YanindaStatusTone.INFO
    BatteryOptimizationState.CHECK_FAILED -> YanindaStatusTone.ERROR
}

@Composable
private fun powerSaveStatusText(state: ReliabilityCheckState): String = stringResource(
    when (state) {
        ReliabilityCheckState.NOT_CHECKED -> R.string.reminder_status_checking
        ReliabilityCheckState.ENABLED -> R.string.power_save_enabled
        ReliabilityCheckState.DISABLED -> R.string.power_save_disabled
        ReliabilityCheckState.CHECK_FAILED -> R.string.reminder_status_check_failed
    }
)

@Composable
private fun batteryOptimizationStatusText(state: BatteryOptimizationState): String =
    stringResource(
        when (state) {
            BatteryOptimizationState.NOT_CHECKED -> R.string.reminder_status_checking
            BatteryOptimizationState.EXEMPT -> R.string.battery_optimization_exempt
            BatteryOptimizationState.SYSTEM_MANAGED ->
                R.string.battery_optimization_system_managed

            BatteryOptimizationState.CHECK_FAILED -> R.string.reminder_status_check_failed
        }
    )
