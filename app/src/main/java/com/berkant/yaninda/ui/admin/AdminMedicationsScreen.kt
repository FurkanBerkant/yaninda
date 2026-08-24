package com.berkant.yaninda.ui.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.berkant.yaninda.YanindaApplication
import com.berkant.yaninda.schedule.PublishedMedication
import com.berkant.yaninda.schedule.PublishedScheduleVersion
import com.berkant.yaninda.ui.caregiver.MedicationFormScreen
import com.berkant.yaninda.domain.medication.DayOfWeekMask
import com.berkant.yaninda.domain.medication.Medication
import com.berkant.yaninda.domain.medication.MedicationConfiguration
import com.berkant.yaninda.domain.medication.MedicationSchedule
import com.berkant.yaninda.domain.medication.MedicationScheduleType
import com.berkant.yaninda.ui.components.YanindaIconBadge
import com.berkant.yaninda.ui.components.YanindaIconType
import com.berkant.yaninda.ui.components.YanindaIcon
import com.berkant.yaninda.ui.components.YanindaMedicationImage
import com.berkant.yaninda.ui.components.YanindaStatusPill
import com.berkant.yaninda.ui.components.YanindaStatusTone
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

private enum class AdminMedicationPage {
    LIST,
    FORM,
}

@Composable
fun AdminMedicationsRoute(
    familyId: String?,
) {
    val context = LocalContext.current
    val application =
        context.applicationContext as YanindaApplication

    val factory = remember(application) {
        AdminMedicationViewModel.Factory(
            repository =
                application.adminScheduleRepository,
        )
    }

    val viewModel: AdminMedicationViewModel =
        viewModel(factory = factory)

    val state by
    viewModel.state.collectAsStateWithLifecycle()

    var page by rememberSaveable {
        mutableStateOf(AdminMedicationPage.LIST)
    }
    var selectedMedication by remember { mutableStateOf<PublishedMedication?>(null) }
    var medicationToDelete by remember { mutableStateOf<PublishedMedication?>(null) }
    var editingBaseVersion by rememberSaveable {
        mutableStateOf<Long?>(null)
    }

    var deletingBaseVersion by rememberSaveable {
        mutableStateOf<Long?>(null)
    }
    LaunchedEffect(familyId) {
        viewModel.bindFamily(familyId)
    }

    BackHandler(
        enabled = page == AdminMedicationPage.FORM,
    ) {
        selectedMedication = null
        editingBaseVersion = null
        page = AdminMedicationPage.LIST
    }

    LaunchedEffect(state.message) {
        when (state.message) {
            AdminMedicationMessage.SAVED,
            AdminMedicationMessage.DELETED,
                -> {
                selectedMedication = null
                editingBaseVersion = null
                deletingBaseVersion = null
                page = AdminMedicationPage.LIST
            }

            AdminMedicationMessage.VERSION_CONFLICT -> {
                selectedMedication = null
                editingBaseVersion = null
                deletingBaseVersion = null
                medicationToDelete = null
                page = AdminMedicationPage.LIST
            }



            else -> Unit
        }
    }

    when (page) {

        AdminMedicationPage.LIST -> {
            AdminMedicationListScreen(
                schedule = state.schedule,
                isLoading = state.isLoading,
                onAddMedication = {
                    selectedMedication = null
                    editingBaseVersion = null
                    page = AdminMedicationPage.FORM
                },
                onEditMedication = { medication ->
                    selectedMedication = medication
                    editingBaseVersion = state.schedule?.version
                    page = AdminMedicationPage.FORM
                },
                onDeleteMedication = { medication ->
                    medicationToDelete = medication
                    deletingBaseVersion = state.schedule?.version
                },
                )
        }

        AdminMedicationPage.FORM -> {
            MedicationFormScreen(
                configuration = selectedMedication?.toMedicationConfiguration(),
                errors = state.validationErrors,
                isWorking = state.isWorking,
                onSave = { draft ->
                    viewModel.saveMedication(
                        draft = draft,
                        expectedVersion =
                            if (selectedMedication != null) {
                                editingBaseVersion
                            } else {
                                null
                            },
                    )
                },
                onBack = {
                    selectedMedication = null
                    page =
                        AdminMedicationPage.LIST
                },
                onInputChanged =
                    viewModel::clearValidationErrors,
            )
        }

    }

    state.message?.let { message ->
        AdminMedicationMessageDialog(
            message = message,
            onDismiss = viewModel::clearMessage,
        )
    }

    medicationToDelete?.let { medication ->
        AlertDialog(
            onDismissRequest = {
                medicationToDelete = null
                deletingBaseVersion = null},
            title = { Text("İlaç programdan silinsin mi?") },
            text = { Text("${medication.displayName} ve saatleri yeni programdan kaldırılacak.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteMedication(
                            medicationId = medication.medicationId,
                            expectedVersion = deletingBaseVersion,
                        )
                        medicationToDelete = null
                        deletingBaseVersion = null
                    },
                    enabled = !state.isWorking,

                ) {
                    Text("SİL")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    medicationToDelete = null
                    deletingBaseVersion = null
                }) {
                    Text("VAZGEÇ")
                }
            },
        )
    }
}

