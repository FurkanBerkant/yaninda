package com.berkant.yaninda.ui.admin

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.berkant.yaninda.R
import com.berkant.yaninda.domain.family.DeviceRegistration
import com.berkant.yaninda.domain.family.DeviceRole
import com.berkant.yaninda.domain.family.FamilyConnectionFreshness
import com.berkant.yaninda.domain.family.FamilyDoseOccurrence
import com.berkant.yaninda.domain.family.FamilyMonitoringPolicy
import com.berkant.yaninda.domain.medication.DayOfWeekMask
import com.berkant.yaninda.domain.occurrence.DoseOccurrenceStatus
import com.berkant.yaninda.schedule.PublishedMedication
import com.berkant.yaninda.schedule.PublishedMedicationSchedule
import com.berkant.yaninda.schedule.PublishedScheduleVersion
import com.berkant.yaninda.ui.components.YanindaMedicationImage
import com.berkant.yaninda.ui.components.YanindaStatusPill
import com.berkant.yaninda.ui.components.YanindaStatusTone
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class ConnectionPresentation(
    val label: String,
    val supportingText: String,
    val tone: YanindaStatusTone,
)

@Composable
fun AdminDashboardScreen(
    occurrences: List<FamilyDoseOccurrence>,
    devices: List<DeviceRegistration>,
    schedule: PublishedScheduleVersion?,
) {
    val zoneId = ZoneId.systemDefault()
    val todayDate = LocalDate.now(zoneId)
    val alarmDevices = devices.filter { it.role == DeviceRole.ALARM_DEVICE }
    val connection = connectionPresentation(
        alarmDevices = alarmDevices,
        now = Instant.now(),
        zoneId = zoneId,
    )
    val todayMedications = schedule?.medications.orEmpty()
        .filter(PublishedMedication::active)
        .flatMap { medication ->
            medication.schedules
                .filter { todayDate.dayOfWeek in DayOfWeekMask.decode(it.daysOfWeekMask) }
                .map { medication to it }
        }
        .sortedBy { it.second.localTimeMinutes }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "Dede Takip",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = DateTimeFormatter
                        .ofPattern("d MMMM EEEE", Locale.forLanguageTag("tr-TR"))
                        .format(todayDate),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            ProfileOverviewCard(connection = connection)
        }

        item {
            Text(
                text = "Bugünün ilaçları",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
        }

        if (todayMedications.isEmpty()) {
            item {
                EmptyCard(
                    title = if (schedule?.medications.isNullOrEmpty()) {
                        "Henüz ilaç programı yok"
                    } else {
                        "Bugün için ilaç yok"
                    },
                    body = if (schedule?.medications.isNullOrEmpty()) {
                        "İlaç programı oluşturulduğunda günlük durum burada görünür."
                    } else {
                        "Bugün planlanmış bir ilaç bulunmuyor."
                    },
                )
            }
        } else {
            todayMedications.forEach { (medication, medicationSchedule) ->
                item(
                    key = "${medication.medicationId}-${medicationSchedule.scheduleId}",
                ) {
                    PublishedDoseCard(
                        medication = medication,
                        schedule = medicationSchedule,
                        occurrence = selectOccurrenceForPublishedDose(
                            occurrences = occurrences,
                            medicationDisplayName = medication.displayName,
                            scheduledDate = todayDate,
                            localTimeMinutes = medicationSchedule.localTimeMinutes,
                            zoneId = zoneId,
                        ),
                        zoneId = zoneId,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileOverviewCard(
    connection: ConnectionPresentation,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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
                contentDescription = "Dede profil görseli",
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "Dede",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = connection.supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            YanindaStatusPill(
                text = connection.label,
                tone = connection.tone,
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
    val hour = schedule.localTimeMinutes / MINUTES_PER_HOUR
    val minute = schedule.localTimeMinutes % MINUTES_PER_HOUR
    val status = doseStatusPresentation(occurrence, zoneId)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YanindaMedicationImage(
                medicationName = medication.displayName,
                size = 54.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "%02d:%02d".format(hour, minute),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = medication.displayName,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (medication.dosageText.isNotBlank()) {
                    Text(
                        text = medication.dosageText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                status.detail?.let { detail ->
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            YanindaStatusPill(
                text = status.label,
                tone = status.tone,
            )
        }
    }
}

private data class DoseStatusPresentation(
    val label: String,
    val tone: YanindaStatusTone,
    val detail: String? = null,
)

private fun doseStatusPresentation(
    occurrence: FamilyDoseOccurrence?,
    zoneId: ZoneId,
): DoseStatusPresentation = when (occurrence?.status) {
    DoseOccurrenceStatus.ACKNOWLEDGED_TAKEN -> DoseStatusPresentation(
        label = "Aldığını onayladı",
        tone = YanindaStatusTone.SUCCESS,
        detail = occurrence.acknowledgedAt?.let { acknowledgedAt ->
            "Onay saati: " +
                DateTimeFormatter.ofPattern("HH:mm").format(acknowledgedAt.atZone(zoneId))
        },
    )

    DoseOccurrenceStatus.DUE -> DoseStatusPresentation(
        label = "İlaç zamanı",
        tone = YanindaStatusTone.WARNING,
    )

    DoseOccurrenceStatus.SNOOZED -> DoseStatusPresentation(
        label = "Ertelendi",
        tone = YanindaStatusTone.INFO,
    )

    DoseOccurrenceStatus.NO_CONFIRMATION -> DoseStatusPresentation(
        label = "Onay yok",
        tone = YanindaStatusTone.WARNING,
    )

    DoseOccurrenceStatus.CANCELLED -> DoseStatusPresentation(
        label = "İptal",
        tone = YanindaStatusTone.NEUTRAL,
    )

    DoseOccurrenceStatus.SCHEDULED,
    null,
    -> DoseStatusPresentation(
        label = "Planlandı",
        tone = YanindaStatusTone.INFO,
    )
}

@Composable
private fun EmptyCard(
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
            YanindaMedicationImage(
                medicationName = "Yanında",
                size = 54.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
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

private fun connectionPresentation(
    alarmDevices: List<DeviceRegistration>,
    now: Instant,
    zoneId: ZoneId,
): ConnectionPresentation {
    val freshestDevice = alarmDevices.maxByOrNull {
        it.lastSuccessfulSyncAt ?: Instant.MIN
    }
    val status = FamilyMonitoringPolicy().evaluate(
        device = freshestDevice,
        now = now,
    )
    val devicePrefix = when (alarmDevices.size) {
        0 -> "Alarm telefonu kayıtlı değil"
        1 -> "1 alarm telefonu"
        else -> "${alarmDevices.size} alarm telefonu"
    }
    val lastSync = status.lastSuccessfulSyncAt?.let { instant ->
        DateTimeFormatter.ofPattern("HH:mm").format(instant.atZone(zoneId))
    }

    return when (status.freshness) {
        FamilyConnectionFreshness.ALARM_DEVICE_NOT_PAIRED -> ConnectionPresentation(
            label = "Cihaz bekleniyor",
            supportingText = devicePrefix,
            tone = YanindaStatusTone.WARNING,
        )

        FamilyConnectionFreshness.WAITING_FOR_FIRST_SYNC -> ConnectionPresentation(
            label = "İlk bağlantı",
            supportingText = "$devicePrefix • İlk senkronizasyon bekleniyor",
            tone = YanindaStatusTone.WARNING,
        )

        FamilyConnectionFreshness.CURRENT -> ConnectionPresentation(
            label = "Güncel",
            supportingText = "$devicePrefix • Son bağlantı $lastSync",
            tone = YanindaStatusTone.SUCCESS,
        )

        FamilyConnectionFreshness.STALE -> ConnectionPresentation(
            label = "Bağlantı eski",
            supportingText = "$devicePrefix • Son bağlantı $lastSync",
            tone = YanindaStatusTone.WARNING,
        )
    }
}

internal fun selectOccurrenceForPublishedDose(
    occurrences: List<FamilyDoseOccurrence>,
    medicationDisplayName: String,
    scheduledDate: LocalDate,
    localTimeMinutes: Int,
    zoneId: ZoneId,
): FamilyDoseOccurrence? {
    require(localTimeMinutes in 0 until MINUTES_PER_DAY) {
        "Scheduled local time is outside the day."
    }

    val matchingReports = occurrences.filter { occurrence ->
        val scheduled = occurrence.scheduledAt.atZone(zoneId)

        occurrence.medicationDisplayName == medicationDisplayName &&
            scheduled.toLocalDate() == scheduledDate &&
            scheduled.hour * MINUTES_PER_HOUR + scheduled.minute == localTimeMinutes
    }

    return matchingReports
        .filter { it.status == DoseOccurrenceStatus.ACKNOWLEDGED_TAKEN }
        .maxWithOrNull(
            compareBy<FamilyDoseOccurrence>(
                FamilyDoseOccurrence::updatedAt,
                FamilyDoseOccurrence::syncedAt,
            )
        )
        ?: matchingReports.maxWithOrNull(
            compareBy<FamilyDoseOccurrence>(
                FamilyDoseOccurrence::updatedAt,
                FamilyDoseOccurrence::syncedAt,
            )
        )
}

private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
