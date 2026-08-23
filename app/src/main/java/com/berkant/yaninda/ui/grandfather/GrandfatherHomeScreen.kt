package com.berkant.yaninda.ui.grandfather

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
import com.berkant.yaninda.ui.components.YanindaIconBadge
import com.berkant.yaninda.ui.components.YanindaIconType
import com.berkant.yaninda.ui.components.YanindaPrimaryButton
import com.berkant.yaninda.ui.theme.YanindaTheme

enum class GrandfatherHomeStatusTone {
    POSITIVE,
    NEUTRAL,
    ATTENTION,
}

@Composable
fun GrandfatherHomeScreen(
    dateText: String,
    timeText: String,
    statusText: String,
    nextMedicationTime: String,
    nextMedicationNames: List<String> = emptyList(),
    reminderHealthText: String,
    reminderHealthy: Boolean,
    onCallFamily: (() -> Unit)?,
    modifier: Modifier = Modifier,
    statusTone: GrandfatherHomeStatusTone =
        GrandfatherHomeStatusTone.POSITIVE,
    statusSymbol: String? = null,
) {
    val screenTitle =
        stringResource(
            R.string.accessibility_home_screen
        )

    val currentTimeDescription =
        stringResource(
            R.string.accessibility_current_time,
            timeText,
        )

    val resolvedStatusSymbol =
        statusSymbol
            ?: stringResource(
                R.string.home_status_symbol
            )

    val statusContainerColor =
        MaterialTheme.colorScheme.surface

    val onStatusContainerColor =
        MaterialTheme.colorScheme.onSurface

    val onStatusAccentColor =
        when (statusTone) {

            GrandfatherHomeStatusTone.POSITIVE ->
                MaterialTheme
                    .colorScheme
                    .onTertiaryContainer

            GrandfatherHomeStatusTone.NEUTRAL ->
                MaterialTheme
                    .colorScheme
                    .onPrimaryContainer

            GrandfatherHomeStatusTone.ATTENTION ->
                MaterialTheme
                    .colorScheme
                    .onErrorContainer
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
             * Tarih + saat
             *
             * Gerçek bir weather kaynağı olmadığı
             * için sahte sıcaklık/lokasyon bilgisi
             * gösterilmiyor.
             */
            item {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(
                                RoundedCornerShape(
                                    30.dp
                                )
                            ),
                ) {

                    Image(
                        painter =
                            painterResource(
                                R.drawable
                                    .grandfather_home_landscape
                            ),
                        contentDescription = null,
                        contentScale =
                            ContentScale.Crop,
                        modifier =
                            Modifier.fillMaxSize(),
                    )

                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush
                                        .verticalGradient(
                                            0f to
                                                    Color(
                                                        0xA6002037
                                                    ),
                                            0.52f to
                                                    Color(
                                                        0x48002037
                                                    ),
                                            1f to
                                                    Color(
                                                        0xB8002037
                                                    ),
                                        )
                                )
                    )

                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(
                                    horizontal =
                                        22.dp,
                                    vertical =
                                        20.dp,
                                ),
                        horizontalAlignment =
                            Alignment.Start,
                        verticalArrangement =
                            Arrangement.Center,
                    ) {

                        Text(
                            text = dateText,
                            style =
                                MaterialTheme
                                    .typography
                                    .titleLarge,
                            color =
                                Color.White,
                            textAlign =
                                TextAlign.Start,
                            modifier =
                                Modifier.semantics {
                                    heading()
                                },
                        )

                        Text(
                            text = timeText,
                            style =
                                MaterialTheme
                                    .typography
                                    .displayLarge,
                            color =
                                Color.White,
                            textAlign =
                                TextAlign.Start,
                            modifier =
                                Modifier
                                    .padding(
                                        top = 6.dp
                                    )
                                    .clearAndSetSemantics {
                                        contentDescription =
                                            currentTimeDescription
                                    },
                        )
                    }
                }
            }

            /*
             * Günlük durum
             */
            item {
                Surface(
                    modifier =
                        Modifier.fillMaxWidth(),
                    shape =
                        RoundedCornerShape(
                            24.dp
                        ),
                    color =
                        statusContainerColor,
                    contentColor =
                        onStatusContainerColor,
                    shadowElevation =
                        2.dp,
                ) {

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal =
                                        18.dp,
                                    vertical =
                                        17.dp,
                                )
                                .semantics(
                                    mergeDescendants =
                                        true
                                ) {
                                    liveRegion =
                                        LiveRegionMode
                                            .Polite
                                },
                        verticalAlignment =
                            Alignment
                                .CenterVertically,
                        horizontalArrangement =
                            Arrangement
                                .spacedBy(
                                    16.dp
                                ),
                    ) {

                        Surface(
                            modifier =
                                Modifier
                                    .size(52.dp)
                                    .clearAndSetSemantics {
                                    },
                            shape =
                                CircleShape,
                            color =
                                when (
                                    statusTone
                                ) {

                                    GrandfatherHomeStatusTone
                                        .POSITIVE ->
                                        MaterialTheme
                                            .colorScheme
                                            .tertiaryContainer

                                    GrandfatherHomeStatusTone
                                        .NEUTRAL ->
                                        MaterialTheme
                                            .colorScheme
                                            .primaryContainer

                                    GrandfatherHomeStatusTone
                                        .ATTENTION ->
                                        MaterialTheme
                                            .colorScheme
                                            .errorContainer
                                },
                            contentColor =
                                onStatusAccentColor,
                        ) {

                            Box(
                                contentAlignment =
                                    Alignment.Center,
                            ) {
                                Text(
                                    text =
                                        resolvedStatusSymbol,
                                    style =
                                        MaterialTheme
                                            .typography
                                            .titleLarge,
                                )
                            }
                        }

                        Text(
                            text =
                                statusText,
                            style =
                                MaterialTheme
                                    .typography
                                    .titleMedium,
                            textAlign =
                                TextAlign.Start,
                            modifier =
                                Modifier.weight(
                                    1f
                                ),
                        )
                    }
                }
            }

            /*
             * Sıradaki ilaç / dose group
             */
            item {
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .semantics(
                                mergeDescendants =
                                    true
                            ) {

                                contentDescription =
                                    buildString {

                                        append(
                                            "Sıradaki ilaç saati "
                                        )

                                        append(
                                            nextMedicationTime
                                        )

                                        if (
                                            nextMedicationNames
                                                .isNotEmpty()
                                        ) {
                                            append(". ")

                                            append(
                                                nextMedicationNames
                                                    .joinToString(
                                                        separator =
                                                            ", "
                                                    )
                                            )
                                        }
                                    }
                            },
                    shape =
                        RoundedCornerShape(
                            24.dp
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
                        2.dp,
                ) {

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal =
                                        20.dp,
                                    vertical =
                                        20.dp,
                                ),
                        verticalAlignment =
                            Alignment
                                .CenterVertically,
                        horizontalArrangement =
                            Arrangement
                                .spacedBy(
                                    16.dp
                                ),
                    ) {

                        YanindaIconBadge(
                            icon =
                                YanindaIconType
                                    .MEDICATION,
                            size =
                                64.dp,
                        )

                        Column(
                            modifier =
                                Modifier.weight(
                                    1f
                                ),
                            verticalArrangement =
                                Arrangement
                                    .spacedBy(
                                        6.dp
                                    ),
                        ) {

                            Text(
                                text =
                                    stringResource(
                                        R.string
                                            .next_medication_label
                                    ),
                                style =
                                    MaterialTheme
                                        .typography
                                        .titleMedium,
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurfaceVariant,
                            )

                            Text(
                                text =
                                    nextMedicationTime,
                                style =
                                    MaterialTheme
                                        .typography
                                        .displayMedium,
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurface,
                                modifier =
                                    Modifier
                                        .clearAndSetSemantics {
                                        },
                            )

                            when {

                                nextMedicationNames
                                    .isEmpty() -> {

                                    Text(
                                        text =
                                            "İlaç programı bekleniyor",
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

                                nextMedicationNames
                                    .size == 1 -> {

                                    Text(
                                        text =
                                            nextMedicationNames
                                                .first(),
                                        style =
                                            MaterialTheme
                                                .typography
                                                .headlineSmall,
                                        color =
                                            MaterialTheme
                                                .colorScheme
                                                .onSurface,
                                    )
                                }

                                else -> {

                                    Text(
                                        text =
                                            "${nextMedicationNames.size} ilaç birlikte",
                                        style =
                                            MaterialTheme
                                                .typography
                                                .titleMedium,
                                        color =
                                            MaterialTheme
                                                .colorScheme
                                                .primary,
                                    )

                                    nextMedicationNames
                                        .forEach {
                                                medicationName ->

                                            Row(
                                                verticalAlignment =
                                                    Alignment
                                                        .CenterVertically,
                                                horizontalArrangement =
                                                    Arrangement
                                                        .spacedBy(
                                                            8.dp
                                                        ),
                                            ) {

                                                Text(
                                                    text =
                                                        "•",
                                                    style =
                                                        MaterialTheme
                                                            .typography
                                                            .headlineSmall,
                                                    color =
                                                        MaterialTheme
                                                            .colorScheme
                                                            .primary,
                                                )

                                                Text(
                                                    text =
                                                        medicationName,
                                                    style =
                                                        MaterialTheme
                                                            .typography
                                                            .titleLarge,
                                                    color =
                                                        MaterialTheme
                                                            .colorScheme
                                                            .onSurface,
                                                )
                                            }
                                        }
                                }
                            }
                        }
                    }
                }
            }

            /*
             * Alarm durumu
             *
             * Teknik capability detayları kullanıcıya
             * gösterilmiyor. Diagnostics verisi ise
             * accessibility description içinde korunuyor.
             */
            item {
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .semantics(
                                mergeDescendants =
                                    true
                            ) {

                                contentDescription =
                                    if (
                                        reminderHealthy
                                    ) {
                                        "İlaç alarmı hazır. $reminderHealthText"
                                    } else {
                                        "İlaç alarmında kontrol gereken bir durum var. $reminderHealthText"
                                    }

                                liveRegion =
                                    LiveRegionMode
                                        .Polite
                            },
                    shape =
                        RoundedCornerShape(
                            24.dp
                        ),
                    color =
                        if (
                            reminderHealthy
                        ) {
                            MaterialTheme
                                .colorScheme
                                .tertiaryContainer
                        } else {
                            MaterialTheme
                                .colorScheme
                                .errorContainer
                        },
                    contentColor =
                        if (
                            reminderHealthy
                        ) {
                            MaterialTheme
                                .colorScheme
                                .onTertiaryContainer
                        } else {
                            MaterialTheme
                                .colorScheme
                                .onErrorContainer
                        },
                    shadowElevation =
                        2.dp,
                ) {

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal =
                                        18.dp,
                                    vertical =
                                        17.dp,
                                ),
                        verticalAlignment =
                            Alignment
                                .CenterVertically,
                        horizontalArrangement =
                            Arrangement
                                .spacedBy(
                                    14.dp
                                ),
                    ) {

                        Surface(
                            modifier =
                                Modifier
                                    .size(52.dp)
                                    .clearAndSetSemantics {
                                    },
                            shape =
                                CircleShape,
                            color =
                                if (
                                    reminderHealthy
                                ) {
                                    MaterialTheme
                                        .colorScheme
                                        .tertiary
                                } else {
                                    MaterialTheme
                                        .colorScheme
                                        .error
                                },
                            contentColor =
                                if (
                                    reminderHealthy
                                ) {
                                    MaterialTheme
                                        .colorScheme
                                        .onTertiary
                                } else {
                                    MaterialTheme
                                        .colorScheme
                                        .onError
                                },
                        ) {

                            Box(
                                contentAlignment =
                                    Alignment.Center,
                            ) {

                                Text(
                                    text =
                                        if (
                                            reminderHealthy
                                        ) {
                                            "✓"
                                        } else {
                                            "!"
                                        },
                                    style =
                                        MaterialTheme
                                            .typography
                                            .headlineSmall,
                                )
                            }
                        }

                        Column(
                            modifier =
                                Modifier.weight(
                                    1f
                                ),
                            verticalArrangement =
                                Arrangement
                                    .spacedBy(
                                        3.dp
                                    ),
                        ) {

                            Text(
                                text =
                                    if (
                                        reminderHealthy
                                    ) {
                                        "İlaç alarmı hazır"
                                    } else {
                                        "İlaç alarmını kontrol edin"
                                    },
                                style =
                                    MaterialTheme
                                        .typography
                                        .titleMedium,
                            )

                            Text(
                                text =
                                    if (
                                        reminderHealthy
                                    ) {
                                        "Telefon ilaç saatini hatırlatacak."
                                    } else {
                                        "Bir aile üyesinden yardım isteyin."
                                    },
                                style =
                                    MaterialTheme
                                        .typography
                                        .bodyMedium,
                            )
                        }
                    }
                }
            }

            /*
             * Aileyi ara
             *
             * Telefon ayarlanmamışsa buton hiç
             * gösterilmez. Varsa büyük, tam genişlikte
             * tek dokunuş hedefi sunulur.
             */
            if (onCallFamily != null) {

                item {
                    YanindaPrimaryButton(
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
                                    min = 84.dp
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
                        minHeight =
                            84.dp,
                        containerColor =
                            MaterialTheme
                                .colorScheme
                                .secondary,
                        contentColor =
                            MaterialTheme
                                .colorScheme
                                .onSecondary,
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
private fun GrandfatherHomeScreenPreview() {
    YanindaTheme(
        darkTheme = false
    ) {
        GrandfatherHomeScreen(
            dateText =
                stringResource(
                    R.string
                        .prototype_home_date
                ),
            timeText =
                stringResource(
                    R.string
                        .prototype_home_time
                ),
            statusText =
                stringResource(
                    R.string
                        .home_status_idle
                ),
            nextMedicationTime =
                stringResource(
                    R.string
                        .prototype_next_medication_time
                ),
            nextMedicationNames =
                listOf(
                    "Şeker İlacı",
                    "Tansiyon İlacı",
                ),
            reminderHealthText =
                "Kesin alarm hazır • Bildirimler hazır • Tam ekran alarm hazır",
            reminderHealthy =
                true,
            onCallFamily = {},
        )
    }
}