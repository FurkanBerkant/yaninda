package com.berkant.yaninda.domain.contact

sealed interface CaregiverPhoneNumberValidation {
    data class Valid(
        val normalizedNumber: String?,
    ) : CaregiverPhoneNumberValidation

    data object Invalid : CaregiverPhoneNumberValidation
}

object CaregiverPhoneNumberValidator {
    fun validate(input: String): CaregiverPhoneNumberValidation {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return CaregiverPhoneNumberValidation.Valid(null)
        if (!DISPLAY_PATTERN.matches(trimmed)) return CaregiverPhoneNumberValidation.Invalid

        val hasInternationalPrefix = trimmed.startsWith('+')
        if (trimmed.drop(1).contains('+')) return CaregiverPhoneNumberValidation.Invalid
        val digits = trimmed.filter { it in '0'..'9' }
        if (digits.length !in MIN_DIGITS..MAX_DIGITS) {
            return CaregiverPhoneNumberValidation.Invalid
        }

        return CaregiverPhoneNumberValidation.Valid(
            normalizedNumber = if (hasInternationalPrefix) "+$digits" else digits,
        )
    }

    private val DISPLAY_PATTERN = Regex("^\\+?[0-9 ()-]+$")
    private const val MIN_DIGITS = 7
    private const val MAX_DIGITS = 15
}
