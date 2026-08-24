package com.berkant.yaninda.ui.alarm

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.berkant.yaninda.core.time.TimeProvider
import com.berkant.yaninda.data.contact.CaregiverContactRepository
import com.berkant.yaninda.data.repository.DoseOccurrenceRepository
import com.berkant.yaninda.data.repository.MedicationRepository
import com.berkant.yaninda.domain.occurrence.DoseOccurrenceStatus
import com.berkant.yaninda.notification.ReminderNotifier
import com.berkant.yaninda.reminder.ReminderCoordinator
import com.berkant.yaninda.reminder.ReminderSnoozeResult
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MedicationAlarmDestination {
    ALARM,
    TAKEN_CONFIRMATION,
}

internal enum class MedicationAlarmBackAction {
    RETURN_TO_ALARM,
    MOVE_TASK_TO_BACKGROUND,
}

internal fun resolveMedicationAlarmBackAction(
    destination: MedicationAlarmDestination,
): MedicationAlarmBackAction = when (destination) {
    MedicationAlarmDestination.TAKEN_CONFIRMATION ->
        MedicationAlarmBackAction.RETURN_TO_ALARM

    MedicationAlarmDestination.ALARM ->
        MedicationAlarmBackAction.MOVE_TASK_TO_BACKGROUND
}

enum class MedicationAlarmMessage {
    CAREGIVER_PHONE_MISSING,
    DIALER_UNAVAILABLE,
    EXACT_ALARM_ACCESS_REQUIRED,
    SNOOZE_SETUP_FAILED,
    ACKNOWLEDGEMENT_FAILED,
}
data class MedicationAlarmItem(
    val medicationId: String,
    val medicationName: String,
    val dosageText: String,
    val instructionText: String,
)
sealed interface MedicationAlarmCompletion {
    data object Acknowledged : MedicationAlarmCompletion

    data class Snoozed(
        val reminderTime: String,
    ) : MedicationAlarmCompletion
}

data class MedicationAlarmContent(
    val alarmTime: String,
    val medications: List<MedicationAlarmItem>,
    val snoozeMinutes: Int,
    val maxSnoozes: Int,
    val snoozeAvailable: Boolean,
    internal val caregiverPhoneNumber: String?,
)

data class MedicationAlarmUiState(
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val closeRequested: Boolean = false,
    val loadFailed: Boolean = false,
    val destination: MedicationAlarmDestination = MedicationAlarmDestination.ALARM,
    val content: MedicationAlarmContent? = null,
    val message: MedicationAlarmMessage? = null,
    val completion: MedicationAlarmCompletion? = null,
)

