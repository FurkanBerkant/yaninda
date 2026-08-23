package com.berkant.yaninda.ui.caregiver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.berkant.yaninda.R
import com.berkant.yaninda.ui.components.YanindaIconType
import com.berkant.yaninda.ui.components.YanindaOutlinedButton
import com.berkant.yaninda.ui.components.YanindaPrimaryButton

@Composable
internal fun FixedScheduleSafetyGateScreen(
    onFixedSchedule: () -> Unit,
    onUnsupported: () -> Unit,
    onBack: () -> Unit,
) {
    val title = stringResource(R.string.fixed_schedule_check_title)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .semantics { paneTitle = title },
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        CaregiverHeader(title = title, onBack = onBack, icon = YanindaIconType.MEDICATION)
        CaregiverNotice(
            title = stringResource(R.string.fixed_schedule_scope_title),
            body = stringResource(R.string.fixed_schedule_scope_body),
            isError = true,
        )
        Text(
            text = stringResource(R.string.fixed_schedule_question),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Start,
        )
        Text(
            text = stringResource(R.string.fixed_schedule_explanation),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        YanindaPrimaryButton(
            text = stringResource(R.string.fixed_schedule_yes),
            onClick = onFixedSchedule,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp),
            icon = YanindaIconType.CHECK,
        )
        YanindaOutlinedButton(
            text = stringResource(R.string.fixed_schedule_no),
            onClick = onUnsupported,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp),
            icon = YanindaIconType.WARNING,
            minHeight = 64.dp,
        )
    }
}

@Composable
internal fun UnsupportedScheduleScreen(onBack: () -> Unit) {
    val title = stringResource(R.string.unsupported_schedule_title)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .semantics { paneTitle = title },
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        CaregiverHeader(title = title, icon = YanindaIconType.WARNING)
        CaregiverNotice(
            title = stringResource(R.string.unsupported_schedule_notice_title),
            body = stringResource(R.string.unsupported_schedule_body),
            isError = true,
        )
        Text(
            text = stringResource(R.string.unsupported_schedule_result),
            style = MaterialTheme.typography.bodyLarge,
        )
        YanindaPrimaryButton(
            text = stringResource(R.string.back_to_medication_list),
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp),
            icon = YanindaIconType.MEDICATION,
        )
    }
}

@Composable
internal fun MissingMedicationScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        CaregiverHeader(
            title = stringResource(R.string.missing_medication_title),
            icon = YanindaIconType.WARNING,
        )
        Text(
            text = stringResource(R.string.missing_medication_body),
            style = MaterialTheme.typography.bodyLarge,
        )
        YanindaPrimaryButton(
            text = stringResource(R.string.back_to_medication_list),
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp),
            icon = YanindaIconType.MEDICATION,
        )
    }
}
