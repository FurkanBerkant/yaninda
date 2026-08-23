package com.berkant.yaninda.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.berkant.yaninda.domain.family.FamilyDoseOccurrence
import com.berkant.yaninda.domain.occurrence.DoseOccurrenceStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AdminHistoryScreen(
    occurrences: List<FamilyDoseOccurrence>,
) {
    val zoneId = ZoneId.systemDefault()
    val formatter = DateTimeFormatter.ofPattern("dd MMM • HH:mm")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = 20.dp,
            vertical = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                text = "Geçmiş",
                style = MaterialTheme.typography.headlineLarge,
            )

            Text(
                text = "İlaç hatırlatmalarının ve verilen onayların geçmişi.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (occurrences.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Text(
                        text = "Henüz geçmiş kaydı yok.",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(22.dp),
                    )
                }
            }
        } else {
            occurrences
                .sortedByDescending { it.scheduledAt }
                .forEach { occurrence ->
                    item(key = occurrence.occurrenceId) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                        ) {
                            androidx.compose.foundation.layout.Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Text(
                                    text = occurrence.medicationDisplayName,
                                    style = MaterialTheme.typography.titleMedium,
                                )

                                Text(
                                    text = formatter.format(
                                        occurrence.scheduledAt.atZone(zoneId)
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                Text(
                                    text = when (occurrence.status) {
                                        DoseOccurrenceStatus.ACKNOWLEDGED_TAKEN ->
                                            "Aldığını onayladı"

                                        DoseOccurrenceStatus.NO_CONFIRMATION ->
                                            "Henüz onay yok"

                                        DoseOccurrenceStatus.DUE ->
                                            "İlaç zamanı"

                                        DoseOccurrenceStatus.SNOOZED ->
                                            "Ertelendi"

                                        DoseOccurrenceStatus.SCHEDULED ->
                                            "Planlandı"

                                        DoseOccurrenceStatus.CANCELLED ->
                                            "İptal edildi"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = when (occurrence.status) {
                                        DoseOccurrenceStatus.ACKNOWLEDGED_TAKEN ->
                                            MaterialTheme.colorScheme.tertiary

                                        DoseOccurrenceStatus.NO_CONFIRMATION,
                                        DoseOccurrenceStatus.DUE ->
                                            MaterialTheme.colorScheme.error

                                        else ->
                                            MaterialTheme.colorScheme.primary
                                    },
                                )
                            }
                        }
                    }
                }
        }
    }
}