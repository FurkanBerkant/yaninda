package com.berkant.yaninda.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.berkant.yaninda.domain.family.FamilyDoseOccurrence
import com.berkant.yaninda.domain.occurrence.DoseOccurrenceStatus
import com.berkant.yaninda.ui.components.YanindaMedicationImage
import com.berkant.yaninda.ui.components.YanindaStatusPill
import com.berkant.yaninda.ui.components.YanindaStatusTone
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class AdminHistoryDay(
    val date: LocalDate,
    val doseGroups: List<AdminHistoryDoseGroup>,
)

internal data class AdminHistoryDoseGroup(
    val scheduledAt: Instant,
    val medicationNames: List<String>,
    val status: DoseOccurrenceStatus,
    val acknowledgedAt: Instant?,
    val sourceDeviceCount: Int,
)

@Composable
fun AdminHistoryScreen(
    occurrences: List<FamilyDoseOccurrence>,
) {
    val zoneId = ZoneId.systemDefault()
    val today = LocalDate.now(zoneId)
    val days = remember(occurrences, zoneId, today) {
        buildAdminHistoryDays(
            occurrences = occurrences,
            zoneId = zoneId,
            throughDate = today,
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Geçmiş",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = "Planlanan dozlar ve aileye ulaşan onaylar.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (days.isEmpty()) {
            item {
                EmptyHistoryCard()
            }
        } else {
            days.forEach { day ->
                item(key = "day-${day.date}") {
                    Text(
                        text = dayTitle(day.date, today),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .semantics { heading() },
                    )
                }

                items(
                    items = day.doseGroups,
                    key = { group ->
                        "dose-${group.scheduledAt.toEpochMilli()}"
                    },
                ) { group ->
                    HistoryDoseCard(
                        group = group,
                        zoneId = zoneId,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyHistoryCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Henüz geçmiş kaydı yok",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "İlk planlanan dozdan sonra günlük durum burada görünür.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HistoryDoseCard(
    group: AdminHistoryDoseGroup,
    zoneId: ZoneId,
) {
    val timeText = DateTimeFormatter.ofPattern("HH:mm")
        .format(group.scheduledAt.atZone(zoneId))
    val status = historyStatusPresentation(group.status)

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
            verticalAlignment = Alignment.Top,
        ) {
            YanindaMedicationImage(
                medicationName = group.medicationNames.firstOrNull().orEmpty(),
                size = 52.dp,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.titleLarge,
                )
                group.medicationNames.forEach { medicationName ->
                    Text(
                        text = medicationName,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                YanindaStatusPill(
                    text = status.first,
                    tone = status.second,
                )
                group.acknowledgedAt?.let { acknowledgedAt ->
                    Text(
                        text = "Onay saati: " +
                            DateTimeFormatter.ofPattern("HH:mm")
                                .format(acknowledgedAt.atZone(zoneId)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (group.sourceDeviceCount > 1) {
                    Text(
                        text = "${group.sourceDeviceCount} alarm telefonundan rapor geldi",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

internal fun buildAdminHistoryDays(
    occurrences: List<FamilyDoseOccurrence>,
    zoneId: ZoneId,
    throughDate: LocalDate,
): List<AdminHistoryDay> {
    val eligibleReports = occurrences.filter { occurrence ->
        !occurrence.scheduledAt.atZone(zoneId).toLocalDate().isAfter(throughDate)
    }
    if (eligibleReports.isEmpty()) return emptyList()

    val mergedLogicalOccurrences = eligibleReports
        .groupBy(FamilyDoseOccurrence::occurrenceId)
        .mapValues { (_, reports) -> mergeDeviceReports(reports) }

    val doseGroups = mergedLogicalOccurrences.values
        .groupBy(FamilyDoseOccurrence::scheduledAt)
        .map { (scheduledAt, groupOccurrences) ->
            val logicalIds = groupOccurrences.map(FamilyDoseOccurrence::occurrenceId).toSet()
            AdminHistoryDoseGroup(
                scheduledAt = scheduledAt,
                medicationNames = groupOccurrences
                    .map { it.medicationDisplayName.trim() }
                    .filter(String::isNotBlank)
                    .distinct()
                    .sorted(),
                status = selectGroupStatus(groupOccurrences.map(FamilyDoseOccurrence::status)),
                acknowledgedAt = groupOccurrences
                    .mapNotNull(FamilyDoseOccurrence::acknowledgedAt)
                    .maxOrNull(),
                sourceDeviceCount = eligibleReports
                    .asSequence()
                    .filter { it.occurrenceId in logicalIds }
                    .map(FamilyDoseOccurrence::sourceDeviceId)
                    .filter(String::isNotBlank)
                    .distinct()
                    .count(),
            )
        }

    return doseGroups
        .groupBy { group ->
            group.scheduledAt.atZone(zoneId).toLocalDate()
        }
        .map { (date, groups) ->
            AdminHistoryDay(
                date = date,
                doseGroups = groups.sortedByDescending(AdminHistoryDoseGroup::scheduledAt),
            )
        }
        .sortedByDescending(AdminHistoryDay::date)
}

private fun mergeDeviceReports(
    reports: List<FamilyDoseOccurrence>,
): FamilyDoseOccurrence {
    require(reports.isNotEmpty()) {
        "Occurrence reports cannot be empty."
    }

    return reports
        .filter { it.status == DoseOccurrenceStatus.ACKNOWLEDGED_TAKEN }
        .maxWithOrNull(
            compareBy<FamilyDoseOccurrence>(
                FamilyDoseOccurrence::updatedAt,
                FamilyDoseOccurrence::syncedAt,
            )
        )
        ?: reports.maxWithOrNull(
            compareBy<FamilyDoseOccurrence>(
                FamilyDoseOccurrence::updatedAt,
                FamilyDoseOccurrence::syncedAt,
            )
        )
        ?: reports.first()
}

private fun selectGroupStatus(
    statuses: List<DoseOccurrenceStatus>,
): DoseOccurrenceStatus = when {
    DoseOccurrenceStatus.ACKNOWLEDGED_TAKEN in statuses ->
        DoseOccurrenceStatus.ACKNOWLEDGED_TAKEN

    DoseOccurrenceStatus.DUE in statuses -> DoseOccurrenceStatus.DUE
    DoseOccurrenceStatus.SNOOZED in statuses -> DoseOccurrenceStatus.SNOOZED
    DoseOccurrenceStatus.NO_CONFIRMATION in statuses -> DoseOccurrenceStatus.NO_CONFIRMATION
    DoseOccurrenceStatus.SCHEDULED in statuses -> DoseOccurrenceStatus.SCHEDULED
    else -> DoseOccurrenceStatus.CANCELLED
}

private fun historyStatusPresentation(
    status: DoseOccurrenceStatus,
): Pair<String, YanindaStatusTone> = when (status) {
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

private fun dayTitle(
    date: LocalDate,
    today: LocalDate,
): String {
    val formatted = DateTimeFormatter
        .ofPattern("d MMMM EEEE", Locale.forLanguageTag("tr-TR"))
        .format(date)
    return when (date) {
        today -> "Bugün • $formatted"
        today.minusDays(1) -> "Dün • $formatted"
        else -> formatted
    }
}