@Composable
private fun AdminMedicationListScreen(
    schedule: PublishedScheduleVersion?,
    isLoading: Boolean,
    onAddMedication: () -> Unit,
    onEditMedication: (PublishedMedication) -> Unit,
    onDeleteMedication: (PublishedMedication) -> Unit,
) {
    val medications =
        schedule?.medications
            .orEmpty()
            .filter(PublishedMedication::active)
            .sortedBy {
                it.displayName.lowercase()
            }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = 20.dp,
            vertical = 24.dp,
        ),
        verticalArrangement =
            Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "İlaçlarım",
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(
                        text = "Dedenin ilaç programı",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                IconButton(
                    onClick = onAddMedication,
                    enabled = !isLoading,
                    modifier = Modifier.size(56.dp),
                ) {
                    YanindaIcon(
                        type = YanindaIconType.ADD,
                        contentDescription = "İlaç ekle",
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        when {
            isLoading -> {
                item {
                    MedicationEmptyCard(
                        title = "İlaç programı yükleniyor",
                        body =
                            "Aile programı kontrol ediliyor.",
                    )
                }
            }

            medications.isEmpty() -> {
                item {
                    MedicationEmptyCard(
                        title = "Henüz ilaç programı yok",
                        body =
                            "İlk sabit saatli ilacı ekleyerek başlayabilirsin.",
                    )
                }
            }

            else -> {
                medications.forEach { medication ->
                    item(
                        key = medication.medicationId,
                    ) {
                        PublishedMedicationCard(
                            medication = medication,
                            onEdit = { onEditMedication(medication) },
                            onDelete = { onDeleteMedication(medication) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PublishedMedicationCard(
    medication: PublishedMedication,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement =
                Arrangement.spacedBy(14.dp),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            YanindaMedicationImage(
                medicationName = medication.displayName,
                size = 50.dp,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement =
                    Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = medication.displayName,
                    style =
                        MaterialTheme.typography
                            .titleMedium,
                )

                Text(
                    text = medication.dosageText,
                    style =
                        MaterialTheme.typography
                            .bodyMedium,
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant,
                )

                Text(
                    text =
                        medication.scheduleTimesText(),
                    style =
                        MaterialTheme.typography
                            .titleSmall,
                    color =
                        MaterialTheme.colorScheme.primary,
                )

                Text(
                    text = medication.scheduleDaysText(),
                    style =
                        MaterialTheme.typography.bodyMedium,
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant,
                )
            }
            YanindaStatusPill(
                text = "Aktif",
                tone = YanindaStatusTone.SUCCESS,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                Text("DÜZENLE")
            }
            TextButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                Text("SİL", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun PublishedMedication.toMedicationConfiguration(): MedicationConfiguration {
    val now = Instant.EPOCH
    return MedicationConfiguration(
        medication = Medication(
            id = medicationId,
            displayName = displayName,
            dosageText = dosageText,
            instructionText = instructionText,
            photoUri = null,
            scheduleType = MedicationScheduleType.FIXED_ONLY,
            active = active,
            createdAt = now,
            updatedAt = now,
            version = 1L,
        ),
        schedules = schedules.map { schedule ->
            MedicationSchedule(
                id = schedule.scheduleId,
                medicationId = medicationId,
                localTime = LocalTime.of(
                    schedule.localTimeMinutes / 60,
                    schedule.localTimeMinutes % 60,
                ),
                daysOfWeek = DayOfWeekMask.decode(schedule.daysOfWeekMask),
                validFrom = LocalDate.now(),
                validUntil = null,
                snoozeEnabled = schedule.snoozeEnabled,
                snoozeMinutes = schedule.snoozeMinutes,
                maxSnoozes = schedule.maxSnoozes,
                createdAt = now,
                updatedAt = now,
                version = 1L,
            )
        },
    )
}

@Composable
private fun MedicationEmptyCard(
    title: String,
    body: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement =
                Arrangement.spacedBy(16.dp),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            YanindaIconBadge(
                icon = YanindaIconType.MEDICATION,
                size = 52.dp,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement =
                    Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style =
                        MaterialTheme.typography
                            .titleMedium,
                )

                Text(
                    text = body,
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

@Composable
private fun AdminMedicationMessageDialog(
    message: AdminMedicationMessage,
    onDismiss: () -> Unit,
) {
    val title: String
    val body: String

    when (message) {
        AdminMedicationMessage.SAVED -> {
            title = "İlaç programı yayınlandı"
            body =
                "Yeni program aile hesabına güvenli şekilde kaydedildi."
        }

        AdminMedicationMessage.DELETED -> {
            title = "İlaç programdan silindi"
            body = "İlaç yeni programdan kaldırıldı. Geçmiş kayıtlar korunuyor."
        }

        AdminMedicationMessage.NOT_AUTHENTICATED -> {
            title = "Oturum gerekli"
            body =
                "İlaç programını değiştirmek için yönetici hesabıyla giriş yap."
        }

        AdminMedicationMessage.PERMISSION_DENIED -> {
            title = "Yetki yok"
            body =
                "Bu ailede ilaç programını değiştirme yetkin bulunmuyor."
        }

        AdminMedicationMessage.NETWORK_UNAVAILABLE -> {
            title = "Bağlantı kurulamadı"
            body =
                "Program yayınlanamadı. İnternet bağlantısı geldiğinde tekrar deneyebilirsin."
        }

        AdminMedicationMessage.INVALID_INPUT -> {
            title = "Bilgileri kontrol et"
            body =
                "İlaç bilgilerinden biri geçerli değil."
        }

        AdminMedicationMessage.UNKNOWN_FAILURE -> {
            title = "Program kaydedilemedi"
            body =
                "Beklenmeyen bir hata oluştu. Tekrar deneyebilirsin."
        }
        AdminMedicationMessage.VERSION_CONFLICT -> {
            title = "Program başka bir yerde değiştirildi"
            body =
                "İlaç programı sen bu ekranı açtıktan sonra güncellendi. Güncel programı açıp değişikliği tekrar yap."
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title)
        },
        text = {
            Text(body)
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text("TAMAM")
            }
        },
    )
}

private fun PublishedMedication.scheduleTimesText():
        String =
    schedules
        .sortedBy {
            it.localTimeMinutes
        }
        .joinToString(" • ") { schedule ->
            val hour =
                schedule.localTimeMinutes / 60
            val minute =
                schedule.localTimeMinutes % 60

            "%02d:%02d".format(
                hour,
                minute,
            )
        }

private fun PublishedMedication.scheduleDaysText(): String =
    if (schedules.any { it.daysOfWeekMask == 127 }) {
        "Her gün"
    } else {
        "Programlı günler"
    }
