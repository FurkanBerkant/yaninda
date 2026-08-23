package com.berkant.yaninda.domain.contact

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaregiverPhoneNumberValidatorTest {
    @Test
    fun formattedTurkishNumber_isNormalizedWithoutInferringCountryCode() {
        val result = CaregiverPhoneNumberValidator.validate("0 (555) 123 45 67")

        assertEquals(
            CaregiverPhoneNumberValidation.Valid("05551234567"),
            result,
        )
    }

    @Test
    fun internationalPrefix_isPreserved() {
        val result = CaregiverPhoneNumberValidator.validate("+90 555 123 45 67")

        assertEquals(
            CaregiverPhoneNumberValidation.Valid("+905551234567"),
            result,
        )
    }

    @Test
    fun blankInput_clearsTheOptionalCallTarget() {
        assertEquals(
            CaregiverPhoneNumberValidation.Valid(null),
            CaregiverPhoneNumberValidator.validate("   "),
        )
    }

    @Test
    fun lettersOrTooFewDigits_areRejected() {
        assertTrue(
            CaregiverPhoneNumberValidator.validate("0555-ABC") is
                CaregiverPhoneNumberValidation.Invalid
        )
        assertTrue(
            CaregiverPhoneNumberValidator.validate("12345") is
                CaregiverPhoneNumberValidation.Invalid
        )
    }
}
