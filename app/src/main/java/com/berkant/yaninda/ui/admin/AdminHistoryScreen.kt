package com.berkant.yaninda.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.berkant.yaninda.domain.family.FamilyDoseOccurrence
import com.berkant.yaninda.domain.occurrence.DoseOccurrenceStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private data class AdminHistoryGroup(
    val scheduledAt: Instant,
    val medicationNames: List<String>,
    val status: DoseOccurrenceStatus,
    val sourceDeviceCount: Int,
)

@Composable
fun AdminHistoryScreen(
    occurrences: List<FamilyDoseOccurrence>,
) {
    val zoneId =
        ZoneId.systemDefault()

    val formatter =
        DateTimeFormatter.ofPattern(
            "dd MMM • HH:mm"
        )

    val historyGroups =
        remember(occurrences) {
            buildHistoryGroups(
                occurrences = occurrences
            )
        }

    LazyColumn(
        modifier =
            Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                horizontal = 20.dp,
                vertical = 24.dp,
            ),
        verticalArrangement =
            Arrangement.spacedBy(14.dp),
    ) {

        item {
            Column(
                modifier =
                    Modifier.fillMaxWidth(),
                verticalArrangement =
                    Arrangement.spacedBy(4.dp),
            ) {

                Text(
                    text = "Geçmiş",
                    style =
                        MaterialTheme.typography
                            .headlineLarge,
                )

                Text(
                    text =
                        "İlaç hatırlatmalarının ve verilen onayların geçmişi.",
                    style =
                        MaterialTheme.typography
                            .bodyLarge,
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant,
                )
            }
        }

        if (historyGroups.isEmpty()) {

            item {
                EmptyHistoryCard()
            }

        } else {

            items(
                items = historyGroups,
                key = { group ->
                    group.scheduledAt
                        .toEpochMilli()
                },
            ) { group ->

                HistoryGroupCard(
                    group = group,
                    formattedTime =
                        formatter.format(
                            group.scheduledAt
                                .atZone(zoneId)
                        ),
                )
            }
        }
    }
}

@Composable
private fun EmptyHistoryCard() {
    Card(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor =
                    MaterialTheme
                        .colorScheme
                        .surface,
            ),
    ) {

        Text(
            text =
                "Henüz geçmiş kaydı yok.",
            style =
                MaterialTheme.typography
                    .titleMedium,
            modifier =
                Modifier.padding(22.dp),
        )
    }
}

@Composable
private fun HistoryGroupCard(
    group: AdminHistoryGroup,
    formattedTime: String,
) {
    Card(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor =
                    MaterialTheme
                        .colorScheme
                        .surface,
            ),
    ) {

        Column(
            modifier =
                Modifier.padding(18.dp),
            verticalArrangement =
                Arrangement.spacedBy(6.dp),
        ) {

            group.medicationNames
                .forEach { medicationName ->

                    Text(
                        text = medicationName,
                        style =
                            MaterialTheme.typography
                                .titleMedium,
                    )
                }

            Text(
                text = formattedTime,
                style =
                    MaterialTheme.typography
                        .bodyMedium,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant,
            )

            Text(
                text =
                    statusText(
                        group.status
                    ),
                style =
                    MaterialTheme.typography
                        .labelLarge,
                color =
                    statusColor(
                        group.status
                    ),
            )

            /*
             * Bu bilgi kullanıcıya cihaz isimleriyle
             * teknik detay vermeden, birden fazla alarm
             * telefonundan rapor geldiğini gösterebilir.
             *
             * Tek cihaz varsa hiç gösterilmez.
             */
            if (
                group.sourceDeviceCount > 1
            ) {

                Text(
                    text =
                        "${group.sourceDeviceCount} alarm telefonundan raporlandı",
                    style =
                        MaterialTheme.typography
                            .bodySmall,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                )
            }
        }
    }
}

private fun buildHistoryGroups(
    occurrences: List<FamilyDoseOccurrence>,
): List<AdminHistoryGroup> {

    if (occurrences.isEmpty()) {
        return emptyList()
    }

    /*
     * 1)
     * Aynı logical occurrence farklı ALARM_DEVICE
     * telefonlarından gelebilir.
     *
     * Örnek:
     *
     * device-dede--abc
     * device-nine--abc
     *
     * Firestore'da iki ayrı report vardır ama
     * occurrenceId ikisinde de "abc" olur.
     *
     * Önce bunları logical occurrence seviyesinde
     * tek kayda indiriyoruz.
     */
    val logicalOccurrences =
        occurrences
            .groupBy(
                FamilyDoseOccurrence::occurrenceId
            )
            .map { (_, reports) ->
                mergeDeviceReports(
                    reports = reports
                )
            }

    /*
     * 2)
     * Roadmap 8 nedeniyle aynı scheduledAt
     * zamanındaki farklı ilaçlar tek dose group.
     *
     * Örnek:
     *
     * 08:00 Beloc
     * 08:00 Coraspin
     * 08:00 Vitamin D
     *
     * Admin geçmişinde üç ayrı kart yerine
     * tek kart gösteriyoruz.
     */
    return logicalOccurrences
        .groupBy(
            FamilyDoseOccurrence::scheduledAt
        )
        .map { (scheduledAt, groupOccurrences) ->

            val medicationNames =
                groupOccurrences
                    .map {
                        it.medicationDisplayName
                            .trim()
                    }
                    .filter {
                        it.isNotBlank()
                    }
                    .distinct()
                    .sorted()

            val status =
                selectGroupStatus(
                    groupOccurrences.map {
                        it.status
                    }
                )

            val sourceDeviceCount =
                occurrences
                    .asSequence()
                    .filter {
                        it.scheduledAt ==
                                scheduledAt
                    }
                    .map {
                        it.sourceDeviceId
                    }
                    .filter {
                        it.isNotBlank()
                    }
                    .distinct()
                    .count()

            AdminHistoryGroup(
                scheduledAt =
                    scheduledAt,
                medicationNames =
                    medicationNames,
                status =
                    status,
                sourceDeviceCount =
                    sourceDeviceCount,
            )
        }
        .sortedByDescending {
            it.scheduledAt
        }
}

