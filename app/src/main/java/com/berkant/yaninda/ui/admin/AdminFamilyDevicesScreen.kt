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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.berkant.yaninda.domain.family.DeviceRegistration
import com.berkant.yaninda.domain.family.DeviceRole
import com.berkant.yaninda.ui.components.YanindaCard
import com.berkant.yaninda.ui.components.YanindaIconBadge
import com.berkant.yaninda.ui.components.YanindaIconType
import com.berkant.yaninda.ui.components.YanindaOutlinedButton
import com.berkant.yaninda.ui.components.YanindaStatusPill
import com.berkant.yaninda.ui.components.YanindaStatusTone
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AdminFamilyDevicesScreen(
    devices: List<DeviceRegistration>,
    onBack: () -> Unit,
) {
    BackHandler {
        onBack()
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

        item {
            Text(
                text = "Cihazlar",
                style =
                    MaterialTheme.typography
                        .headlineLarge,
            )

            Text(
                text =
                    "Yanında kurulu aile telefonlarını buradan görebilirsin. Yeni telefonda ilk açılışta kişi seçildiğinde cihaz otomatik olarak bu aileye eklenir.",
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
            )
        }
    }
}

@Composable
private fun AdminDeviceCard(
    device: DeviceRegistration,
) {
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
                text =
                    if (
                        device.lastSuccessfulSyncAt != null
                    ) {
                        "Bağlantı kuruldu"
                    } else {
                        "İlk senkronizasyon bekleniyor"
                    },
                tone =
                    if (
                        device.lastSuccessfulSyncAt != null
                    ) {
                        YanindaStatusTone.SUCCESS
                    } else {
                        YanindaStatusTone.WARNING
                    },
            )

            Text(
                text =
                    device.lastSuccessfulSyncAt
                        ?.let {
                            "Son başarılı bağlantı: ${
                                DEVICE_DATE_TIME_FORMAT.format(
                                    it.atZone(
                                        ZoneId.systemDefault()
                                    )
                                )
                            }"
                        }
                        ?: "Bu cihaz henüz başarılı bir senkronizasyon bildirmedi.",
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

private val DEVICE_DATE_TIME_FORMAT =
    DateTimeFormatter.ofPattern(
        "dd MMM HH:mm",
        Locale("tr", "TR"),
    )
