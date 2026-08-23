package com.berkant.yaninda.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import com.berkant.yaninda.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.berkant.yaninda.domain.family.DeviceRegistration
import com.berkant.yaninda.domain.family.FamilyDoseOccurrence
import com.berkant.yaninda.domain.occurrence.DoseOccurrenceStatus
import com.berkant.yaninda.domain.medication.DayOfWeekMask
import com.berkant.yaninda.schedule.PublishedMedication
import com.berkant.yaninda.schedule.PublishedMedicationSchedule
import com.berkant.yaninda.schedule.PublishedScheduleVersion
import com.berkant.yaninda.ui.components.YanindaIcon
import com.berkant.yaninda.ui.components.YanindaIconBadge
import com.berkant.yaninda.ui.components.YanindaIconType
import com.berkant.yaninda.ui.components.YanindaMedicationImage
import com.berkant.yaninda.ui.components.YanindaStatusPill
import com.berkant.yaninda.ui.components.YanindaStatusTone
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.berkant.yaninda.domain.family.DeviceRole
@Composable
fun AdminDashboardScreen(
    occurrences: List<FamilyDoseOccurrence>,
    devices: List<DeviceRegistration>,
    schedule: PublishedScheduleVersion?,
) {
    val zoneId = ZoneId.systemDefault()
    val today = java.time.LocalDate.now(zoneId).dayOfWeek
    val alarmDevices = devices.filter {
        it.role == DeviceRole.ALARM_DEVICE
    }
    val nextOccurrence = occurrences
        .filter {
            it.status == DoseOccurrenceStatus.SCHEDULED ||
                    it.status == DoseOccurrenceStatus.DUE ||
                    it.status == DoseOccurrenceStatus.SNOOZED
        }
        .minByOrNull { it.scheduledAt }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = 20.dp,
            vertical = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = "Dede Takip",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        item {
            ProfileOverviewCard(
                isConnected = alarmDevices.isNotEmpty(),
            )
        }

        item {
            SectionTitle("Bugünün ilaçları")
        }

        val todayMedications = schedule?.medications.orEmpty()
            .filter(PublishedMedication::active)
            .flatMap { medication ->
                medication.schedules
                    .filter { today in DayOfWeekMask.decode(it.daysOfWeekMask) }
                    .map { medication to it }
            }
            .sortedBy { it.second.localTimeMinutes }

        if (todayMedications.isEmpty() && occurrences.isEmpty()) {
            item {
                EmptyCard(
                    icon = YanindaIconType.MEDICATION,
                    title = "Henüz ilaç kaydı yok",
                    body = "İlaç programı oluşturulduğunda bugünkü durum burada görünecek.",
                )
            }
        } else {
            todayMedications.forEach { (medication, medicationSchedule) ->
                item {
                    PublishedDoseCard(
                        medication = medication,
                        schedule = medicationSchedule,
                        occurrence = occurrences.firstOrNull { occurrence ->
                            occurrence.medicationDisplayName == medication.displayName &&
                                occurrence.scheduledAt.atZone(zoneId).toLocalTime().hour ==
                                medicationSchedule.localTimeMinutes / 60 &&
                                occurrence.scheduledAt.atZone(zoneId).toLocalTime().minute ==
                                medicationSchedule.localTimeMinutes % 60
                        },
                        zoneId = zoneId,
                    )
                }
            }

            if (todayMedications.isEmpty()) {
                nextOccurrence?.let { occurrence ->
                    item {
                        DoseCard(
                            label = "Sıradaki",
                            occurrence = occurrence,
                            zoneId = zoneId,
                        )
                    }
                }
            }
        }

        item {
            SectionTitle("Son konum")
            EmptyCard(
                icon = YanindaIconType.DEVICE,
                title = "Konum bilgisi yok",
                body = "Konum özelliği bu sürümde henüz etkin değil.",
            )
        }
    }
}

@Composable
private fun ProfileOverviewCard(
    isConnected: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.yaninda_dede_asset),
                contentDescription = "Dede profil fotoğrafı",
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Dede",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "Köyde yaşıyor",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            YanindaStatusPill(
                text = if (isConnected) "Çevrimiçi" else "Bağlantı bekleniyor",
                tone = if (isConnected) {
                    YanindaStatusTone.SUCCESS
                } else {
                    YanindaStatusTone.WARNING
                },
            )
        }
    }
}

