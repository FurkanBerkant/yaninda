package com.berkant.yaninda.ui.alarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.berkant.yaninda.MainActivity
import com.berkant.yaninda.R
import com.berkant.yaninda.YanindaApplication
import com.berkant.yaninda.core.phone.openPhoneDialer
import com.berkant.yaninda.reminder.AlarmActivityLaunch
import com.berkant.yaninda.reminder.AlarmIntentFactory
import com.berkant.yaninda.reminder.MedicationAlarmAttentionService
import com.berkant.yaninda.ui.grandfather.MedicationAlarmScreen
import com.berkant.yaninda.ui.theme.YanindaTheme
import kotlinx.coroutines.delay

class MedicationAlarmActivity :
    ComponentActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?,
    ) {

        super.onCreate(savedInstanceState)

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O_MR1
        ) {

            setShowWhenLocked(true)
            setTurnScreenOn(true)

        } else {

            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams
                    .FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams
                            .FLAG_TURN_SCREEN_ON
            )
        }

        window.addFlags(
            WindowManager.LayoutParams
                .FLAG_KEEP_SCREEN_ON
        )

        enableEdgeToEdge()

        val launch =
            AlarmIntentFactory
                .alarmActivityLaunch(intent)

        if (launch == null) {
            finish()
            return
        }

        setContent {

            YanindaTheme {

                when (launch) {

                    is AlarmActivityLaunch.Medication -> {

                        MedicationAlarmRoute(
                            occurrenceId =
                                launch.occurrenceId,
                        )
                    }

                    AlarmActivityLaunch.Test -> {
                        TestAlarmRoute()
                    }
                }
            }
        }
    }

    @Composable
    private fun MedicationAlarmRoute(
        occurrenceId: String,
    ) {

        val application =
            application
                    as YanindaApplication

        val factory =
            remember(
                application,
                occurrenceId,
            ) {

                MedicationAlarmViewModel
                    .Factory(
                        occurrenceId =
                            occurrenceId,
                        occurrenceRepository =
                            application
                                .doseOccurrenceRepository,
                        medicationRepository =
                            application
                                .medicationRepository,
                        contactRepository =
                            application
                                .caregiverContactRepository,
                        reminderCoordinator =
                            application
                                .reminderCoordinator,
                        reminderNotifier =
                            application
                                .reminderNotifier,
                        timeProvider =
                            application
                                .timeProvider,
                    )
            }

        val viewModel:
                MedicationAlarmViewModel =
            viewModel(
                key =
                    occurrenceId,
                factory =
                    factory,
            )

        val state by
        viewModel
            .state
            .collectAsStateWithLifecycle()

        /*
         * The active medication window consumes Back.
         *
         * Android Home/system navigation cannot be fully blocked without
         * kiosk/device-owner mode, but Back must not dismiss the alarm.
         */
        BackHandler(
            enabled =
                state.activeUntil != null &&
                    state.completion == null
        ) {
            when (
                resolveMedicationAlarmBackAction()
            ) {
                MedicationAlarmBackAction.CONSUME -> Unit
            }
        }

        LaunchedEffect(
            state.closeRequested
        ) {

            if (
                state.closeRequested
            ) {

                MedicationAlarmAttentionService
                    .stop(
                        this@MedicationAlarmActivity
                    )

                finish()
            }
        }

        LaunchedEffect(
            state.activeUntil,
            state.completion,
        ) {
            if (state.completion != null) {
                return@LaunchedEffect
            }

            val activeUntil =
                state.activeUntil
                    ?: return@LaunchedEffect

            val remainingMillis =
                (
                    activeUntil.toEpochMilli() -
                        application
                            .timeProvider
                            .now()
                            .toEpochMilli()
                    )
                    .coerceAtLeast(0L)

            delay(remainingMillis)

            MedicationAlarmAttentionService
                .stop(
                    this@MedicationAlarmActivity
                )

            /*
             * If this Activity was opened over the lock screen, finish()
             * returns to the lock screen. If MainActivity is underneath,
             * it returns to the normal grandfather home.
             */
            finish()
        }

        /*
         * Hem ACKNOWLEDGED hem SNOOZED burada
         * completion üretir.
         *
         * Dolayısıyla:
         *
         * İLACIMI ALDIM
         * veya
         * ERTELE
         *
         * başarılı olduğunda ses/titreşim
         * anında durur.
         */
        LaunchedEffect(
            state.completion
        ) {

            if (
                state.completion != null
            ) {

                MedicationAlarmAttentionService
                    .stop(
                        this@MedicationAlarmActivity
                    )

                delay(
                    RESULT_VISIBLE_MILLIS
                )

                returnToGrandfatherHome()
            }
        }

        when {

            state.isLoading -> {
                AlarmLoadingScreen()
            }

            state.completion != null -> {

                AlarmResultScreen(
                    checkNotNull(
                        state.completion
                    )
                )
            }

            state.loadFailed -> {

                AlarmLoadFailureScreen(
                    onCallFamily = {

                        viewModel
                            .callTargetOrShowMessage()
                            ?.let {
                                    phoneNumber ->

                                if (
                                    !openPhoneDialer(
                                        phoneNumber
                                    )
                                ) {
                                    viewModel
                                        .reportDialerUnavailable()
                                }
                            }
                    },
                    onClose = {

                        MedicationAlarmAttentionService
                            .stop(
                                this@MedicationAlarmActivity
                            )

                        returnToGrandfatherHome()
                    },
                )
            }

            state.content != null -> {

                val content =
                    checkNotNull(
                        state.content
                    )

                MedicationAlarmScreen(
                    alarmTime =
                        content.alarmTime,
                    medications =
                        content.medications,
                    snoozeMinutes =
                        content.snoozeMinutes,
                    snoozeAvailable =
                        content.snoozeAvailable,
                    isWorking =
                        state.isWorking,

                    /*
                     * Single-tap acknowledgement:
                     * there is intentionally no second confirmation screen.
                     */
                    onTaken =
                        viewModel::confirmTaken,

                    onSnooze =
                        viewModel::snooze,

                    onCallFamily = {

                        viewModel
                            .callTargetOrShowMessage()
                            ?.let {
                                    phoneNumber ->

                                if (
                                    !openPhoneDialer(
                                        phoneNumber
                                    )
                                ) {

                                    viewModel
                                        .reportDialerUnavailable()
                                }
                            }
                    },
                )
            }

            else -> {
                AlarmLoadingScreen()
            }
        }

        state.message
            ?.let {
                    message ->

                AlarmMessageDialog(
                    message =
                        message,
                    onDismiss =
                        viewModel::dismissMessage,
                )
            }
    }

    @Composable
    private fun TestAlarmRoute() {

        BackHandler {
            moveTaskToBack(true)
        }

        AlarmTestScreen(
            onFinish = {

                (
                        application
                                as YanindaApplication
                        )
                    .reminderNotifier
                    .cancelTestReminder()

                finish()
            }
        )
    }

    private fun returnToGrandfatherHome() {

        val homeIntent =
            Intent(
                this,
                MainActivity::class.java,
            ).apply {
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

        startActivity(
            homeIntent
        )

        finish()
    }

    /*
     * Burada alarm service'ini DURDURMUYORUZ.
     *
     * Activity sistem tarafından kapanırsa,
     * rotation/recreation yaşarsa veya kullanıcı
     * Home'a basarsa ilaç alarmı susmamalı.
     *
     * Sadece acknowledge / snooze / response-window
     * gibi gerçek domain olayları sesi durdurur.
     */
    override fun onDestroy() {
        super.onDestroy()
    }

    private companion object {

        const val RESULT_VISIBLE_MILLIS =
            1_500L
    }
}

