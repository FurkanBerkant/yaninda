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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.berkant.yaninda.R
import com.berkant.yaninda.ui.alarm.MedicationAlarmItem
import com.berkant.yaninda.ui.components.YanindaIconBadge
import com.berkant.yaninda.ui.components.YanindaIconType
import com.berkant.yaninda.ui.components.YanindaOutlinedButton
import com.berkant.yaninda.ui.components.YanindaPrimaryButton
import com.berkant.yaninda.ui.theme.YanindaTheme

@Composable
fun MedicationAlarmScreen(
    alarmTime: String,
    medications: List<MedicationAlarmItem>,
    snoozeMinutes: Int,
    snoozeAvailable: Boolean,
    isWorking: Boolean,
    onTaken: () -> Unit,
    onSnooze: () -> Unit,
    onCallFamily: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val screenTitle =
        stringResource(
            R.string.accessibility_alarm_screen
        )

    val alarmTimeDescription =
        stringResource(
            R.string.accessibility_alarm_time,
            alarmTime,
        )

    val medicationDescription =
        medications.joinToString(
            separator = ", "
        ) { medication ->

            buildString {

                append(
                    medication.medicationName
                )

                if (
                    medication.dosageText
                        .isNotBlank()
                ) {
                    append(", ")

                    append(
                        medication.dosageText
                    )
                }

                if (
                    medication.instructionText
                        .isNotBlank()
                ) {
                    append(", ")

                    append(
                        medication.instructionText
                    )
                }
            }
        }

    Surface(
        modifier =
            modifier.fillMaxSize(),
        color =
            MaterialTheme.colorScheme.background,
        contentColor =
            MaterialTheme.colorScheme.onBackground,
    ) {

        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .semantics {
                        paneTitle =
                            screenTitle

                        liveRegion =
                            LiveRegionMode.Assertive
                    },
            contentPadding =
                PaddingValues(
                    horizontal = 16.dp,
                    vertical = 14.dp,
                ),
            verticalArrangement =
                Arrangement.spacedBy(14.dp),
            horizontalAlignment =
                Alignment.CenterHorizontally,
        ) {

            /*
             * Alarm başlığı.
             *
             * Bu ekranın normal ana sayfadan tamamen
             * farklı olduğu ilk bakışta anlaşılmalı.
             */
            item {

                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .semantics(
                                mergeDescendants = true
                            ) {
                                contentDescription =
                                    "İlaç saati. $alarmTimeDescription"
                            },
                    shape =
                        RoundedCornerShape(
                            30.dp
                        ),
                    color =
                        MaterialTheme
                            .colorScheme
                            .errorContainer,
                    contentColor =
                        MaterialTheme
                            .colorScheme
                            .onErrorContainer,
                ) {

                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal =
                                        20.dp,
                                    vertical =
                                        22.dp,
                                ),
                        horizontalAlignment =
                            Alignment
                                .CenterHorizontally,
                        verticalArrangement =
                            Arrangement
                                .spacedBy(
                                    8.dp
                                ),
                    ) {

                        Text(
                            text =
                                "İLAÇ SAATİ",
                            style =
                                MaterialTheme
                                    .typography
                                    .headlineMedium,
                            textAlign =
                                TextAlign.Center,
                            modifier =
                                Modifier
                                    .clearAndSetSemantics {
                                    },
                        )

                        Text(
                            text =
                                alarmTime,
                            style =
                                MaterialTheme
                                    .typography
                                    .displayLarge,
                            textAlign =
                                TextAlign.Center,
                            modifier =
                                Modifier
                                    .clearAndSetSemantics {
                                    },
                        )

                        Text(
                            text =
                                if (
                                    medications.size <= 1
                                ) {
                                    "İlacınızı almanız gerekiyor"
                                } else {
                                    "İlaçlarınızı almanız gerekiyor"
                                },
                            style =
                                MaterialTheme
                                    .typography
                                    .titleLarge,
                            textAlign =
                                TextAlign.Center,
                            modifier =
                                Modifier
                                    .clearAndSetSemantics {
                                    },
                        )
                    }
                }
            }

            /*
             * Aynı saate ait tüm ilaçlar tek kart.
             *
             * Roadmap 8:
             * bir dose group = bir alarm ekranı.
             */
            item {

                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clearAndSetSemantics {

                                contentDescription =
                                    medicationDescription
                            },
                    shape =
                        RoundedCornerShape(
                            26.dp
                        ),
                    color =
                        MaterialTheme
                            .colorScheme
                            .surface,
                    contentColor =
                        MaterialTheme
                            .colorScheme
                            .onSurface,
                    shadowElevation =
                        3.dp,
                ) {

                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal =
                                        18.dp,
                                    vertical =
                                        20.dp,
                                ),
                        verticalArrangement =
                            Arrangement
                                .spacedBy(
                                    18.dp
                                ),
                    ) {

                        Text(
                            text =
                                when {

                                    medications.isEmpty() ->
                                        "İlaç bilgisi"

                                    medications.size == 1 ->
                                        "Almanız gereken ilaç"

                                    else ->
                                        "Almanız gereken ${medications.size} ilaç"
                                },
                            style =
                                MaterialTheme
                                    .typography
                                    .titleLarge,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurface,
                            modifier =
                                Modifier.semantics {
                                    heading()
                                },
                        )

                        if (medications.isEmpty()) {

                            Text(
                                text =
                                    "İlaç bilgisi hazırlanıyor.",
                                style =
                                    MaterialTheme
                                        .typography
                                        .bodyLarge,
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurfaceVariant,
                            )

                        } else {

                            medications
                                .forEach {
                                        medication ->

                                    MedicationAlarmRow(
                                        medication =
                                            medication
                                    )
                                }
                        }
                    }
                }
            }

            /*
             * Ana işlem.
             *
             * Dedemin yapması gereken en önemli
             * işlem bu olduğu için diğer butonlardan
             * daha büyük.
             */
            item {

                Column(
                    modifier =
                        Modifier.fillMaxWidth(),
                    verticalArrangement =
                        Arrangement
                            .spacedBy(
                                12.dp
                            ),
                    horizontalAlignment =
                        Alignment
                            .CenterHorizontally,
                ) {

                    YanindaPrimaryButton(
                        text =
                            stringResource(
                                R.string
                                    .taken_action
                            ),
                        onClick =
                            onTaken,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(
                                    min = 88.dp
                                )
                                .semantics(
                                    mergeDescendants =
                                        true
                                ) {
                                    contentDescription =
                                        "İlaçlarımı aldım"
                                },
                        icon =
                            YanindaIconType
                                .CHECK,
                        enabled =
                            !isWorking &&
                                    medications
                                        .isNotEmpty(),
                        minHeight =
                            88.dp,
                        containerColor =
                            MaterialTheme
                                .colorScheme
                                .tertiary,
                        contentColor =
                            MaterialTheme
                                .colorScheme
                                .onTertiary,
                    )

                    /*
                     * Erteleme sadece mevcut dose
                     * group için gerçekten destekleniyorsa
                     * gösterilir.
                     */
                    if (
                        snoozeAvailable
                    ) {

                        YanindaPrimaryButton(
                            text =
                                stringResource(
                                    R.string
                                        .snooze_action,
                                    snoozeMinutes,
                                ),
                            onClick =
                                onSnooze,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(
                                        min = 68.dp
                                    ),
                            icon =
                                YanindaIconType
                                    .CLOCK,
                            enabled =
                                !isWorking,
                            minHeight =
                                68.dp,
                            containerColor =
                                MaterialTheme
                                    .colorScheme
                                    .primaryContainer,
                            contentColor =
                                MaterialTheme
                                    .colorScheme
                                    .onPrimaryContainer,
                        )
                    }

                    YanindaOutlinedButton(
                        text =
                            stringResource(
                                R.string
                                    .call_family
                            ),
                        onClick =
                            onCallFamily,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(
                                    min = 68.dp
                                )
                                .semantics(
                                    mergeDescendants =
                                        true
                                ) {
                                    contentDescription =
                                        "Aileyi ara"
                                },
                        icon =
                            YanindaIconType
                                .PHONE,
                        enabled =
                            !isWorking,
                        minHeight =
                            68.dp,
                    )
                }
            }

            /*
             * Ana butona basıldıktan sonra işlem
             * sürüyorsa kullanıcıya çok basit geri bildirim.
             */
            if (isWorking) {

                item {

                    Text(
                        text =
                            "Kaydediliyor…",
                        style =
                            MaterialTheme
                                .typography
                                .titleMedium,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant,
                        textAlign =
                            TextAlign.Center,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .semantics {
                                    liveRegion =
                                        LiveRegionMode
                                            .Polite
                                },
                    )
                }
            }
        }
    }
}

