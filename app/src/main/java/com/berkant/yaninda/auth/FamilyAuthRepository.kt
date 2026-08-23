package com.berkant.yaninda.auth

import com.berkant.yaninda.firebase.awaitFirebaseCompletion
import com.berkant.yaninda.firebase.awaitFirebaseValue
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import android.util.Log
sealed interface FamilyAuthState {
    data object Unavailable : FamilyAuthState

    data object SignedOut : FamilyAuthState

    data class SignedIn(
        val userId: String,
        val email: String?,
        val isAnonymous: Boolean,
        val emailVerified: Boolean,
    ) : FamilyAuthState
}

enum class FamilyAuthFailure {
    INVALID_CREDENTIALS,
    WEAK_PASSWORD,
    NETWORK_UNAVAILABLE,
    ACCOUNT_UNAVAILABLE,
    NOT_CONFIGURED,
    UNKNOWN,
}

sealed interface FamilyAuthOperationResult {
    data object Success : FamilyAuthOperationResult

    data class Failure(val reason: FamilyAuthFailure) : FamilyAuthOperationResult
}

interface FamilyAuthRepository {
    val state: Flow<FamilyAuthState>

    suspend fun createCaregiverAccount(
        email: String,
        password: String,
    ): FamilyAuthOperationResult

    suspend fun signInCaregiver(
        email: String,
        password: String,
    ): FamilyAuthOperationResult

    suspend fun ensureAlarmDeviceSession(): FamilyAuthOperationResult

    suspend fun sendPasswordReset(email: String): FamilyAuthOperationResult

    fun signOut()
}

object UnavailableFamilyAuthRepository : FamilyAuthRepository {
    override val state: Flow<FamilyAuthState> = flowOf(FamilyAuthState.Unavailable)

    override suspend fun createCaregiverAccount(
        email: String,
        password: String,
    ): FamilyAuthOperationResult = notConfigured()

    override suspend fun signInCaregiver(
        email: String,
        password: String,
    ): FamilyAuthOperationResult = notConfigured()

    override suspend fun ensureAlarmDeviceSession(): FamilyAuthOperationResult = notConfigured()

    override suspend fun sendPasswordReset(email: String): FamilyAuthOperationResult =
        notConfigured()

    override fun signOut() = Unit

    private fun notConfigured() = FamilyAuthOperationResult.Failure(
        FamilyAuthFailure.NOT_CONFIGURED
    )
}

class FirebaseFamilyAuthRepository(
    private val auth: FirebaseAuth,
) : FamilyAuthRepository {
    override val state: Flow<FamilyAuthState> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { currentAuth ->
            val user = currentAuth.currentUser
            trySend(
                if (user == null) {
                    FamilyAuthState.SignedOut
                } else {
                    FamilyAuthState.SignedIn(
                        userId = user.uid,
                        email = user.email,
                        isAnonymous = user.isAnonymous,
                        emailVerified = user.isEmailVerified,
                    )
                }
            )
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun createCaregiverAccount(
        email: String,
        password: String,
    ): FamilyAuthOperationResult {
        val normalizedEmail = normalizeEmail(email) ?: return invalidCredentials()
        if (!isAcceptablePassword(password)) {
            return FamilyAuthOperationResult.Failure(FamilyAuthFailure.WEAK_PASSWORD)
        }
        return runAuthOperation {
            auth.createUserWithEmailAndPassword(normalizedEmail, password).awaitFirebaseValue()
        }
    }

    override suspend fun signInCaregiver(
        email: String,
        password: String,
    ): FamilyAuthOperationResult {
        val normalizedEmail = normalizeEmail(email) ?: return invalidCredentials()
        if (password.isEmpty() || password.length > MAX_PASSWORD_LENGTH) {
            return invalidCredentials()
        }
        return runAuthOperation {
            auth.signInWithEmailAndPassword(normalizedEmail, password).awaitFirebaseValue()
        }
    }

    override suspend fun ensureAlarmDeviceSession(): FamilyAuthOperationResult {
        if (auth.currentUser != null) return FamilyAuthOperationResult.Success
        return runAuthOperation { auth.signInAnonymously().awaitFirebaseValue() }
    }

    override suspend fun sendPasswordReset(email: String): FamilyAuthOperationResult {
        val normalizedEmail = normalizeEmail(email) ?: return invalidCredentials()
        return runAuthOperation {
            auth.sendPasswordResetEmail(normalizedEmail).awaitFirebaseCompletion()
        }
    }

    override fun signOut() {
        auth.signOut()
    }

    private suspend fun runAuthOperation(
        block: suspend () -> Unit,
    ): FamilyAuthOperationResult =
        try {
            block()
            FamilyAuthOperationResult.Success

        } catch (error: FirebaseAuthWeakPasswordException) {
            Log.e(
                "FamilyAuthRepository",
                "Auth failed: WEAK_PASSWORD",
                error,
            )

            FamilyAuthOperationResult.Failure(
                FamilyAuthFailure.WEAK_PASSWORD
            )

        } catch (error: FirebaseAuthInvalidCredentialsException) {
            Log.e(
                "FamilyAuthRepository",
                "Auth failed: INVALID_CREDENTIALS",
                error,
            )

            invalidCredentials()

        } catch (error: FirebaseAuthInvalidUserException) {
            Log.e(
                "FamilyAuthRepository",
                "Auth failed: INVALID_USER",
                error,
            )

            invalidCredentials()

        } catch (error: FirebaseAuthUserCollisionException) {
            Log.e(
                "FamilyAuthRepository",
                "Auth failed: ACCOUNT_ALREADY_EXISTS",
                error,
            )

            FamilyAuthOperationResult.Failure(
                FamilyAuthFailure.ACCOUNT_UNAVAILABLE
            )

        } catch (error: FirebaseNetworkException) {
            Log.e(
                "FamilyAuthRepository",
                "Auth failed: NETWORK",
                error,
            )

            FamilyAuthOperationResult.Failure(
                FamilyAuthFailure.NETWORK_UNAVAILABLE
            )

        } catch (error: Exception) {
            Log.e(
                "FamilyAuthRepository",
                """
            Auth operation FAILED
            exception=${error::class.java.name}
            message=${error.message}
            cause=${error.cause?.javaClass?.name}
            causeMessage=${error.cause?.message}
            """.trimIndent(),
                error,
            )

            FamilyAuthOperationResult.Failure(
                FamilyAuthFailure.UNKNOWN
            )
        }

    private fun normalizeEmail(value: String): String? {
        val normalized = value.trim().lowercase()
        return normalized.takeIf {
            it.length in MIN_EMAIL_LENGTH..MAX_EMAIL_LENGTH && EMAIL_PATTERN.matches(it)
        }
    }

    private fun isAcceptablePassword(value: String): Boolean =
        value.length in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH

    private fun invalidCredentials() = FamilyAuthOperationResult.Failure(
        FamilyAuthFailure.INVALID_CREDENTIALS
    )

    private companion object {
        const val MIN_EMAIL_LENGTH = 3
        const val MAX_EMAIL_LENGTH = 254
        const val MIN_PASSWORD_LENGTH = 8
        const val MAX_PASSWORD_LENGTH = 128
        val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
