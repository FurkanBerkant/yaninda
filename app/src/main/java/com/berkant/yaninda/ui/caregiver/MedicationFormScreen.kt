package com.berkant.yaninda.ui.caregiver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import android.app.TimePickerDialog
import androidx.compose.ui.platform.LocalContext
import java.time.LocalTime
import java.util.Locale
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.berkant.yaninda.R
import com.berkant.yaninda.domain.medication.DayOfWeekMask
import com.berkant.yaninda.domain.medication.MedicationConfiguration
import com.berkant.yaninda.domain.medication.MedicationDraft
import com.berkant.yaninda.domain.medication.MedicationDraftError
import com.berkant.yaninda.domain.medication.MedicationDraftValidator
import com.berkant.yaninda.domain.medication.ScheduleDraft
import com.berkant.yaninda.ui.components.YanindaIconType
import com.berkant.yaninda.ui.components.YanindaOutlinedButton
import com.berkant.yaninda.ui.components.YanindaPrimaryButton
import com.berkant.yaninda.ui.components.YanindaSectionTitle
import java.time.DayOfWeek
import androidx.compose.foundation.lazy.items

@Composable
internal fun MedicationFormScreen(
    configuration: MedicationConfiguration?,
    errors: Set<MedicationDraftError>,
    isWorking: Boolean,
    onSave: (MedicationDraft) -> Unit,
    onBack: () -> Unit,
    onInputChanged: () -> Unit,
) {
    val formKey = configuration?.medication?.id ?: "new-medication"
    val existingSchedules = configuration?.schedules.orEmpty()
    var displayName by rememberSaveable(formKey) {
        mutableStateOf(configuration?.medication?.displayName.orEmpty())
    }
    var dosageText by rememberSaveable(formKey) {
        mutableStateOf(configuration?.medication?.dosageText.orEmpty())
    }
    var instructionText by rememberSaveable(formKey) {
        mutableStateOf(configuration?.medication?.instructionText.orEmpty())
    }
    var scheduleIds by rememberSaveable(formKey) {
        mutableStateOf(
            existingSchedules.map { it.id }.ifEmpty { listOf("") }
        )
    }
    var scheduleTimes by rememberSaveable(formKey) {
        mutableStateOf(
            existingSchedules
                .map { MedicationDraftValidator.formatTime(it.localTime) }
                .ifEmpty { listOf("") }
        )
    }
    var selectedDaysMask by rememberSaveable(formKey) {
        mutableIntStateOf(
            existingSchedules.firstOrNull()?.daysOfWeek?.let(DayOfWeekMask::encode)
                ?: DayOfWeekMask.encode(DayOfWeek.entries.toSet())
        )
    }
    var snoozeEnabled by rememberSaveable(formKey) {
        mutableStateOf(existingSchedules.firstOrNull()?.snoozeEnabled ?: false)
    }
    var snoozeMinutes by rememberSaveable(formKey) {
        mutableStateOf(existingSchedules.firstOrNull()?.snoozeMinutes?.toString().orEmpty())
    }
    var maxSnoozes by rememberSaveable(formKey) {
        mutableStateOf(existingSchedules.firstOrNull()?.maxSnoozes?.toString().orEmpty())
    }
    val title = stringResource(
        if (configuration == null) R.string.add_medication_title else R.string.edit_medication_title
    )
    val inputChanged = {
        if (errors.isNotEmpty()) onInputChanged()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .semantics { paneTitle = title },
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            CaregiverHeader(
                title = title,
                onBack = onBack,
                icon = YanindaIconType.MEDICATION,
            )
        }
        item {
            CaregiverNotice(
                title = stringResource(R.string.medication_form_safety_title),
                body = stringResource(R.string.medication_form_safety_body),
                isError = true,
            )
        }
        if (errors.isNotEmpty()) {
            item {
                ValidationErrorPanel(errors)
            }
        }
        item {
            OutlinedTextField(
                value = displayName,
                onValueChange = {
                    displayName = it.take(MedicationDraftValidator.NAME_MAX_LENGTH + 1)
                    inputChanged()
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.medication_name_label)) },
                supportingText = { Text(stringResource(R.string.medication_exact_text_hint)) },
                singleLine = true,
                enabled = !isWorking,
            )
        }
        item {
            OutlinedTextField(
                value = dosageText,
                onValueChange = {
                    dosageText = it.take(MedicationDraftValidator.DOSAGE_MAX_LENGTH + 1)
                    inputChanged()
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.medication_dosage_label)) },
                supportingText = { Text(stringResource(R.string.medication_dosage_hint)) },
                singleLine = true,
                enabled = !isWorking,
            )
        }
        item {
            OutlinedTextField(
                value = instructionText,
                onValueChange = {
                    instructionText = it.take(MedicationDraftValidator.INSTRUCTION_MAX_LENGTH + 1)
                    inputChanged()
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.medication_instruction_label)) },
                supportingText = { Text(stringResource(R.string.medication_instruction_hint)) },
                minLines = 2,
                maxLines = 4,
                enabled = !isWorking,
            )
        }
        item {
            FormSectionTitle(
                text = stringResource(R.string.medication_times_title),
                icon = YanindaIconType.CLOCK,
            )
            Text(
                text = stringResource(R.string.medication_times_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(scheduleTimes.size) { index ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MedicationTimePickerField(
                    value = scheduleTimes[index],
                    label = stringResource(
                        R.string.medication_time_label,
                        index + 1,
                    ),
                    enabled = !isWorking,
                    modifier = Modifier.weight(1f),
                    onTimeSelected = { selectedTime ->
                        scheduleTimes =
                            scheduleTimes.toMutableList().also {
                                it[index] = selectedTime
                            }

                        inputChanged()
                    },
                )

                if (scheduleTimes.size > 1) {
                    OutlinedButton(
                        onClick = {
                            scheduleTimes =
                                scheduleTimes.toMutableList().also {
                                    it.removeAt(index)
                                }

                            scheduleIds =
                                scheduleIds.toMutableList().also {
                                    it.removeAt(index)
                                }

                            inputChanged()
                        },
                        modifier = Modifier.heightIn(min = 64.dp),
                        enabled = !isWorking,
                    ) {
                        Text("Sil")
                    }
                }
            }
        }
        item {
            YanindaOutlinedButton(
                text = stringResource(R.string.add_medication_time),
                onClick = {
                    scheduleTimes = scheduleTimes + ""
                    scheduleIds = scheduleIds + ""
                    inputChanged()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                enabled = !isWorking,
                icon = YanindaIconType.ADD,
            )
        }
        item {
            FormSectionTitle(
                text = stringResource(R.string.medication_days_title),
                icon = YanindaIconType.CALENDAR,
            )
            DaySelection(
                selectedMask = selectedDaysMask,
                enabled = !isWorking,
                onToggle = { day ->
                    val bit = 1 shl (day.value - 1)
                    selectedDaysMask = selectedDaysMask xor bit
                    inputChanged()
                },
            )
        }
        item {
            FormSectionTitle(
                text = stringResource(R.string.medication_snooze_title),
                icon = YanindaIconType.ALARM,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .toggleable(
                        value = snoozeEnabled,
                        enabled = !isWorking,
                        role = Role.Switch,
                        onValueChange = {
                            snoozeEnabled = it
                            inputChanged()
                        },
                    )
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.medication_snooze_toggle),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = snoozeEnabled,
                    onCheckedChange = null,
                    enabled = !isWorking,
                )
            }
        }
        if (snoozeEnabled) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = snoozeMinutes,
                        onValueChange = {
                            snoozeMinutes = it.filter(Char::isDigit).take(2)
                            inputChanged()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.medication_snooze_minutes_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        enabled = !isWorking,
                    )
                    OutlinedTextField(
                        value = maxSnoozes,
                        onValueChange = {
                            maxSnoozes = it.filter(Char::isDigit).take(1)
                            inputChanged()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.medication_max_snoozes_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        enabled = !isWorking,
                    )
                }
                Text(
                    text = stringResource(R.string.medication_snooze_phase_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            if (isWorking) {
                Text(
                    text = "Program kaydediliyor...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            YanindaPrimaryButton(
                text = stringResource(R.string.save_medication),
                onClick = {
                    val days = DayOfWeekMask.decode(selectedDaysMask)
                    onSave(
                        MedicationDraft(
                            medicationId = configuration?.medication?.id,
                            schedules = scheduleTimes.mapIndexed { index, time ->
                                ScheduleDraft(
                                    id = scheduleIds[index].ifBlank { null },
                                    timeText = time,
                                )
                            },
                            displayName = displayName,
                            dosageText = dosageText,
                            instructionText = instructionText,
                            daysOfWeek = days,
                            snoozeEnabled = snoozeEnabled,
                            snoozeMinutesText = snoozeMinutes,
                            maxSnoozesText = maxSnoozes,
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
                enabled = !isWorking,
                icon = YanindaIconType.CHECK,
            )
        }
        item {
            YanindaOutlinedButton(
                text = stringResource(R.string.caregiver_cancel),
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                enabled = !isWorking,
                icon = YanindaIconType.WARNING,
            )
        }
    }
}

@Composable
private fun MedicationTimePickerField(
    value: String,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onTimeSelected: (String) -> Unit,
) {
    val context = LocalContext.current

    val currentValue =
        MedicationDraftValidator.parseTimeOrNull(value)

    val displayValue =
        currentValue?.let(
            MedicationDraftValidator::formatTime
        ) ?: "Saat seç"

    OutlinedButton(
        onClick = {
            val initialTime =
                currentValue ?: LocalTime.now()

            TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    val selected =
                        String.format(
                            Locale.ROOT,
                            "%02d:%02d",
                            hourOfDay,
                            minute,
                        )

                    onTimeSelected(selected)
                },
                initialTime.hour,
                initialTime.minute,
                true,
            ).show()
        },
        modifier = modifier.heightIn(min = 64.dp),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = displayValue,
                style = MaterialTheme.typography.titleLarge,
                color =
                    if (currentValue == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
            )
        }
    }
}
@Composable
private fun FormSectionTitle(
    text: String,
    icon: YanindaIconType,
) {
    YanindaSectionTitle(
        title = text,
        icon = icon,
    )
}

@Composable
private fun DaySelection(
    selectedMask: Int,
    enabled: Boolean,
    onToggle: (DayOfWeek) -> Unit,
) {
    val dayRows = listOf(
        DayOfWeek.entries.take(4),
        DayOfWeek.entries.drop(4),
    )
    val labels = mapOf(
        DayOfWeek.MONDAY to stringResource(R.string.day_monday),
        DayOfWeek.TUESDAY to stringResource(R.string.day_tuesday),
        DayOfWeek.WEDNESDAY to stringResource(R.string.day_wednesday),
        DayOfWeek.THURSDAY to stringResource(R.string.day_thursday),
        DayOfWeek.FRIDAY to stringResource(R.string.day_friday),
        DayOfWeek.SATURDAY to stringResource(R.string.day_saturday),
        DayOfWeek.SUNDAY to stringResource(R.string.day_sunday),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        dayRows.forEach { days ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                days.forEach { day ->
                    val selected = selectedMask and (1 shl (day.value - 1)) != 0
                    FilterChip(
                        selected = selected,
                        onClick = { onToggle(day) },
                        label = { Text(labels.getValue(day)) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        enabled = enabled,
                    )
                }
                repeat(4 - days.size) {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ValidationErrorPanel(errors: Set<MedicationDraftError>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.medication_validation_title),
                style = MaterialTheme.typography.titleMedium,
            )
            errors.forEach { error ->
                Text(
                    text = "• ${stringResource(error.messageResource())}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun MedicationDraftError.messageResource(): Int = when (this) {
    MedicationDraftError.NAME_REQUIRED -> R.string.validation_name_required
    MedicationDraftError.NAME_TOO_LONG -> R.string.validation_name_too_long
    MedicationDraftError.DOSAGE_REQUIRED -> R.string.validation_dosage_required
    MedicationDraftError.DOSAGE_TOO_LONG -> R.string.validation_dosage_too_long
    MedicationDraftError.INSTRUCTION_REQUIRED -> R.string.validation_instruction_required
    MedicationDraftError.INSTRUCTION_TOO_LONG -> R.string.validation_instruction_too_long
    MedicationDraftError.TIME_REQUIRED -> R.string.validation_time_required
    MedicationDraftError.INVALID_TIME -> R.string.validation_time_invalid
    MedicationDraftError.DUPLICATE_TIME -> R.string.validation_time_duplicate
    MedicationDraftError.DAY_REQUIRED -> R.string.validation_day_required
    MedicationDraftError.SNOOZE_MINUTES_INVALID -> R.string.validation_snooze_minutes
    MedicationDraftError.MAX_SNOOZES_INVALID -> R.string.validation_max_snoozes
}
