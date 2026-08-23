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
    nextMedicationName: String? = null,
    onCallFamily: (() -> Unit)?,
    modifier: Modifier = Modifier,
    statusTone: GrandfatherHomeStatusTone = GrandfatherHomeStatusTone.POSITIVE,
    statusSymbol: String? = null,
) {
    val screenTitle = stringResource(R.string.accessibility_home_screen)
    val currentTimeDescription = stringResource(R.string.accessibility_current_time, timeText)
    val nextMedicationDescription = stringResource(
        R.string.accessibility_next_medication,
        nextMedicationTime
    )
    val resolvedStatusSymbol = statusSymbol ?: stringResource(R.string.home_status_symbol)
    val statusContainerColor = MaterialTheme.colorScheme.surface
    val onStatusContainerColor = MaterialTheme.colorScheme.onSurface
    val onStatusAccentColor = when (statusTone) {
        GrandfatherHomeStatusTone.POSITIVE -> MaterialTheme.colorScheme.onTertiaryContainer
        GrandfatherHomeStatusTone.NEUTRAL -> MaterialTheme.colorScheme.onPrimaryContainer
        GrandfatherHomeStatusTone.ATTENTION -> MaterialTheme.colorScheme.onErrorContainer
    }

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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(272.dp)
                        .clip(RoundedCornerShape(30.dp)),
                ) {
                    Image(
                        painter = painterResource(R.drawable.grandfather_home_landscape),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color(0xA6002037),
                                    0.52f to Color(0x48002037),
                                    1f to Color(0xB8002037),
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 22.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = dateText,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.semantics { heading() }
                            )
                            Text(
                                text = timeText,
                                style = MaterialTheme.typography.displayLarge,
                                color = Color.White,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.clearAndSetSemantics {
                                    contentDescription = currentTimeDescription
                                }
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "☀️",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = "22°Köy",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = statusContainerColor,
                    contentColor = onStatusContainerColor,
                    shadowElevation = 2.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 17.dp)
                            .semantics(mergeDescendants = true) {
                                liveRegion = LiveRegionMode.Polite
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .size(52.dp)
                                .clearAndSetSemantics { },
                            shape = CircleShape,
                            color = when (statusTone) {
                                GrandfatherHomeStatusTone.POSITIVE ->
                                    MaterialTheme.colorScheme.tertiaryContainer
                                GrandfatherHomeStatusTone.NEUTRAL ->
                                    MaterialTheme.colorScheme.primaryContainer
                                GrandfatherHomeStatusTone.ATTENTION ->
                                    MaterialTheme.colorScheme.errorContainer
                            },
                            contentColor = onStatusAccentColor
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = resolvedStatusSymbol,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shadowElevation = 2.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        YanindaIconBadge(
                            icon = YanindaIconType.MEDICATION,
                            size = 58.dp,
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.next_medication_label),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = nextMedicationTime,
                                    style = MaterialTheme.typography.headlineLarge,
                                    modifier = Modifier.clearAndSetSemantics {
                                        contentDescription = nextMedicationDescription
                                    }
                                )
                            }
                            if (nextMedicationName != null) {
                                Text(
                                    text = nextMedicationName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }

            if (onCallFamily != null) {
                item {
                    YanindaPrimaryButton(
                        text = stringResource(R.string.call_family),
                        onClick = onCallFamily,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp),
                        icon = YanindaIconType.PHONE,
                        minHeight = 80.dp,
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
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
private fun GrandfatherHomeScreenPreview() {
    YanindaTheme(darkTheme = false) {
        GrandfatherHomeScreen(
            dateText = stringResource(R.string.prototype_home_date),
            timeText = stringResource(R.string.prototype_home_time),
            statusText = stringResource(R.string.home_status_idle),
            nextMedicationTime = stringResource(R.string.prototype_next_medication_time),
            onCallFamily = {}
        )
    }
}
