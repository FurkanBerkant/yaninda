package com.berkant.yaninda.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.berkant.yaninda.domain.family.FamilyContact
import com.berkant.yaninda.family.FamilyRepository
import com.berkant.yaninda.family.FamilyRepositoryFailure
import com.berkant.yaninda.family.FamilyRepositoryResult
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AdminFamilyContactMessage {
    SAVED,
    DELETED,
    DEFAULT_CHANGED,
    INVALID_INPUT,
    NO_FAMILY,
    NOT_AUTHENTICATED,
    PERMISSION_DENIED,
    NETWORK_UNAVAILABLE,
    NOT_CONFIGURED,
    UNKNOWN_FAILURE,
}

data class AdminFamilyContactsUiState(
    val contacts: List<FamilyContact> = emptyList(),
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val message: AdminFamilyContactMessage? = null,
)

class AdminFamilyContactsViewModel(
    private val familyId: String?,
    private val familyRepository: FamilyRepository,
) : ViewModel() {

    private val mutableState =
        MutableStateFlow(
            AdminFamilyContactsUiState(
                isLoading = familyId != null,
                message =
                    if (familyId == null) {
                        AdminFamilyContactMessage.NO_FAMILY
                    } else {
                        null
                    },
            )
        )

    val state: StateFlow<AdminFamilyContactsUiState> =
        mutableState.asStateFlow()

    init {
        observeContacts()
    }

    private fun observeContacts() {
        val currentFamilyId = familyId ?: return

        viewModelScope.launch {
            familyRepository
                .observeContacts(currentFamilyId)
                .retryWhen { cause, attempt ->

                    if (!cause.isRetryableContactFailure()) {
                        return@retryWhen false
                    }

                    delay(
                        contactRetryDelay(attempt)
                    )

                    true
                }
                .catch { error ->
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            message = error.toContactMessage(),
                        )
                    }
                }
                .collect { contacts ->
                    mutableState.update {
                        it.copy(
                            contacts = contacts,
                            isLoading = false,
                        )
                    }
                }
        }
    }

    fun addContact(
        displayName: String,
        phoneNumber: String,
    ) {
        val currentFamilyId =
            familyId ?: run {
                mutableState.update {
                    it.copy(
                        message =
                            AdminFamilyContactMessage.NO_FAMILY
                    )
                }
                return
            }

        val normalizedName =
            normalizeDisplayName(displayName)

        val normalizedPhone =
            normalizePhoneNumber(phoneNumber)

        if (
            normalizedName == null ||
            normalizedPhone == null
        ) {
            mutableState.update {
                it.copy(
                    message =
                        AdminFamilyContactMessage.INVALID_INPUT
                )
            }
            return
        }

        val contact =
            FamilyContact(
                contactId = UUID.randomUUID().toString(),
                familyId = currentFamilyId,
                displayName = normalizedName,
                phoneNumber = normalizedPhone,

                /*
                 * İlk eklenen kişi otomatik olarak
                 * AİLEYİ ARA hedefi olur.
                 */
                isDefault =
                    mutableState.value.contacts
                        .none { it.isDefault },

                /*
                 * Firestore repository bu alanı
                 * serverTimestamp ile değiştirecek.
                 */
                updatedAt = Instant.now(),
            )

        runContactOperation(
            successMessage =
                AdminFamilyContactMessage.SAVED,
        ) {
            familyRepository.saveContact(
                familyId = currentFamilyId,
                contact = contact,
            )
        }
    }

    fun makeDefault(
        contact: FamilyContact,
    ) {
        val currentFamilyId =
            familyId ?: return

        if (contact.isDefault) {
            return
        }

        runContactOperation(
            successMessage =
                AdminFamilyContactMessage.DEFAULT_CHANGED,
        ) {
            familyRepository.saveContact(
                familyId = currentFamilyId,
                contact = contact.copy(
                    isDefault = true,
                    updatedAt = Instant.now(),
                ),
            )
        }
    }

    fun deleteContact(
        contact: FamilyContact,
    ) {
        val currentFamilyId =
            familyId ?: return

        runContactOperation(
            successMessage =
                AdminFamilyContactMessage.DELETED,
        ) {
            familyRepository.deleteContact(
                familyId = currentFamilyId,
                contactId = contact.contactId,
            )
        }
    }

    fun clearMessage() {
        mutableState.update {
            it.copy(message = null)
        }
    }

    private fun runContactOperation(
        successMessage: AdminFamilyContactMessage,
        operation:
        suspend () -> FamilyRepositoryResult<Unit>,
    ) {
        if (mutableState.value.isWorking) {
            return
        }

        mutableState.update {
            it.copy(
                isWorking = true,
                message = null,
            )
        }

        viewModelScope.launch {
            try {
                when (val result = operation()) {

                    is FamilyRepositoryResult.Success -> {
                        mutableState.update {
                            it.copy(
                                isWorking = false,
                                message = successMessage,
                            )
                        }
                    }

                    is FamilyRepositoryResult.Failure -> {
                        mutableState.update {
                            it.copy(
                                isWorking = false,
                                message =
                                    result.reason
                                        .toContactMessage(),
                            )
                        }
                    }
                }

            } catch (
                error: CancellationException
            ) {
                throw error

            } catch (
                error: Exception
            ) {
                mutableState.update {
                    it.copy(
                        isWorking = false,
                        message =
                            error.toContactMessage(),
                    )
                }
            }
        }
    }

    class Factory(
        private val familyId: String?,
        private val familyRepository:
        FamilyRepository,
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: Class<T>,
        ): T {

            require(
                modelClass.isAssignableFrom(
                    AdminFamilyContactsViewModel::class.java
                )
            )

            return AdminFamilyContactsViewModel(
                familyId = familyId,
                familyRepository = familyRepository,
            ) as T
        }
    }
}

