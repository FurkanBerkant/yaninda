package com.berkant.yaninda.data.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class StoredPinHash(
    val saltBase64: String,
    val hashBase64: String,
    val iterations: Int,
)

class PinHasher(
    private val secureRandom: SecureRandom = SecureRandom(),
    private val defaultIterations: Int = DEFAULT_ITERATIONS,
) {
    fun create(pin: CharArray): StoredPinHash {
        val salt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
        val hash = derive(pin, salt, defaultIterations)
        return StoredPinHash(
            saltBase64 = Base64.getEncoder().encodeToString(salt),
            hashBase64 = Base64.getEncoder().encodeToString(hash),
            iterations = defaultIterations,
        )
    }

    fun verify(pin: CharArray, stored: StoredPinHash): Boolean {
        val salt = Base64.getDecoder().decode(stored.saltBase64)
        val expected = Base64.getDecoder().decode(stored.hashBase64)
        val actual = derive(pin, salt, stored.iterations)
        return MessageDigest.isEqual(expected, actual)
    }

    private fun derive(pin: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        require(iterations > 0) { "PIN hash iteration count must be positive." }
        val keySpec = PBEKeySpec(pin, salt, iterations, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(keySpec).encoded
        } finally {
            keySpec.clearPassword()
        }
    }

    companion object {
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val SALT_BYTES = 16
        private const val KEY_LENGTH_BITS = 256
        private const val DEFAULT_ITERATIONS = 120_000
    }
}
