package com.berkant.yaninda.ui.alarm

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.berkant.yaninda.R
import com.berkant.yaninda.ui.components.YanindaIconBadge
import com.berkant.yaninda.ui.components.YanindaIconType
import com.berkant.yaninda.ui.components.YanindaOutlinedButton
import com.berkant.yaninda.ui.components.YanindaPrimaryButton

@Composable
internal fun AlarmLoadingScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(56.dp))
                Text(
                    text = stringResource(R.string.alarm_loading),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
internal fun AlarmLoadFailureScreen(
    onCallFamily: () -> Unit,
    onClose: () -> Unit,
) {
    val paneTitle = stringResource(R.string.alarm_details_unavailable_title)
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .semantics { this.paneTitle = paneTitle },
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                YanindaIconBadge(
                    icon = YanindaIconType.WARNING,
                    size = 92.dp,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    iconColor = MaterialTheme.colorScheme.error,
                )
            }
            item {
                Text(
                    text = paneTitle,
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
            }
            item {
                Text(
                    text = stringResource(R.string.alarm_details_unavailable_body),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
            item {
                YanindaPrimaryButton(
                    text = stringResource(R.string.call_family),
                    onClick = onCallFamily,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp),
                    icon = YanindaIconType.PHONE,
                    minHeight = 72.dp,
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                )
            }
            item {
                YanindaOutlinedButton(
                    text = stringResource(R.string.alarm_close),
                    onClick = onClose,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp),
                    minHeight = 64.dp,
                )
            }
        }
    }
}

@Composable
internal fun AlarmResultScreen(
    completion: MedicationAlarmCompletion,
) {
    val acknowledged = completion is MedicationAlarmCompletion.Acknowledged
    val title = if (acknowledged) {
        stringResource(R.string.alarm_acknowledged_title)
    } else {
        stringResource(R.string.alarm_snoozed_title)
    }
    val body = when (completion) {
        MedicationAlarmCompletion.Acknowledged ->
            stringResource(R.string.alarm_acknowledged_body)

        is MedicationAlarmCompletion.Snoozed ->
            stringResource(R.string.alarm_snoozed_body, completion.reminderTime)
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 36.dp)
                .semantics {
                    paneTitle = title
                    liveRegion = LiveRegionMode.Assertive
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            YanindaIconBadge(
                icon = if (acknowledged) YanindaIconType.CHECK else YanindaIconType.CLOCK,
                size = 104.dp,
                containerColor = if (acknowledged) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.primary
                },
                iconColor = if (acknowledged) {
                    MaterialTheme.colorScheme.onTertiary
                } else {
                    MaterialTheme.colorScheme.onPrimary
                },
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = body,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun AlarmTestScreen(
    onFinish: () -> Unit,
) {
    val paneTitle = stringResource(R.string.alarm_test_screen_title)
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .semantics { this.paneTitle = paneTitle },
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.error),
                ) {
                    Text(
                        text = paneTitle,
                        modifier = Modifier
                            .padding(horizontal = 18.dp, vertical = 28.dp)
                            .semantics { heading() },
                        style = MaterialTheme.typography.headlineLarge,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            item {
                Text(
                    text = stringResource(R.string.alarm_test_screen_body),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
            item {
                Button(
                    onClick = onFinish,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Text(
                        text = stringResource(R.string.alarm_test_finish),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}