private fun normalizeDisplayName(
    value: String,
): String? =
    value
        .trim()
        .replace(
            Regex("\\s+"),
            " ",
        )
        .takeIf {
            it.isNotEmpty() &&
                    it.length <= 80
        }

private fun normalizePhoneNumber(
    value: String,
): String? {

    val trimmed =
        value.trim()

    if (trimmed.isEmpty()) {
        return null
    }

    /*
     * Kullanıcı:
     *
     * 0532 123 45 67
     * 0532-123-45-67
     * +90 532 123 45 67
     *
     * gibi yazabilsin.
     *
     * Ama harf veya beklenmeyen karakter
     * girilmişse sessizce temizlemiyoruz.
     */
    val allowed =
        trimmed.all { character ->
            character.isDigit() ||
                    character == '+' ||
                    character == ' ' ||
                    character == '-' ||
                    character == '(' ||
                    character == ')'
        }

    if (!allowed) {
        return null
    }

    /*
     * + yalnızca en başta olabilir.
     */
    if (
        '+' in trimmed &&
        !trimmed.startsWith("+")
    ) {
        return null
    }

    if (
        trimmed.drop(1)
            .contains("+")
    ) {
        return null
    }

    val digits =
        trimmed.filter(Char::isDigit)

    if (digits.length !in 7..15) {
        return null
    }

    return if (
        trimmed.startsWith("+")
    ) {
        "+$digits"
    } else {
        digits
    }
}

private fun FamilyRepositoryFailure
        .toContactMessage():
        AdminFamilyContactMessage =
    when (this) {

        FamilyRepositoryFailure.NOT_AUTHENTICATED ->
            AdminFamilyContactMessage.NOT_AUTHENTICATED

        FamilyRepositoryFailure.PERMISSION_DENIED ->
            AdminFamilyContactMessage.PERMISSION_DENIED

        FamilyRepositoryFailure.NETWORK_UNAVAILABLE ->
            AdminFamilyContactMessage.NETWORK_UNAVAILABLE

        FamilyRepositoryFailure.INVALID_INPUT ->
            AdminFamilyContactMessage.INVALID_INPUT

        FamilyRepositoryFailure.NOT_CONFIGURED ->
            AdminFamilyContactMessage.NOT_CONFIGURED

        FamilyRepositoryFailure.INVITATION_INVALID,
        FamilyRepositoryFailure.INVITATION_EXPIRED,
        FamilyRepositoryFailure.INVITATION_ALREADY_USED,
        FamilyRepositoryFailure.ROLE_MISMATCH,
        FamilyRepositoryFailure.UNKNOWN,
            ->
            AdminFamilyContactMessage.UNKNOWN_FAILURE
    }

private fun Throwable.toContactMessage():
        AdminFamilyContactMessage {

    val firestoreError =
        findCause<FirebaseFirestoreException>()

    if (firestoreError != null) {
        return when (firestoreError.code) {

            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                AdminFamilyContactMessage.PERMISSION_DENIED

            FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                AdminFamilyContactMessage.NOT_AUTHENTICATED

            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
            FirebaseFirestoreException.Code.ABORTED,
            FirebaseFirestoreException.Code.INTERNAL,
            FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED,
                ->
                AdminFamilyContactMessage.NETWORK_UNAVAILABLE

            else ->
                AdminFamilyContactMessage.UNKNOWN_FAILURE
        }
    }

    if (
        findCause<FirebaseNetworkException>() != null
    ) {
        return AdminFamilyContactMessage.NETWORK_UNAVAILABLE
    }

    return AdminFamilyContactMessage.UNKNOWN_FAILURE
}

private fun Throwable.isRetryableContactFailure():
        Boolean {

    if (
        findCause<FirebaseNetworkException>() != null
    ) {
        return true
    }

    val firestoreError =
        findCause<FirebaseFirestoreException>()
            ?: return false

    return when (firestoreError.code) {

        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
        FirebaseFirestoreException.Code.ABORTED,
        FirebaseFirestoreException.Code.INTERNAL,
        FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED,
            ->
            true

        else ->
            false
    }
}

private inline fun <reified T : Throwable>
        Throwable.findCause(): T? {

    var current: Throwable? =
        this

    while (current != null) {

        if (current is T) {
            return current
        }

        current =
            current.cause
    }

    return null
}

private fun contactRetryDelay(
    attempt: Long,
): Long =
    when (attempt) {
        0L -> 2_000L
        1L -> 5_000L
        2L -> 10_000L
        else -> 15_000L
    }