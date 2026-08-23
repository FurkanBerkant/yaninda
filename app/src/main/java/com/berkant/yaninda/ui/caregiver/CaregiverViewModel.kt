package com.berkant.yaninda.ui.caregiver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.berkant.yaninda.data.contact.CaregiverContactRepository
import com.berkant.yaninda.data.repository.MedicationRepository
import com.berkant.yaninda.data.repository.SyncOutboxRepository
import com.berkant.yaninda.data.security.CaregiverPinRepository
import com.berkant.yaninda.domain.contact.CaregiverPhoneNumberValidation
import com.berkant.yaninda.domain.contact.CaregiverPhoneNumberValidator
import com.berkant.yaninda.domain.medication.MedicationConfiguration
import com.berkant.yaninda.domain.medication.MedicationDraft
import com.berkant.yaninda.domain.medication.MedicationDraftError
import com.berkant.yaninda.domain.medication.MedicationDraftValidator
import com.berkant.yaninda.notification.FullScreenIntentCapability
import com.berkant.yaninda.notification.NotificationCapability
import com.berkant.yaninda.reminder.ExactAlarmCapability
import com.berkant.yaninda.reminder.ReminderCoordinator
import com.berkant.yaninda.reminder.ReminderRuntimeStatus
import com.berkant.yaninda.reminder.ReminderTestResult
import com.berkant.yaninda.reliability.DeviceReliabilityChecker
import com.berkant.yaninda.reliability.DeviceReliabilityStatus
import com.berkant.yaninda.sync.RemoteSyncDataSource
import com.berkant.yaninda.sync.RemoteSyncReadiness
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CaregiverDestination {
    MEDICATION_LIST,
    FIXED_SCHEDULE_CHECK,
    UNSUPPORTED_SCHEDULE,
    MEDICATION_FORM,
}

enum class CaregiverPinError {
    INVALID_FORMAT,
    MISMATCH,
    INCORRECT,
}

enum class ReminderFeedback {
    TEST_SCHEDULED,
    TEST_CANCELLED,
    EXACT_ALARM_ACCESS_REQUIRED,
    NOTIFICATION_ACCESS_REQUIRED,
    REMINDER_SETUP_NEEDS_ATTENTION,
    OPERATION_FAILED,
}

data class CaregiverUiState(
    val pinConfigured: Boolean? = null,
    val unlocked: Boolean = false,
    val destination: CaregiverDestination = CaregiverDestination.MEDICATION_LIST,
    val configurations: List<MedicationConfiguration> = emptyList(),
    val selectedMedicationId: String? = null,
    val pinError: CaregiverPinError? = null,
    val formErrors: Set<MedicationDraftError> = emptySet(),
    val isWorking: Boolean = false,
    val isReminderWorking: Boolean = false,
    val reminderStatus: ReminderRuntimeStatus = ReminderRuntimeStatus(),
    val deviceReliabilityStatus: DeviceReliabilityStatus = DeviceReliabilityStatus(),
    val pendingOutboxCount: Int = 0,
    val remoteSyncReadiness: RemoteSyncReadiness = RemoteSyncReadiness.UNAVAILABLE,
    val reminderFeedback: ReminderFeedback? = null,
    val caregiverPhoneDraft: String = "",
    val caregiverPhoneDirty: Boolean = false,
    val caregiverPhoneInvalid: Boolean = false,
    val caregiverPhoneSaved: Boolean = false,
    val operationMessage: String? = null,
)

