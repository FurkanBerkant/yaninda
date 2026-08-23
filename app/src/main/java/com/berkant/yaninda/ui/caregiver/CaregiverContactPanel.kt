package com.berkant.yaninda.ui.caregiver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.berkant.yaninda.R
import com.berkant.yaninda.ui.components.YanindaCard
import com.berkant.yaninda.ui.components.YanindaIconType
import com.berkant.yaninda.ui.components.YanindaPrimaryButton
import com.berkant.yaninda.ui.components.YanindaSectionTitle

@Composable
internal fun CaregiverContactPanel(
    phoneNumber: String,
    isInvalid: Boolean,
    isSaved: Boolean,
    isWorking: Boolean,
    onPhoneNumberChanged: (String) -> Unit,
    onSave: () -> Unit,
) {
    YanindaCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            YanindaSectionTitle(
                title = stringResource(R.string.caregiver_contact_title),
                icon = YanindaIconType.PHONE,
            )
            Text(
                text = stringResource(R.string.caregiver_contact_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = onPhoneNumberChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                enabled = !isWorking,
                singleLine = true,
                label = { Text(stringResource(R.string.caregiver_contact_phone_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                isError = isInvalid,
                supportingText = {
                    when {
                        isInvalid -> Text(stringResource(R.string.caregiver_contact_invalid))
                        isSaved -> Text(stringResource(R.string.caregiver_contact_saved))
                        else -> Text(stringResource(R.string.caregiver_contact_hint))
                    }
                },
            )
            YanindaPrimaryButton(
                text = stringResource(R.string.caregiver_contact_save),
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                enabled = !isWorking,
                icon = YanindaIconType.CHECK,
            )
        }
    }
}
