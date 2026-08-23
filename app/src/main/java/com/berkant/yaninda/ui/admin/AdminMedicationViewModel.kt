package com.berkant.yaninda.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.berkant.yaninda.domain.medication.MedicationDraft
import com.berkant.yaninda.domain.medication.MedicationDraftError
import com.berkant.yaninda.domain.medication.MedicationDraftValidator
import com.berkant.yaninda.schedule.AdminScheduleFailure
import com.berkant.yaninda.schedule.AdminScheduleRepository
import com.berkant.yaninda.schedule.AdminScheduleResult
import com.berkant.yaninda.schedule.PublishedScheduleVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AdminMedicationMessage {
    SAVED,
    DELETED,
    NOT_AUTHENTICATED,
    PERMISSION_DENIED,
    NETWORK_UNAVAILABLE,
    INVALID_INPUT,
    UNKNOWN_FAILURE,
}

data class AdminMedicationUiState(
    val schedule: PublishedScheduleVersion? = null,
    val validationErrors: Set<MedicationDraftError> = emptySet(),
    val isLoading: Boolean = false,
    val isWorking: Boolean = false,
    val message: AdminMedicationMessage? = null,
)

class AdminMedicationViewModel(
    private val repository: AdminScheduleRepository,
    private val validator: MedicationDraftValidator =
        MedicationDraftValidator(),
) : ViewModel() {

    private val mutableState =
        MutableStateFlow(AdminMedicationUiState())

    val state: StateFlow<AdminMedicationUiState> =
        mutableState.asStateFlow()

    private var currentFamilyId: String? = null
    private var scheduleJob: Job? = null

    fun bindFamily(
        familyId: String?,
    ) {
        if (currentFamilyId == familyId) {
            return
        }

        currentFamilyId = familyId
        scheduleJob?.cancel()

        if (familyId == null) {
            mutableState.value =
                AdminMedicationUiState()
            return
        }

        mutableState.update {
            it.copy(
                isLoading = true,
                message = null,
            )
        }

        scheduleJob = viewModelScope.launch {
            try {
                repository
                    .observeCurrentSchedule(familyId)
                    .collect { schedule ->
                        mutableState.update {
                            it.copy(
                                schedule = schedule,
                                isLoading = false,
                            )
                        }
                    }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        message =
                            AdminMedicationMessage
                                .NETWORK_UNAVAILABLE,
                    )
                }
            }
        }
    }

    fun saveMedication(
        draft: MedicationDraft,
    ) {
        if (mutableState.value.isWorking) {
            return
        }

        val validation =
            validator.validate(draft)

        if (!validation.isValid) {
            mutableState.update {
                it.copy(
                    validationErrors = validation.errors,
                )
            }
            return
        }

        val familyId = currentFamilyId

        if (familyId == null) {
            mutableState.update {
                it.copy(
                    message =
                        AdminMedicationMessage
                            .PERMISSION_DENIED,
                )
            }
            return
        }

        val validatedDraft =
            requireNotNull(validation.value)

        mutableState.update {
            it.copy(
                isWorking = true,
                validationErrors = emptySet(),
                message = null,
            )
        }

        viewModelScope.launch {
            try {
                when (
                    val result =
                        repository.saveMedication(
                            familyId = familyId,
                            draft = validatedDraft,
                        )
                ) {
                    is AdminScheduleResult.Success -> {
                        mutableState.update {
                            it.copy(
                                isWorking = false,
                                message =
                                    AdminMedicationMessage.SAVED,
                            )
                        }
                    }

                    is AdminScheduleResult.Failure -> {
                        mutableState.update {
                            it.copy(
                                isWorking = false,
                                message =
                                    result.reason.toMessage(),
                            )
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(
                        isWorking = false,
                        message =
                            AdminMedicationMessage
                                .UNKNOWN_FAILURE,
                    )
                }
            }
        }
    }

    fun deleteMedication(medicationId: String) {
        if (mutableState.value.isWorking) return
        val familyId = currentFamilyId ?: run {
            mutableState.update { it.copy(message = AdminMedicationMessage.PERMISSION_DENIED) }
            return
        }
        mutableState.update { it.copy(isWorking = true, message = null) }
        viewModelScope.launch {
            try {
                val result = repository.deleteMedication(familyId, medicationId)
                mutableState.update {
                    it.copy(
                        isWorking = false,
                        message = when (result) {
                            is AdminScheduleResult.Success -> AdminMedicationMessage.DELETED
                            is AdminScheduleResult.Failure -> result.reason.toMessage()
                        },
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(isWorking = false, message = AdminMedicationMessage.UNKNOWN_FAILURE)
                }
            }
        }
    }

    fun clearValidationErrors() {
        if (mutableState.value.validationErrors.isEmpty()) {
            return
        }

        mutableState.update {
            it.copy(
                validationErrors = emptySet(),
            )
        }
    }

    fun clearMessage() {
        mutableState.update {
            it.copy(message = null)
        }
    }

    private fun AdminScheduleFailure.toMessage():
            AdminMedicationMessage =
        when (this) {
            AdminScheduleFailure.NOT_AUTHENTICATED ->
                AdminMedicationMessage.NOT_AUTHENTICATED

            AdminScheduleFailure.PERMISSION_DENIED ->
                AdminMedicationMessage.PERMISSION_DENIED

            AdminScheduleFailure.NETWORK_UNAVAILABLE ->
                AdminMedicationMessage.NETWORK_UNAVAILABLE

            AdminScheduleFailure.INVALID_INPUT ->
                AdminMedicationMessage.INVALID_INPUT

            AdminScheduleFailure.UNKNOWN ->
                AdminMedicationMessage.UNKNOWN_FAILURE
        }

    class Factory(
        private val repository: AdminScheduleRepository,
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: Class<T>,
        ): T {
            return AdminMedicationViewModel(
                repository = repository,
            ) as T
        }
    }
}