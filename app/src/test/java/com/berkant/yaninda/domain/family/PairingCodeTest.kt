package com.berkant.yaninda.domain.family

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingCodeTest {
    @Test
    fun generator_createsSixteenCharactersFromTheNonAmbiguousAlphabet() {
        var index = 0
        val generator = PairingCodeGenerator { bound ->
            (index++ % bound)
        }

        val code = generator.create()

        assertEquals(PairingCodeGenerator.CODE_LENGTH, code.length)
        assertTrue(code.all { it in "23456789ABCDEFGHJKLMNPQRSTUVWXYZ" })
    }

    @Test
    fun normalizer_acceptsDisplayGroupingAndLowercase() {
        assertEquals(
            "23456789ABCDEFGH",
            PairingCodeNormalizer.normalize("2345-6789-abcd-efgh"),
        )
        assertEquals(
            "2345-6789-ABCD-EFGH",
            PairingCodeNormalizer.display("23456789ABCDEFGH"),
        )
    }

    @Test
    fun normalizer_rejectsAmbiguousAndWrongLengthCodes() {
        assertNull(PairingCodeNormalizer.normalize("2345-6789-ABCD-EFG"))
        assertNull(PairingCodeNormalizer.normalize("2345-6789-ABCD-EFGI"))
    }
}
