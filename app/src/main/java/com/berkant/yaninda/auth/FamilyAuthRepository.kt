package com.berkant.yaninda.auth

import android.util.Log
import com.berkant.yaninda.firebase.awaitFirebaseValue
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf

sealed interface FamilyAuthState {
    data object Unavailable : FamilyAuthState

    data object SignedOut : FamilyAuthState

    data class SignedIn(
        val isAnonymous: Boolean,
    ) : FamilyAuthState
}

enum class FamilyAuthFailure {
    NETWORK_UNAVAILABLE,
    NOT_CONFIGURED,
    UNKNOWN,
}

sealed interface FamilyAuthOperationResult {
    data object Success : FamilyAuthOperationResult

    data class Failure(val reason: FamilyAuthFailure) : FamilyAuthOperationResult
}

interface FamilyAuthRepository {
    val state: Flow<FamilyAuthState>

    suspend fun ensureDeviceSession(): FamilyAuthOperationResult
}

object UnavailableFamilyAuthRepository : FamilyAuthRepository {
    override val state: Flow<FamilyAuthState> = flowOf(FamilyAuthState.Unavailable)

    override suspend fun ensureDeviceSession(): FamilyAuthOperationResult = notConfigured()

    private fun notConfigured() = FamilyAuthOperationResult.Failure(
        FamilyAuthFailure.NOT_CONFIGURED
    )
}

class FirebaseFamilyAuthRepository(
    private val auth: FirebaseAuth,
    private val usesLocalEmulators: Boolean = false,
) : FamilyAuthRepository {
    override val state: Flow<FamilyAuthState> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { currentAuth ->
            val user = currentAuth.currentUser
            trySend(
                if (user == null) {
                    FamilyAuthState.SignedOut
                } else {
                    FamilyAuthState.SignedIn(
                        isAnonymous = user.isAnonymous,
                    )
                }
            )
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun ensureDeviceSession(): FamilyAuthOperationResult {
        val currentUser = auth.currentUser
            ?: return signInAnonymously()

        return try {
            currentUser.getIdToken(true).awaitFirebaseValue()
            FamilyAuthOperationResult.Success

        } catch (_: FirebaseNetworkException) {
            FamilyAuthOperationResult.Failure(
                FamilyAuthFailure.NETWORK_UNAVAILABLE
            )

        } catch (error: Exception) {
            if (
                currentUser.isAnonymous &&
                shouldReplaceAnonymousSession(error)
            ) {
                auth.signOut()
                signInAnonymously()
            } else {
                Log.e(
                    LOG_TAG,
                    "Device session validation failed. error=${error::class.java.simpleName}",
                )
                FamilyAuthOperationResult.Failure(
                    FamilyAuthFailure.UNKNOWN
                )
            }
        }
    }

    private suspend fun signInAnonymously(): FamilyAuthOperationResult =
        runAuthOperation {
            auth.signInAnonymously().awaitFirebaseValue()
        }

    private fun shouldReplaceAnonymousSession(error: Exception): Boolean {
        if (error is FirebaseAuthInvalidUserException) return true

        if (error is FirebaseAuthException) {
            return error.errorCode in INVALID_SESSION_ERROR_CODES
        }

        // Auth Emulator import/export does not preserve Android refresh tokens.
        // Unknown non-network token failures are recoverable in local development.
        return usesLocalEmulators
    }

    private suspend fun runAuthOperation(
        block: suspend () -> Unit,
    ): FamilyAuthOperationResult =
        try {
            block()
            FamilyAuthOperationResult.Success

        } catch (_: FirebaseNetworkException) {
            Log.e(
                LOG_TAG,
                "Auth failed: NETWORK",
            )

            FamilyAuthOperationResult.Failure(
                FamilyAuthFailure.NETWORK_UNAVAILABLE
            )

        } catch (error: Exception) {
            Log.e(
                LOG_TAG,
                "Auth operation failed. error=${error::class.java.simpleName}",
            )

            FamilyAuthOperationResult.Failure(
                FamilyAuthFailure.UNKNOWN
            )
        }

    private companion object {
        const val LOG_TAG = "FamilyAuthRepository"
        val INVALID_SESSION_ERROR_CODES = setOf(
            "ERROR_INVALID_USER_TOKEN",
            "ERROR_USER_NOT_FOUND",
            "ERROR_USER_TOKEN_EXPIRED",
        )
    }
}
