package com.berkant.yaninda.ui.grandfather

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.berkant.yaninda.R
import com.berkant.yaninda.ui.alarm.MedicationAlarmItem
const val PROTOTYPE_SCREEN_EXTRA = "prototype_screen"

enum class PrototypeScreen {
    HOME,
    ALARM,
    CONFIRMATION;

    companion object {
        fun fromExtraOrNull(value: String?): PrototypeScreen? =
            entries.firstOrNull { screen -> screen.name.equals(value, ignoreCase = true) }

        fun fromExtra(value: String?): PrototypeScreen =
            fromExtraOrNull(value) ?: HOME
    }
}

private enum class PrototypeNotice {
    CALL_UNAVAILABLE,
    SNOOZE_UNAVAILABLE
}

@Composable
fun GrandfatherPrototypeApp(
    initialScreen: PrototypeScreen = PrototypeScreen.HOME,
    onCallFamily: () -> Boolean = { false },
) {
    var currentScreen by rememberSaveable(initialScreen) { mutableStateOf(initialScreen) }
    var fakeAcknowledgementRecorded by rememberSaveable { mutableStateOf(false) }
    var notice by rememberSaveable { mutableStateOf<PrototypeNotice?>(null) }
    val requestFamilyCall = {
        if (!onCallFamily()) notice = PrototypeNotice.CALL_UNAVAILABLE
    }

    when (currentScreen) {
        PrototypeScreen.HOME -> GrandfatherHomeScreen(
            dateText = stringResource(R.string.prototype_home_date),
            timeText = stringResource(R.string.prototype_home_time),
            statusText = stringResource(
                if (fakeAcknowledgementRecorded) {
                    R.string.home_status_recorded
                } else {
                    R.string.home_status_idle
                }
            ),
            nextMedicationTime = stringResource(R.string.prototype_next_medication_time),
            nextMedicationNames =
                listOf(
                    stringResource(R.string.prototype_medication_name)
                ),

            reminderHealthText = """
        Kesin alarm hazır
        Bildirimler hazır
        Tam ekran alarm hazır
        Planlanan: 1 • Kurulan: 1
        Sıradaki gerçek alarm: 14:30
    """.trimIndent(),

            reminderHealthy = true,

            onCallFamily = requestFamilyCall
        )

        PrototypeScreen.ALARM -> MedicationAlarmScreen(
            alarmTime =
                stringResource(R.string.prototype_alarm_time),

            medications =
                listOf(
                    MedicationAlarmItem(
                        medicationId = "prototype-medication",
                        medicationName =
                            stringResource(
                                R.string.prototype_medication_name
                            ),
                        dosageText =
                            stringResource(
                                R.string.prototype_dosage_text
                            ),
                        instructionText =
                            stringResource(
                                R.string.prototype_instruction_text
                            ),
                    )
                ),

            snoozeMinutes = 10,
            snoozeAvailable = true,
            isWorking = false,
            onTaken = {
                currentScreen =
                    PrototypeScreen.CONFIRMATION
            },
            onSnooze = {
                notice =
                    PrototypeNotice.SNOOZE_UNAVAILABLE
            },
            onCallFamily = requestFamilyCall,
        )

        PrototypeScreen.CONFIRMATION -> TakenConfirmation(
            onConfirmTaken = {
                fakeAcknowledgementRecorded = true
                currentScreen = PrototypeScreen.HOME
            },
            onNotTaken = { currentScreen = PrototypeScreen.ALARM }
        )
    }

    notice?.let { visibleNotice ->
        PrototypeNoticeDialog(
            title = stringResource(
                when (visibleNotice) {
                    PrototypeNotice.CALL_UNAVAILABLE -> R.string.call_unavailable_title
                    PrototypeNotice.SNOOZE_UNAVAILABLE -> R.string.prototype_notice_title
                }
            ),
            message = stringResource(
                when (visibleNotice) {
                    PrototypeNotice.CALL_UNAVAILABLE -> R.string.prototype_call_notice
                    PrototypeNotice.SNOOZE_UNAVAILABLE -> R.string.prototype_snooze_notice
                }
            ),
            onDismiss = { notice = null }
        )
    }
}

@Composable
private fun PrototypeNoticeDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
            ) {
                Text(text = stringResource(R.string.prototype_notice_dismiss))
            }
        }
    )
}
