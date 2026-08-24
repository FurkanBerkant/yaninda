package com.berkant.yaninda.ui.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.berkant.yaninda.domain.family.DeviceRegistration
import com.berkant.yaninda.domain.family.DeviceRole
import com.berkant.yaninda.domain.family.PendingDeviceApproval
import com.berkant.yaninda.ui.components.YanindaCard
import com.berkant.yaninda.ui.components.YanindaIconBadge
import com.berkant.yaninda.ui.components.YanindaIconType
import com.berkant.yaninda.ui.components.YanindaOutlinedButton
import com.berkant.yaninda.ui.components.YanindaPrimaryButton
import com.berkant.yaninda.ui.components.YanindaStatusPill
import com.berkant.yaninda.ui.components.YanindaStatusTone
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AdminFamilyDevicesScreen(
    devices: List<DeviceRegistration>,
    pendingApprovals: List<PendingDeviceApproval>,
    approvingDeviceUid: String?,
    removingDeviceId: String?,
    currentDeviceId: String?,
    approvalMessage: String?,
    onApprove: (PendingDeviceApproval) -> Unit,
    onRemove: (DeviceRegistration) -> Unit,
    onBack: () -> Unit,
) {
    var approvalToConfirm by remember {
        mutableStateOf<PendingDeviceApproval?>(null)
    }
    var deviceToRemove by remember {
        mutableStateOf<DeviceRegistration?>(null)
    }

    BackHandler {
        onBack()
    }

    approvalToConfirm?.let { approval ->
        AlertDialog(
            onDismissRequest = { approvalToConfirm = null },
            title = { Text("Bu telefonu onayla?") },
            text = {
                Text(
                    when (approval.requestedRole) {
                        DeviceRole.ALARM_DEVICE ->
                            "${approval.displayName}, ilaç alarm telefonu olarak aileye eklenecek. Cihaz kodu: ${approval.deviceId.takeLast(6).uppercase(Locale.ROOT)}. Telefon fiziksel olarak yanındaysa onayla."
                        DeviceRole.ADMIN_DEVICE ->
                            "${approval.displayName}, ilaçları ve aile ayarlarını yönetebilecek. Cihaz kodu: ${approval.deviceId.takeLast(6).uppercase(Locale.ROOT)}. Telefon fiziksel olarak yanındaysa onayla."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        approvalToConfirm = null
                        onApprove(approval)
                    },
                ) {
                    Text("EVET, ONAYLA")
                }
            },
            dismissButton = {
                TextButton(onClick = { approvalToConfirm = null }) {
                    Text("VAZGEÇ")
                }
            },
        )
    }

    deviceToRemove?.let { device ->
        AlertDialog(
            onDismissRequest = { deviceToRemove = null },
            title = { Text("Bu cihazı kaldır?") },
            text = {
                Text(
                    "${device.displayName} artık aile verilerine erişemeyecek. " +
                        "Tekrar kullanmak için yeniden onaylanması gerekir."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deviceToRemove = null
                        onRemove(device)
                    },
                ) { Text("EVET, KALDIR") }
            },
            dismissButton = {
                TextButton(onClick = { deviceToRemove = null }) { Text("VAZGEÇ") }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                horizontal = 20.dp,
                vertical = 20.dp,
            ),
        verticalArrangement =
            Arrangement.spacedBy(16.dp),
    ) {
        item {
            YanindaOutlinedButton(
                text = "GERİ",
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                icon = YanindaIconType.BACK,
            )
        }

        if (pendingApprovals.isNotEmpty()) {
            item {
                Text(
                    text = "Onay bekleyen telefonlar",
                    style = MaterialTheme.typography.headlineSmall,
                )
            }

            items(
                items = pendingApprovals,
                key = { "pending-${it.uid}" },
            ) { approval ->
                PendingDeviceApprovalCard(
                    approval = approval,
                    approving = approvingDeviceUid == approval.uid,
                    onApprove = { approvalToConfirm = approval },
                )
            }
        }

        approvalMessage?.let { message ->
            item {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Text(
                text = "Cihazlar",
                style =
                    MaterialTheme.typography
                        .headlineLarge,
            )

            Text(
                text =
                    "Yanında kurulu aile telefonlarını ve onay bekleyen yeni telefonları buradan yönetebilirsin.",
                style =
                    MaterialTheme.typography
                        .bodyLarge,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
                modifier =
                    Modifier.padding(top = 6.dp),
            )
        }

        if (devices.isEmpty()) {
            item {
                YanindaCard(
                    modifier =
                        Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically,
                        horizontalArrangement =
                            Arrangement.spacedBy(14.dp),
                    ) {
                        YanindaIconBadge(
                            icon =
                                YanindaIconType.DEVICE,
                            size = 56.dp,
                        )

                        Column(
                            modifier =
                                Modifier.weight(1f),
                            verticalArrangement =
                                Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text =
                                    "Henüz kayıtlı cihaz yok",
                                style =
                                    MaterialTheme.typography
                                        .titleLarge,
                            )

                            Text(
                                text =
                                    "Diğer telefonda Yanında'yı açıp Dede, Anneanne, Berkant veya Anne seçimini yapın.",
                                style =
                                    MaterialTheme.typography
                                        .bodyMedium,
                                color =
                                    MaterialTheme.colorScheme
                                        .onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        items(
            items =
                devices.sortedWith(
                    compareBy<DeviceRegistration> {
                        it.role !=
                            DeviceRole.ALARM_DEVICE
                    }.thenBy {
                        it.displayName
                    }
                ),
            key = { it.deviceId },
        ) { device ->
            AdminDeviceCard(
                device = device,
                currentDeviceId = currentDeviceId,
                removing = removingDeviceId == device.deviceId,
                onRemove = { deviceToRemove = device },
            )
        }
    }
}

@Composable
private fun PendingDeviceApprovalCard(
    approval: PendingDeviceApproval,
    approving: Boolean,
    onApprove: () -> Unit,
) {
    YanindaCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = approval.displayName,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = when (approval.requestedRole) {
                    DeviceRole.ALARM_DEVICE -> "İlaç alarm telefonu"
                    DeviceRole.ADMIN_DEVICE -> "Yönetici telefonu"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Uygulama sürümü: ${approval.appVersion}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Cihaz kodu: ${approval.deviceId.takeLast(6).uppercase(Locale.ROOT)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            YanindaPrimaryButton(
                text = if (approving) "ONAYLANIYOR" else "BU TELEFONU ONAYLA",
                onClick = onApprove,
                enabled = !approving,
                icon = YanindaIconType.CHECK,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AdminDeviceCard(
    device: DeviceRegistration,
    currentDeviceId: String?,
    removing: Boolean,
    onRemove: () -> Unit,
) {
    val presentation = deviceStatusPresentation(
        device = device,
        currentDeviceId = currentDeviceId,
        now = Instant.now(),
    )
    YanindaCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement =
                Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.spacedBy(14.dp),
            ) {
                YanindaIconBadge(
                    icon =
                        when (device.role) {
                            DeviceRole.ALARM_DEVICE ->
                                YanindaIconType.ALARM

                            DeviceRole.ADMIN_DEVICE ->
                                YanindaIconType.PERSON
                        },
                    size = 56.dp,
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement =
                        Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = device.displayName,
                        style =
                            MaterialTheme.typography
                                .titleLarge,
                    )

                    Text(
                        text =
                            when (device.role) {
                                DeviceRole.ALARM_DEVICE ->
                                    "Alarm telefonu"

                                DeviceRole.ADMIN_DEVICE ->
                                    "Aile telefonu"
                            },
                        style =
                            MaterialTheme.typography
                                .bodyMedium,
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                    )
                }
            }

            YanindaStatusPill(
                text = presentation.label,
                tone = presentation.tone,
            )

            Text(
                text = presentation.detail,
                style =
                    MaterialTheme.typography
                        .bodyMedium,
                color =
                    MaterialTheme.colorScheme
                    .onSurfaceVariant,
            )

            if (currentDeviceId != null && device.deviceId != currentDeviceId) {
                YanindaOutlinedButton(
                    text = if (removing) "KALDIRILIYOR..." else "CİHAZI KALDIR",
                    onClick = onRemove,
                    enabled = !removing,
                    modifier = Modifier.fillMaxWidth(),
                    icon = YanindaIconType.WARNING,
                )
            }
        }
    }
}

internal data class DeviceStatusPresentation(
    val label: String,
    val detail: String,
    val tone: YanindaStatusTone,
)

internal fun deviceStatusPresentation(
    device: DeviceRegistration,
    currentDeviceId: String?,
    now: Instant,
): DeviceStatusPresentation {
    if (device.role == DeviceRole.ADMIN_DEVICE) {
        return DeviceStatusPresentation(
            label = if (device.deviceId == currentDeviceId) "Bu telefon" else "Onaylı yönetici",
            detail = "İlaçları ve aile ayarlarını yönetebilir.",
            tone = YanindaStatusTone.INFO,
        )
    }

    val lastSync = device.lastSuccessfulSyncAt
        ?: return DeviceStatusPresentation(
            label = "İlk bağlantı bekleniyor",
            detail = "Bu alarm telefonu henüz ilaç programını indirmedi.",
            tone = YanindaStatusTone.WARNING,
        )
    val stale = Duration.between(lastSync, now) > DEVICE_STALE_AFTER
    return DeviceStatusPresentation(
        label = if (stale) "Uzun süredir bağlantı yok" else "Bağlantı güncel",
        detail = "Son başarılı bağlantı: ${
            DEVICE_DATE_TIME_FORMAT.format(lastSync.atZone(ZoneId.systemDefault()))
        }",
        tone = if (stale) YanindaStatusTone.ERROR else YanindaStatusTone.SUCCESS,
    )
}

private val DEVICE_DATE_TIME_FORMAT =
    DateTimeFormatter.ofPattern(
        "dd MMM HH:mm",
        Locale.forLanguageTag("tr-TR"),
    )

private val DEVICE_STALE_AFTER: Duration = Duration.ofMinutes(30)