class MedicationAlarmViewModel(
    private val occurrenceId: String,
    private val occurrenceRepository: DoseOccurrenceRepository,
    private val medicationRepository: MedicationRepository,
    private val contactRepository: CaregiverContactRepository,
    private val reminderCoordinator: ReminderCoordinator,
    private val reminderNotifier: ReminderNotifier,
    private val timeProvider: TimeProvider,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MedicationAlarmUiState())
    val state: StateFlow<MedicationAlarmUiState> = mutableState.asStateFlow()

    init {
        load()
    }

    fun requestTakenConfirmation() {
        if (mutableState.value.content == null || mutableState.value.isWorking) return
        mutableState.update {
            it.copy(
                destination = MedicationAlarmDestination.TAKEN_CONFIRMATION,
                message = null,
            )
        }
    }

    fun returnToAlarm() {
        if (mutableState.value.isWorking) return
        mutableState.update {
            it.copy(
                destination = MedicationAlarmDestination.ALARM,
                message = null,
            )
        }
    }

    fun confirmTaken() {
        if (mutableState.value.isWorking || mutableState.value.content == null) return
        runAction(failureMessage = MedicationAlarmMessage.ACKNOWLEDGEMENT_FAILED) {
            reminderCoordinator.acknowledgeTaken(occurrenceId)
            mutableState.update {
                it.copy(
                    isWorking = false,
                    message = null,
                    completion = MedicationAlarmCompletion.Acknowledged,
                )
            }
        }
    }

    fun snooze() {
        val content = mutableState.value.content ?: return
        if (mutableState.value.isWorking || !content.snoozeAvailable) return
        runAction(failureMessage = MedicationAlarmMessage.SNOOZE_SETUP_FAILED) {
            handleSnoozeResult(
                result = reminderCoordinator.snoozeOccurrence(
                    occurrenceId = occurrenceId,
                    snoozeMinutes = content.snoozeMinutes,
                    maxSnoozes = content.maxSnoozes,
                ),
                content = content.copy(snoozeAvailable = false),
            )
        }
    }

    fun callTargetOrShowMessage(): String? {
        val phoneNumber = mutableState.value.content?.caregiverPhoneNumber
        if (phoneNumber == null) {
            mutableState.update { it.copy(message = MedicationAlarmMessage.CAREGIVER_PHONE_MISSING) }
        }
        return phoneNumber
    }

    fun reportDialerUnavailable() {
        mutableState.update { it.copy(message = MedicationAlarmMessage.DIALER_UNAVAILABLE) }
    }

    fun dismissMessage() {
        mutableState.update { it.copy(message = null) }
    }

    private fun load() {
        viewModelScope.launch {

            try {

                val occurrences =
                    occurrenceRepository
                        .getOccurrencesForDoseGroup(
                            occurrenceId
                        )

                if (occurrences.isEmpty()) {

                    reminderNotifier
                        .cancelMedicationReminder(
                            occurrenceId
                        )

                    mutableState.value =
                        MedicationAlarmUiState(
                            isLoading = false,
                            closeRequested = true,
                        )

                    return@launch
                }

                val group =
                    occurrenceRepository
                        .getDoseGroupForOccurrence(
                            occurrenceId
                        )

                if (group == null) {

                    mutableState.value =
                        MedicationAlarmUiState(
                            isLoading = false,
                            loadFailed = true,
                        )

                    return@launch
                }

                val caregiverPhone =
                    loadCaregiverPhoneOrNull()

                val schedules =
                    occurrences.mapNotNull { occurrence ->

                        val configuration =
                            medicationRepository.get(
                                occurrence.medicationId
                            ) ?: return@mapNotNull null

                        val schedule =
                            configuration
                                .schedules
                                .firstOrNull {
                                    it.id ==
                                            occurrence.scheduleId
                                }
                                ?: return@mapNotNull null

                        occurrence to schedule
                    }

                /*
                 * Snooze ancak grubun tamamı için
                 * güvenli ve deterministik olduğunda
                 * gösterilsin.
                 *
                 * Farklı ilaçlarda farklı snooze
                 * ayarları varsa birini rastgele
                 * seçmiyoruz.
                 */
                val commonSnoozeMinutes =
                    schedules
                        .map {
                            it.second.snoozeMinutes
                        }
                        .distinct()
                        .singleOrNull()

                val commonMaxSnoozes =
                    schedules
                        .map {
                            it.second.maxSnoozes
                        }
                        .distinct()
                        .singleOrNull()

                val everyScheduleAllowsSnooze =
                    schedules.size ==
                            occurrences.size &&
                            schedules.all {
                                it.second.snoozeEnabled
                            }

                val snoozeAvailable =
                    everyScheduleAllowsSnooze &&
                            commonSnoozeMinutes != null &&
                            commonMaxSnoozes != null &&
                            occurrences.all {
                                    occurrence ->
                                occurrence.status ==
                                        DoseOccurrenceStatus.DUE &&
                                        occurrence.snoozeCount <
                                        commonMaxSnoozes
                            }

                val content =
                    MedicationAlarmContent(
                        alarmTime =
                            group.scheduledAt
                                .toAlarmTime(),

                        medications =
                            group.items.map { item ->
                                MedicationAlarmItem(
                                    medicationId =
                                        item.medicationId,

                                    medicationName =
                                        item.medicationDisplayName,

                                    dosageText =
                                        item.dosageText,

                                    instructionText =
                                        item.instructionText,
                                )
                            },

                        snoozeMinutes =
                            commonSnoozeMinutes ?: 0,

                        maxSnoozes =
                            commonMaxSnoozes ?: 0,

                        snoozeAvailable =
                            snoozeAvailable,

                        caregiverPhoneNumber =
                            caregiverPhone,
                    )

                val statuses =
                    occurrences
                        .map {
                            it.status
                        }
                        .toSet()

                when {

                    /*
                     * Grup zaten alınmışsa alarmı
                     * tekrar göstermiyoruz.
                     */
                    statuses.all {
                        it ==
                                DoseOccurrenceStatus
                                    .ACKNOWLEDGED_TAKEN
                    } -> {

                        reminderNotifier
                            .cancelMedicationReminder(
                                occurrenceId
                            )

                        mutableState.value =
                            MedicationAlarmUiState(
                                isLoading = false,
                                content = content,
                                completion =
                                    MedicationAlarmCompletion
                                        .Acknowledged,
                            )
                    }

                    /*
                     * En az bir actionable occurrence
                     * varsa alarm ekranını göster.
                     */
                    statuses.any {
                        it == DoseOccurrenceStatus.DUE ||
                                it ==
                                DoseOccurrenceStatus
                                    .NO_CONFIRMATION ||
                                it ==
                                DoseOccurrenceStatus
                                    .SNOOZED
                    } -> {

                        mutableState.value =
                            MedicationAlarmUiState(
                                isLoading = false,
                                content = content,
                            )
                    }

                    else -> {

                        reminderNotifier
                            .cancelMedicationReminder(
                                occurrenceId
                            )

                        mutableState.value =
                            MedicationAlarmUiState(
                                isLoading = false,
                                closeRequested = true,
                            )
                    }
                }

            } catch (error: CancellationException) {
                throw error

            } catch (_: Exception) {

                mutableState.value =
                    MedicationAlarmUiState(
                        isLoading = false,
                        loadFailed = true,
                    )
            }
        }
    }

    private fun runAction(
        failureMessage: MedicationAlarmMessage,
        block: suspend () -> Unit,
    ) {
        mutableState.update { it.copy(isWorking = true, message = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(
                        isWorking = false,
                        message = failureMessage,
                    )
                }
            }
        }
    }

    private suspend fun loadCaregiverPhoneOrNull(): String? = try {
        contactRepository.phoneNumber.first()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        Log.w(TAG, "Caregiver call target is unavailable.")
        null
    }

    private fun handleSnoozeResult(
        result: ReminderSnoozeResult,
        content: MedicationAlarmContent,
    ) {
        when (result) {
            is ReminderSnoozeResult.Scheduled -> mutableState.update {
                it.copy(
                    isWorking = false,
                    content = content,
                    message = null,
                    completion = MedicationAlarmCompletion.Snoozed(
                        result.triggerAt.toAlarmTime()
                    ),
                )
            }

            is ReminderSnoozeResult.ExactAlarmUnavailable -> mutableState.update {
                it.copy(
                    isWorking = false,
                    content = content,
                    message = MedicationAlarmMessage.EXACT_ALARM_ACCESS_REQUIRED,
                )
            }

            ReminderSnoozeResult.NotActionable,
            ReminderSnoozeResult.PlatformFailure,
            -> mutableState.update {
                it.copy(
                    isWorking = false,
                    content = content,
                    message = MedicationAlarmMessage.SNOOZE_SETUP_FAILED,
                )
            }
        }
    }

    private fun Instant.toAlarmTime(): String = ALARM_TIME_FORMATTER.format(
        atZone(timeProvider.currentZoneId())
    )

    class Factory(
        private val occurrenceId: String,
        private val occurrenceRepository: DoseOccurrenceRepository,
        private val medicationRepository: MedicationRepository,
        private val contactRepository: CaregiverContactRepository,
        private val reminderCoordinator: ReminderCoordinator,
        private val reminderNotifier: ReminderNotifier,
        private val timeProvider: TimeProvider,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(MedicationAlarmViewModel::class.java))
            return MedicationAlarmViewModel(
                occurrenceId = occurrenceId,
                occurrenceRepository = occurrenceRepository,
                medicationRepository = medicationRepository,
                contactRepository = contactRepository,
                reminderCoordinator = reminderCoordinator,
                reminderNotifier = reminderNotifier,
                timeProvider = timeProvider,
            ) as T
        }
    }

    companion object {
        private const val TAG = "MedicationAlarm"
        private val ALARM_TIME_FORMATTER = DateTimeFormatter.ofPattern(
            "HH:mm",
            Locale.forLanguageTag("tr-TR"),
        )
    }
}