private fun mergeDeviceReports(
    reports: List<FamilyDoseOccurrence>,
): FamilyDoseOccurrence {

    require(reports.isNotEmpty()) {
        "Occurrence reports cannot be empty."
    }

    /*
     * Bir alarm telefonunda "aldım" denmiş,
     * diğer telefonda yalnızca alarm çalmış olabilir.
     *
     * Aynı logical occurrence için herhangi bir
     * ALARM_DEVICE ACKNOWLEDGED_TAKEN raporladıysa
     * admin tarafında bunu alınmış kabul ediyoruz.
     *
     * Çünkü diğer alarm telefonunda butona ayrıca
     * basılmaması beklenen bir davranış.
     */
    val acknowledged =
        reports
            .filter {
                it.status ==
                        DoseOccurrenceStatus
                            .ACKNOWLEDGED_TAKEN
            }
            .maxByOrNull {
                it.updatedAt
            }

    if (acknowledged != null) {
        return acknowledged
    }

    /*
     * ACK yoksa en güncel report'u kullan.
     *
     * version tek başına yeterli değil çünkü
     * farklı ALARM_DEVICE'ların version sayıları
     * birbirinden bağımsız ilerleyebilir.
     */
    return reports
        .maxWithOrNull(
            compareBy<FamilyDoseOccurrence>(
                FamilyDoseOccurrence::updatedAt,
                FamilyDoseOccurrence::syncedAt,
            )
        )
        ?: reports.first()
}

private fun selectGroupStatus(
    statuses: List<DoseOccurrenceStatus>,
): DoseOccurrenceStatus {

    /*
     * Aynı dose group içindeki ilaçların normalde
     * aynı lifecycle durumunda olması gerekir.
     *
     * Yine de cloud'da geçici olarak farklı report
     * seviyeleri görülebileceği için UI deterministik
     * bir precedence kullanıyor.
     */

    if (
        DoseOccurrenceStatus
            .ACKNOWLEDGED_TAKEN in statuses
    ) {
        return DoseOccurrenceStatus
            .ACKNOWLEDGED_TAKEN
    }

    if (
        DoseOccurrenceStatus.DUE in statuses
    ) {
        return DoseOccurrenceStatus.DUE
    }

    if (
        DoseOccurrenceStatus.SNOOZED in statuses
    ) {
        return DoseOccurrenceStatus.SNOOZED
    }

    if (
        DoseOccurrenceStatus
            .NO_CONFIRMATION in statuses
    ) {
        return DoseOccurrenceStatus
            .NO_CONFIRMATION
    }

    if (
        DoseOccurrenceStatus.SCHEDULED
        in statuses
    ) {
        return DoseOccurrenceStatus.SCHEDULED
    }

    return DoseOccurrenceStatus.CANCELLED
}

@Composable
private fun statusColor(
    status: DoseOccurrenceStatus,
) =
    when (status) {

        DoseOccurrenceStatus
            .ACKNOWLEDGED_TAKEN ->
            MaterialTheme
                .colorScheme
                .tertiary

        DoseOccurrenceStatus
            .NO_CONFIRMATION,
        DoseOccurrenceStatus.DUE ->
            MaterialTheme
                .colorScheme
                .error

        else ->
            MaterialTheme
                .colorScheme
                .primary
    }

private fun statusText(
    status: DoseOccurrenceStatus,
): String =
    when (status) {

        DoseOccurrenceStatus
            .ACKNOWLEDGED_TAKEN ->
            "Aldığını onayladı"

        DoseOccurrenceStatus
            .NO_CONFIRMATION ->
            "Onay alınamadı"

        DoseOccurrenceStatus.DUE ->
            "İlaç zamanı"

        DoseOccurrenceStatus.SNOOZED ->
            "Ertelendi"

        DoseOccurrenceStatus.SCHEDULED ->
            "Planlandı"

        DoseOccurrenceStatus.CANCELLED ->
            "İptal edildi"
    }