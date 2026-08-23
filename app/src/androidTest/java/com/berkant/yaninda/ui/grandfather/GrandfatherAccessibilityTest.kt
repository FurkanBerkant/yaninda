package com.berkant.yaninda.ui.grandfather

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.berkant.yaninda.ui.theme.YanindaTheme
import org.junit.Rule
import org.junit.Test

class GrandfatherAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun home_hasReadableSemanticsAndLargeCallTarget() {
        composeRule.setContent {
            YanindaTheme(darkTheme = false) {
                GrandfatherHomeScreen(
                    dateText = "22 Ağustos Cumartesi",
                    timeText = "18:10",
                    statusText = "Şu anda ilaç zamanı değil.",
                    nextMedicationTime = "20:00",
                    reminderHealthText = "Alarm sistemi hazır",
                    reminderHealthy = true,
                    onCallFamily = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Saat 18:10").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Sıradaki ilaç: 20:00").assertIsDisplayed()
        composeRule.onNodeWithText("AİLEYİ ARA").assertHeightIsAtLeast(80.dp)
    }

    @Test
    fun home_hidesCallActionWhenFamilyPhoneIsNotConfigured() {
        composeRule.setContent {
            YanindaTheme(darkTheme = false) {
                GrandfatherHomeScreen(
                    dateText = "22 Ağustos Cumartesi",
                    timeText = "18:10",
                    statusText = "Şu anda ilaç zamanı değil.",
                    nextMedicationTime = "20:00",
                    reminderHealthText = "Alarm sistemi hazır",
                    reminderHealthy = true,
                    onCallFamily = null,
                )
            }
        }

        composeRule.onNodeWithText("AİLEYİ ARA").assertDoesNotExist()
    }

    @Test
    fun alarm_hasTextLabelsAndNoCriticalTargetBelow64Dp() {
        composeRule.setContent {
            YanindaTheme(darkTheme = false) {
                MedicationAlarmScreen(
                    alarmTime = "20:00",
                    medicationName = "Şeker İlacı",
                    dosageText = "1 tablet",
                    instructionText = "Yemekten sonra",
                    snoozeMinutes = 10,
                    snoozeAvailable = true,
                    isWorking = false,
                    onTaken = {},
                    onSnooze = {},
                    onCallFamily = {},
                )
            }
        }

        composeRule.onNodeWithText("İLACIMI ALDIM").assertHeightIsAtLeast(80.dp)
        composeRule.onNodeWithText("10 DAKİKA SONRA HATIRLAT")
            .assertHeightIsAtLeast(64.dp)
        composeRule.onNodeWithText("AİLEYİ ARA").assertHeightIsAtLeast(64.dp)
    }

    @Test
    fun confirmation_actionsAreLargeAndVisuallySeparate() {
        composeRule.setContent {
            YanindaTheme(darkTheme = false) {
                TakenConfirmation(
                    onConfirmTaken = {},
                    onNotTaken = {},
                )
            }
        }

        composeRule.onNodeWithText("İlacını aldın mı?").assertIsDisplayed()
        composeRule.onNodeWithText("EVET, ALDIM").assertHeightIsAtLeast(80.dp)
        composeRule.onNodeWithText("HAYIR").assertHeightIsAtLeast(72.dp)
    }
}