class CaregiverViewModel(
    private val medicationRepository: MedicationRepository,
    private val pinRepository: CaregiverPinRepository,
    private val contactRepository: CaregiverContactRepository,
    private val reminderCoordinator: ReminderCoordinator,
    private val deviceReliabilityChecker: DeviceReliabilityChecker,
    private val syncOutboxRepository: SyncOutboxRepository,
    remoteSyncDataSource: RemoteSyncDataSource,
    private val validator: MedicationDraftValidator = MedicationDraftValidator(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(CaregiverUiState())
    val state: StateFlow<CaregiverUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            remoteSyncDataSource.readiness.collect { readiness ->
                mutableState.update { current ->
                    current.copy(remoteSyncReadiness = readiness)
                }
            }
        }
        viewModelScope.launch {
            try {
                pinRepository.isConfigured.collect { configured ->
                    mutableState.update { current ->
                        current.copy(
                            pinConfigured = configured,
                            unlocked = current.unlocked && configured,
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                showOperationFailure()
            }
        }
        refreshReminderStatus()
        viewModelScope.launch {
            reminderCoordinator.status.collect { reminderStatus ->
                mutableState.update { current ->
                    current.copy(reminderStatus = reminderStatus)
                }
            }
        }
        viewModelScope.launch {
            try {
                contactRepository.phoneNumber.collect { phoneNumber ->
                    mutableState.update { current ->
                        if (current.caregiverPhoneDirty) {
                            current
                        } else {
                            current.copy(caregiverPhoneDraft = phoneNumber.orEmpty())
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                showOperationFailure()
            }
        }
        viewModelScope.launch {
            try {
                medicationRepository.configurations.collect { configurations ->
                    mutableState.update { current -> current.copy(configurations = configurations) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                showOperationFailure()
            }
        }
        viewModelScope.launch {
            try {
                syncOutboxRepository.pendingCount.collect { pendingCount ->
                    mutableState.update { current ->
                        current.copy(pendingOutboxCount = pendingCount)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                showOperationFailure()
            }
        }
    }

    fun configurePin(pin: String, confirmation: String) {
        when {
            !PIN_PATTERN.matches(pin) -> {
                mutableState.update { it.copy(pinError = CaregiverPinError.INVALID_FORMAT) }
            }

            pin != confirmation -> {
                mutableState.update { it.copy(pinError = CaregiverPinError.MISMATCH) }
            }

            else -> runOperation {
                pinRepository.configure(pin)
                mutableState.update {
                    it.copy(
                        pinConfigured = true,
                        unlocked = true,
                        destination = CaregiverDestination.MEDICATION_LIST,
                        pinError = null,
                    )
                }
            }
        }
    }

    fun unlock(pin: String) {
        if (!PIN_PATTERN.matches(pin)) {
            mutableState.update { it.copy(pinError = CaregiverPinError.INVALID_FORMAT) }
            return
        }
        runOperation {
            if (pinRepository.verify(pin)) {
                mutableState.update {
                    it.copy(
                        unlocked = true,
                        destination = CaregiverDestination.MEDICATION_LIST,
                        pinError = null,
                    )
                }
            } else {
                mutableState.update { it.copy(pinError = CaregiverPinError.INCORRECT) }
            }
        }
    }

    fun lock() {
        mutableState.update {
            it.copy(
                unlocked = false,
                destination = CaregiverDestination.MEDICATION_LIST,
                selectedMedicationId = null,
                pinError = null,
                formErrors = emptySet(),
            )
        }
    }

    fun startAddingMedication() {
        checkUnlocked()
        mutableState.update {
            it.copy(
                destination = CaregiverDestination.FIXED_SCHEDULE_CHECK,
                selectedMedicationId = null,
                formErrors = emptySet(),
            )
        }
    }

    fun confirmFixedSchedule() {
        checkUnlocked()
        mutableState.update {
            it.copy(
                destination = CaregiverDestination.MEDICATION_FORM,
                selectedMedicationId = null,
                formErrors = emptySet(),
            )
        }
    }

    fun rejectUnsupportedSchedule() {
        checkUnlocked()
        mutableState.update {
            it.copy(destination = CaregiverDestination.UNSUPPORTED_SCHEDULE)
        }
    }

    fun editMedication(medicationId: String) {
        checkUnlocked()
        check(mutableState.value.configurations.any { it.medication.id == medicationId }) {
            "The selected medication does not exist."
        }
        mutableState.update {
            it.copy(
                destination = CaregiverDestination.MEDICATION_FORM,
                selectedMedicationId = medicationId,
                formErrors = emptySet(),
            )
        }
    }

    fun saveMedication(draft: MedicationDraft) {
        checkUnlocked()
        val validation = validator.validate(draft)
        if (!validation.isValid) {
            mutableState.update { it.copy(formErrors = validation.errors) }
            return
        }
        runOperation {
            medicationRepository.save(checkNotNull(validation.value))
            val reminderStatus = reminderCoordinator.refreshUpcoming()
            mutableState.update {
                it.copy(
                    destination = CaregiverDestination.MEDICATION_LIST,
                    selectedMedicationId = null,
                    formErrors = emptySet(),
                    reminderFeedback = reminderStatus.attentionFeedback(),
                )
            }
        }
    }

    fun deactivateMedication(medicationId: String) {
        checkUnlocked()
        runOperation {
            medicationRepository.deactivate(medicationId)
            val reminderStatus = reminderCoordinator.refreshUpcoming()
            mutableState.update {
                it.copy(reminderFeedback = reminderStatus.attentionFeedback())
            }
        }
    }

    fun updateCaregiverPhoneDraft(value: String) {
        checkUnlocked()
        if (value.length > MAX_PHONE_INPUT_LENGTH) return
        mutableState.update {
            it.copy(
                caregiverPhoneDraft = value,
                caregiverPhoneDirty = true,
                caregiverPhoneInvalid = false,
                caregiverPhoneSaved = false,
            )
        }
    }

    fun saveCaregiverPhone() {
        checkUnlocked()
        when (
            val validation = CaregiverPhoneNumberValidator.validate(
                mutableState.value.caregiverPhoneDraft
            )
        ) {
            CaregiverPhoneNumberValidation.Invalid -> mutableState.update {
                it.copy(
                    caregiverPhoneInvalid = true,
                    caregiverPhoneSaved = false,
                )
            }

            is CaregiverPhoneNumberValidation.Valid -> runOperation {
                contactRepository.savePhoneNumber(validation.normalizedNumber)
                mutableState.update {
                    it.copy(
                        caregiverPhoneDraft = validation.normalizedNumber.orEmpty(),
                        caregiverPhoneDirty = false,
                        caregiverPhoneInvalid = false,
                        caregiverPhoneSaved = true,
                    )
                }
            }
        }
    }

    fun refreshReminderStatus() {
        viewModelScope.launch {
            mutableState.update {
                it.copy(deviceReliabilityStatus = deviceReliabilityChecker.snapshot())
            }
            reminderCoordinator.refreshUpcoming()
        }
    }

    fun scheduleOneMinuteTest() {
        checkUnlocked()
        runReminderOperation {
            val feedback = when (reminderCoordinator.scheduleOneMinuteTest()) {
                is ReminderTestResult.Scheduled -> ReminderFeedback.TEST_SCHEDULED
                is ReminderTestResult.ExactAlarmUnavailable ->
                    ReminderFeedback.EXACT_ALARM_ACCESS_REQUIRED

                is ReminderTestResult.NotificationUnavailable ->
                    ReminderFeedback.NOTIFICATION_ACCESS_REQUIRED

                ReminderTestResult.Cancelled -> ReminderFeedback.TEST_CANCELLED
                ReminderTestResult.PlatformFailure,
                ReminderTestResult.TriggerTimeNotFuture,
                -> ReminderFeedback.OPERATION_FAILED
            }
            mutableState.update { it.copy(reminderFeedback = feedback) }
        }
    }

    fun cancelOneMinuteTest() {
        checkUnlocked()
        runReminderOperation {
            val feedback = when (reminderCoordinator.cancelOneMinuteTest()) {
                ReminderTestResult.Cancelled -> ReminderFeedback.TEST_CANCELLED
                else -> ReminderFeedback.OPERATION_FAILED
            }
            mutableState.update { it.copy(reminderFeedback = feedback) }
        }
    }

    fun returnToMedicationList() {
        checkUnlocked()
        mutableState.update {
            it.copy(
                destination = CaregiverDestination.MEDICATION_LIST,
                selectedMedicationId = null,
                formErrors = emptySet(),
            )
        }
    }

    fun clearFormErrors() {
        if (mutableState.value.formErrors.isNotEmpty()) {
            mutableState.update { it.copy(formErrors = emptySet()) }
        }
    }

    fun dismissOperationMessage() {
        mutableState.update { it.copy(operationMessage = null) }
    }

    fun dismissReminderFeedback() {
        mutableState.update { it.copy(reminderFeedback = null) }
    }

    private fun runOperation(block: suspend () -> Unit) {
        if (mutableState.value.isWorking) return
        viewModelScope.launch {
            mutableState.update { it.copy(isWorking = true, operationMessage = null) }
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                showOperationFailure()
            } finally {
                mutableState.update { it.copy(isWorking = false) }
            }
        }
    }

    private fun runReminderOperation(block: suspend () -> Unit) {
        if (mutableState.value.isReminderWorking) return
        viewModelScope.launch {
            mutableState.update {
                it.copy(isReminderWorking = true, reminderFeedback = null)
            }
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(reminderFeedback = ReminderFeedback.OPERATION_FAILED)
                }
            } finally {
                mutableState.update { it.copy(isReminderWorking = false) }
            }
        }
    }

    private fun checkUnlocked() {
        check(mutableState.value.unlocked) { "Caregiver settings are locked." }
    }

    private fun showOperationFailure() {
        mutableState.update { it.copy(operationMessage = OPERATION_ERROR_MESSAGE) }
    }

    class Factory(
        private val medicationRepository: MedicationRepository,
        private val pinRepository: CaregiverPinRepository,
        private val contactRepository: CaregiverContactRepository,
        private val reminderCoordinator: ReminderCoordinator,
        private val deviceReliabilityChecker: DeviceReliabilityChecker,
        private val syncOutboxRepository: SyncOutboxRepository,
        private val remoteSyncDataSource: RemoteSyncDataSource,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CaregiverViewModel::class.java))
            return CaregiverViewModel(
                medicationRepository = medicationRepository,
                pinRepository = pinRepository,
                contactRepository = contactRepository,
                reminderCoordinator = reminderCoordinator,
                deviceReliabilityChecker = deviceReliabilityChecker,
                syncOutboxRepository = syncOutboxRepository,
                remoteSyncDataSource = remoteSyncDataSource,
            ) as T
        }
    }

    companion object {
        private val PIN_PATTERN = Regex("^[0-9]{4,6}$")
        private const val MAX_PHONE_INPUT_LENGTH = 32
        private const val OPERATION_ERROR_MESSAGE =
            "İşlem tamamlanamadı. Lütfen tekrar deneyin."
    }
}

private fun ReminderRuntimeStatus.attentionFeedback(): ReminderFeedback? {
    if (plannedOccurrenceCount == 0) return null
    return if (
        exactAlarmCapability != ExactAlarmCapability.AVAILABLE ||
        notificationCapability != NotificationCapability.AVAILABLE ||
        fullScreenIntentCapability != FullScreenIntentCapability.AVAILABLE ||
        failedOperationCount > 0 ||
        scheduledAlarmCount < plannedOccurrenceCount
    ) {
        ReminderFeedback.REMINDER_SETUP_NEEDS_ATTENTION
    } else {
        null
    }
}
