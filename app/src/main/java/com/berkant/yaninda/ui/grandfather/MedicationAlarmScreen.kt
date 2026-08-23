package com.berkant.yaninda.ui.grandfather

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.berkant.yaninda.R
import com.berkant.yaninda.ui.components.YanindaIconBadge
import com.berkant.yaninda.ui.components.YanindaIconType
import com.berkant.yaninda.ui.components.YanindaOutlinedButton
import com.berkant.yaninda.ui.components.YanindaPrimaryButton
import com.berkant.yaninda.ui.theme.YanindaTheme

@Composable
fun MedicationAlarmScreen(
    alarmTime: String,
    medicationName: String,
    dosageText: String,
    instructionText: String,
    snoozeMinutes: Int,
    snoozeAvailable: Boolean,
    isWorking: Boolean,
    onTaken: () -> Unit,
    onSnooze: () -> Unit,
    onCallFamily: () -> Unit,
    modifier: Modifier = Modifier
) {
    val screenTitle = stringResource(R.string.accessibility_alarm_screen)
    val alarmTimeDescription = stringResource(R.string.accessibility_alarm_time, alarmTime)
    val medicationDescription = stringResource(
        R.string.accessibility_medication_details,
        medicationName,
        dosageText,
        instructionText
    )

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .semantics {
                    paneTitle = screenTitle
                },
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(30.dp),
                    color = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        YanindaIconBadge(
                            icon = YanindaIconType.ALARM,
                            size = 58.dp,
                            containerColor = MaterialTheme.colorScheme.onError.copy(alpha = 0.18f),
                            iconColor = MaterialTheme.colorScheme.onError,
                        )
                        Text(
                            text = stringResource(R.string.alarm_title),
                            style = MaterialTheme.typography.headlineLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = alarmTime,
                            style = MaterialTheme.typography.displayLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.clearAndSetSemantics {
                                contentDescription = alarmTimeDescription
                            }
                        )
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clearAndSetSemantics {
                            contentDescription = medicationDescription
                        },
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shadowElevation = 2.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        YanindaIconBadge(
                            icon = YanindaIconType.MEDICATION,
                            size = 78.dp,
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = medicationName,
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = dosageText,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = instructionText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    YanindaPrimaryButton(
                        text = stringResource(R.string.taken_action),
                        onClick = onTaken,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp),
                        icon = YanindaIconType.CHECK,
                        enabled = !isWorking,
                        minHeight = 80.dp,
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    )

                    if (snoozeAvailable) {
                        YanindaPrimaryButton(
                            text = stringResource(R.string.snooze_action, snoozeMinutes),
                            onClick = onSnooze,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp),
                            icon = YanindaIconType.CLOCK,
                            enabled = !isWorking,
                            minHeight = 64.dp,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }

                    YanindaOutlinedButton(
                        text = stringResource(R.string.call_family),
                        onClick = onCallFamily,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp),
                        icon = YanindaIconType.PHONE,
                        enabled = !isWorking,
                        minHeight = 64.dp,
                    )
                }
            }
        }
    }
}

@Preview(
    name = "Galaxy A06",
    showBackground = true,
    widthDp = 360,
    heightDp = 800
)
@Preview(
    name = "Galaxy A06 - Büyük yazı",
    showBackground = true,
    widthDp = 360,
    heightDp = 800,
    fontScale = 1.3f
)
@Composable
private fun MedicationAlarmScreenPreview() {
    YanindaTheme(darkTheme = false) {
        MedicationAlarmScreen(
            alarmTime = stringResource(R.string.prototype_alarm_time),
            medicationName = stringResource(R.string.prototype_medication_name),
            dosageText = stringResource(R.string.prototype_dosage_text),
            instructionText = stringResource(R.string.prototype_instruction_text),
            snoozeMinutes = 10,
            snoozeAvailable = true,
            isWorking = false,
            onTaken = {},
            onSnooze = {},
            onCallFamily = {}
        )
    }
}