@Composable
private fun AlarmMessageDialog(
    message: MedicationAlarmMessage,
    onDismiss: () -> Unit,
) {

    val messageText =
        stringResource(
            when (message) {

                MedicationAlarmMessage
                    .CAREGIVER_PHONE_MISSING ->
                    R.string
                        .alarm_caregiver_phone_missing

                MedicationAlarmMessage
                    .DIALER_UNAVAILABLE ->
                    R.string
                        .alarm_dialer_unavailable

                MedicationAlarmMessage
                    .EXACT_ALARM_ACCESS_REQUIRED ->
                    R.string
                        .alarm_exact_access_required

                MedicationAlarmMessage
                    .SNOOZE_SETUP_FAILED ->
                    R.string
                        .alarm_snooze_failed

                MedicationAlarmMessage
                    .ACKNOWLEDGEMENT_FAILED ->
                    R.string
                        .alarm_acknowledgement_failed
            }
        )

    AlertDialog(
        onDismissRequest =
            onDismiss,
        title = {

            Text(
                stringResource(
                    R.string
                        .alarm_message_title
                )
            )
        },
        text = {

            Text(
                messageText
            )
        },
        confirmButton = {

            Button(
                onClick =
                    onDismiss,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(
                            min = 56.dp
                        ),
            ) {

                Text(
                    stringResource(
                        R.string
                            .common_ok
                    )
                )
            }
        },
    )
}
