package com.berkant.yaninda.ui.caregiver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.berkant.yaninda.R
import com.berkant.yaninda.ui.components.YanindaIconBadge
import com.berkant.yaninda.ui.components.YanindaIconType
import com.berkant.yaninda.ui.components.YanindaOutlinedButton

@Composable
internal fun CaregiverHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailingLabel: String? = null,
    onTrailing: (() -> Unit)? = null,
    icon: YanindaIconType = YanindaIconType.SETTINGS,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (onBack != null) {
            YanindaOutlinedButton(
                text = stringResource(R.string.caregiver_back),
                onClick = onBack,
                minHeight = 48.dp,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YanindaIconBadge(icon = icon, size = 48.dp)
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            if (trailingLabel != null && onTrailing != null) {
                YanindaOutlinedButton(
                    text = trailingLabel,
                    onClick = onTrailing,
                    icon = YanindaIconType.LOCK,
                    minHeight = 48.dp,
                )
            }
        }
    }
}

@Composable
internal fun CaregiverNotice(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        contentColor = if (isError) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YanindaIconBadge(
                icon = if (isError) YanindaIconType.WARNING else YanindaIconType.INFO,
                size = 44.dp,
                containerColor = if (isError) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                },
                iconColor = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(text = body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
