package com.berkant.yaninda.data.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {
    private val hasher = PinHasher(defaultIterations = 1_000)

    @Test
    fun verify_acceptsOnlyMatchingPin() {
        val stored = hasher.create("2468".toCharArray())

        assertTrue(hasher.verify("2468".toCharArray(), stored))
        assertFalse(hasher.verify("1357".toCharArray(), stored))
    }

    @Test
    fun create_usesDifferentSaltForSamePin() {
        val first = hasher.create("2468".toCharArray())
        val second = hasher.create("2468".toCharArray())

        assertNotEquals(first.saltBase64, second.saltBase64)
        assertNotEquals(first.hashBase64, second.hashBase64)
    }
}
