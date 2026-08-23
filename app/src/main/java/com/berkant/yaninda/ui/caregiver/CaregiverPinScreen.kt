package com.berkant.yaninda.ui.caregiver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.berkant.yaninda.R
import com.berkant.yaninda.ui.components.YanindaCard
import com.berkant.yaninda.ui.components.YanindaIconType
import com.berkant.yaninda.ui.components.YanindaPrimaryButton

@Composable
internal fun CaregiverPinScreen(
    pinConfigured: Boolean,
    pinError: CaregiverPinError?,
    isWorking: Boolean,
    onConfigurePin: (String, String) -> Unit,
    onUnlock: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var pin by rememberSaveable(pinConfigured) { mutableStateOf("") }
    var confirmation by rememberSaveable(pinConfigured) { mutableStateOf("") }
    val title = stringResource(
        if (pinConfigured) R.string.caregiver_pin_unlock_title else R.string.caregiver_pin_create_title
    )
    val submit = {
        focusManager.clearFocus()
        if (pinConfigured) onUnlock(pin) else onConfigurePin(pin, confirmation)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .semantics { paneTitle = title },
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        CaregiverHeader(title = title, icon = YanindaIconType.LOCK)
        CaregiverNotice(
            title = stringResource(R.string.caregiver_pin_notice_title),
            body = stringResource(
                if (pinConfigured) {
                    R.string.caregiver_pin_unlock_explanation
                } else {
                    R.string.caregiver_pin_create_explanation
                }
            ),
        )
        YanindaCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.onlyPinDigits() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.caregiver_pin_label)) },
                    supportingText = { Text(stringResource(R.string.caregiver_pin_format_hint)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = if (pinConfigured) ImeAction.Done else ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { submit() },
                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    ),
                    singleLine = true,
                    isError = pinError != null,
                    enabled = !isWorking,
                )
                if (!pinConfigured) {
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it.onlyPinDigits() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.caregiver_pin_confirmation_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        singleLine = true,
                        isError = pinError != null,
                        enabled = !isWorking,
                    )
                }
                pinError?.let { error ->
                    Text(
                        text = stringResource(error.messageResource()),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        YanindaPrimaryButton(
            text = stringResource(
                if (pinConfigured) {
                    R.string.caregiver_pin_unlock_action
                } else {
                    R.string.caregiver_pin_create_action
                }
            ),
            onClick = submit,
            modifier = Modifier.fillMaxWidth(),
            icon = YanindaIconType.LOCK,
            enabled = !isWorking,
        )
        CaregiverNotice(
            title = stringResource(R.string.caregiver_pin_recovery_title),
            body = stringResource(R.string.caregiver_pin_recovery_notice),
        )
    }
}

private fun String.onlyPinDigits(): String = filter(Char::isDigit).take(6)

private fun CaregiverPinError.messageResource(): Int = when (this) {
    CaregiverPinError.INVALID_FORMAT -> R.string.caregiver_pin_invalid
    CaregiverPinError.MISMATCH -> R.string.caregiver_pin_mismatch
    CaregiverPinError.INCORRECT -> R.string.caregiver_pin_incorrect
}