@Composable
private fun MedicationAlarmRow(
    medication: MedicationAlarmItem,
) {

    Row(
        modifier =
            Modifier.fillMaxWidth(),
        verticalAlignment =
            Alignment.Top,
        horizontalArrangement =
            Arrangement.spacedBy(
                14.dp
            ),
    ) {

        YanindaIconBadge(
            icon =
                YanindaIconType
                    .MEDICATION,
            size =
                62.dp,
        )

        Column(
            modifier =
                Modifier.weight(1f),
            verticalArrangement =
                Arrangement.spacedBy(
                    5.dp
                ),
        ) {

            Text(
                text =
                    medication
                        .medicationName,
                style =
                    MaterialTheme
                        .typography
                        .headlineSmall,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurface,
            )

            if (
                medication
                    .dosageText
                    .isNotBlank()
            ) {

                Text(
                    text =
                        medication
                            .dosageText,
                    style =
                        MaterialTheme
                            .typography
                            .titleLarge,
                    color =
                        MaterialTheme
                            .colorScheme
                            .primary,
                )
            }

            if (
                medication
                    .instructionText
                    .isNotBlank()
            ) {

                Text(
                    text =
                        medication
                            .instructionText,
                    style =
                        MaterialTheme
                            .typography
                            .bodyLarge,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(
    name = "Galaxy A06",
    showBackground = true,
    widthDp = 360,
    heightDp = 800,
)
@Preview(
    name = "Galaxy A06 - Büyük yazı",
    showBackground = true,
    widthDp = 360,
    heightDp = 800,
    fontScale = 1.3f,
)
@Composable
private fun MedicationAlarmScreenPreview() {

    YanindaTheme(
        darkTheme = false
    ) {

        MedicationAlarmScreen(
            alarmTime =
                stringResource(
                    R.string
                        .prototype_alarm_time
                ),
            medications =
                listOf(
                    MedicationAlarmItem(
                        medicationId =
                            "med-1",
                        medicationName =
                            "Beloc",
                        dosageText =
                            "50 mg",
                        instructionText =
                            "1 tablet",
                    ),
                    MedicationAlarmItem(
                        medicationId =
                            "med-2",
                        medicationName =
                            "Coraspin",
                        dosageText =
                            "100 mg",
                        instructionText =
                            "1 tablet",
                    ),
                ),
            snoozeMinutes =
                10,
            snoozeAvailable =
                true,
            isWorking =
                false,
            onTaken = {},
            onSnooze = {},
            onCallFamily = {},
        )
    }
}