@Composable
private fun PublishedDoseCard(
    medication: PublishedMedication,
    schedule: PublishedMedicationSchedule,
    occurrence: FamilyDoseOccurrence?,
    zoneId: ZoneId,
) {
    val hour = schedule.localTimeMinutes / 60
    val minute = schedule.localTimeMinutes % 60
    val statusText = when (occurrence?.status) {
        DoseOccurrenceStatus.ACKNOWLEDGED_TAKEN -> "Aldığını onayladı"
        DoseOccurrenceStatus.DUE -> "İlaç zamanı"
        DoseOccurrenceStatus.SNOOZED -> "Ertelendi"
        DoseOccurrenceStatus.NO_CONFIRMATION -> "Henüz onay yok"
        DoseOccurrenceStatus.CANCELLED -> "İptal edildi"
        DoseOccurrenceStatus.SCHEDULED, null -> "Planlandı"
    }
    val statusTone = when (occurrence?.status) {
        DoseOccurrenceStatus.ACKNOWLEDGED_TAKEN -> YanindaStatusTone.SUCCESS
        DoseOccurrenceStatus.DUE,
        DoseOccurrenceStatus.NO_CONFIRMATION,
        -> YanindaStatusTone.WARNING
        else -> YanindaStatusTone.INFO
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YanindaMedicationImage(
                medicationName = medication.displayName,
                size = 50.dp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "%02d:%02d".format(hour, minute),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(text = medication.displayName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = medication.dosageText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            YanindaStatusPill(text = statusText, tone = statusTone)
        }
    }
}

@Composable
private fun StatusOverviewCard(
    devices: List<DeviceRegistration>,
) {
    val alarmDeviceCount = devices.size

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            YanindaIconBadge(
                icon = YanindaIconType.FAMILY,
                size = 56.dp,
            )

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = if (alarmDeviceCount > 0) {
                        "Aile bağlantısı aktif"
                    } else {
                        "Kurulum devam ediyor"
                    },
                    style = MaterialTheme.typography.titleLarge,
                )

                Text(
                    text = if (alarmDeviceCount > 0) {
                        "$alarmDeviceCount alarm telefonu bağlı"
                    } else {
                        "İlk alarm telefonunu bağlayarak devam et."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun DoseCard(
    label: String,
    occurrence: FamilyDoseOccurrence,
    zoneId: ZoneId,
) {
    val time = DateTimeFormatter.ofPattern("HH:mm")
        .format(occurrence.scheduledAt.atZone(zoneId))

    val (statusText, tone) = when (occurrence.status) {
        DoseOccurrenceStatus.ACKNOWLEDGED_TAKEN ->
            "Aldığını onayladı" to YanindaStatusTone.SUCCESS

        DoseOccurrenceStatus.NO_CONFIRMATION ->
            "Henüz onay yok" to YanindaStatusTone.WARNING

        DoseOccurrenceStatus.DUE ->
            "İlaç zamanı" to YanindaStatusTone.WARNING

        DoseOccurrenceStatus.SNOOZED ->
            "Ertelendi" to YanindaStatusTone.INFO

        DoseOccurrenceStatus.SCHEDULED ->
            "Planlandı" to YanindaStatusTone.INFO

        DoseOccurrenceStatus.CANCELLED ->
            "İptal edildi" to YanindaStatusTone.NEUTRAL
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YanindaIconBadge(
                icon = YanindaIconType.MEDICATION,
                size = 50.dp,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = time,
                    style = MaterialTheme.typography.titleLarge,
                )

                Text(
                    text = occurrence.medicationDisplayName,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            YanindaStatusPill(
                text = statusText,
                tone = tone,
            )
        }
    }
}

@Composable
private fun DeviceCard(
    device: DeviceRegistration,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            YanindaIcon(
                type = YanindaIconType.DEVICE,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = device.displayName,
                    style = MaterialTheme.typography.titleMedium,
                )

                Text(
                    text = if (device.lastSuccessfulSyncAt != null) {
                        "Bağlantı kuruldu"
                    } else {
                        "İlk senkronizasyon bekleniyor"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            YanindaStatusPill(
                text = if (device.lastSuccessfulSyncAt != null) {
                    "Bağlı"
                } else {
                    "Bekliyor"
                },
                tone = if (device.lastSuccessfulSyncAt != null) {
                    YanindaStatusTone.SUCCESS
                } else {
                    YanindaStatusTone.WARNING
                },
            )
        }
    }
}

@Composable
private fun EmptyCard(
    icon: YanindaIconType,
    title: String,
    body: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YanindaIconBadge(
                icon = icon,
                size = 52.dp,
            )

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
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

@Composable
private fun SectionTitle(
    text: String,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
    )
}