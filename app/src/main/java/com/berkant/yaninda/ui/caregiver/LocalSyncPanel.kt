package com.berkant.yaninda.ui.caregiver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.berkant.yaninda.R
import com.berkant.yaninda.sync.RemoteSyncReadiness
import com.berkant.yaninda.ui.components.YanindaCard
import com.berkant.yaninda.ui.components.YanindaIconType
import com.berkant.yaninda.ui.components.YanindaInfoRow
import com.berkant.yaninda.ui.components.YanindaSectionTitle
import com.berkant.yaninda.ui.components.YanindaStatusTone

@Composable
internal fun LocalSyncPanel(
    pendingOutboxCount: Int,
    remoteSyncReadiness: RemoteSyncReadiness,
) {
    YanindaCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            YanindaSectionTitle(
                title = stringResource(R.string.local_sync_panel_title),
                icon = YanindaIconType.SYNC,
            )
            Text(
                text = stringResource(R.string.local_sync_panel_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            YanindaInfoRow(
                label = stringResource(R.string.caregiver_sync_settings_title),
                value = pluralStringResource(
                    R.plurals.local_sync_pending_count,
                    pendingOutboxCount,
                    pendingOutboxCount,
                ),
                icon = YanindaIconType.SYNC,
                tone = if (pendingOutboxCount == 0) {
                    YanindaStatusTone.SUCCESS
                } else {
                    YanindaStatusTone.INFO
                },
            )
            YanindaInfoRow(
                label = stringResource(R.string.caregiver_pairing_settings_title),
                value = stringResource(remoteSyncReadiness.messageResource()),
                icon = YanindaIconType.FAMILY,
                tone = remoteSyncReadiness.statusTone(),
            )
        }
    }
}

private fun RemoteSyncReadiness.statusTone(): YanindaStatusTone = when (this) {
    RemoteSyncReadiness.READY -> YanindaStatusTone.SUCCESS
    RemoteSyncReadiness.UNAVAILABLE -> YanindaStatusTone.NEUTRAL
    RemoteSyncReadiness.AUTHENTICATION_REQUIRED,
    RemoteSyncReadiness.PAIRING_REQUIRED,
    RemoteSyncReadiness.PRIMARY_DEVICE_REQUIRED,
    -> YanindaStatusTone.WARNING
}

private fun RemoteSyncReadiness.messageResource(): Int = when (this) {
    RemoteSyncReadiness.UNAVAILABLE -> R.string.local_sync_remote_not_configured
    RemoteSyncReadiness.AUTHENTICATION_REQUIRED -> R.string.local_sync_auth_required
    RemoteSyncReadiness.PAIRING_REQUIRED -> R.string.local_sync_pairing_required
    RemoteSyncReadiness.PRIMARY_DEVICE_REQUIRED -> R.string.local_sync_primary_required
    RemoteSyncReadiness.READY -> R.string.local_sync_remote_configured
}
