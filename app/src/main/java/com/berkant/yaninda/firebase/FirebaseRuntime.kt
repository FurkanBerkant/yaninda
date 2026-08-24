package com.berkant.yaninda.firebase

import android.content.Context
import com.berkant.yaninda.BuildConfig
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

data class FirebaseRuntime(
    val app: FirebaseApp,
    val auth: FirebaseAuth,
    val firestore: FirebaseFirestore,
    val functions: FirebaseFunctions,
    val usesLocalEmulators: Boolean,
)

object FirebaseRuntimeFactory {

    fun create(context: Context): FirebaseRuntime? {
        val app =
            FirebaseApp.getApps(context).firstOrNull()
                ?: FirebaseApp.initializeApp(context)
                ?: return null

        val auth =
            FirebaseAuth.getInstance(app)

        val firestore =
            FirebaseFirestore.getInstance(app)

        val functions =
            FirebaseFunctions.getInstance(
                app,
                FUNCTIONS_REGION,
            )

        if (BuildConfig.USE_FIREBASE_EMULATORS) {
            auth.useEmulator(
                EMULATOR_HOST,
                AUTH_EMULATOR_PORT,
            )

            firestore.useEmulator(
                EMULATOR_HOST,
                FIRESTORE_EMULATOR_PORT,
            )

            functions.useEmulator(
                EMULATOR_HOST,
                FUNCTIONS_EMULATOR_PORT,
            )
        }

        return FirebaseRuntime(
            app = app,
            auth = auth,
            firestore = firestore,
            functions = functions,
            usesLocalEmulators =
                BuildConfig.USE_FIREBASE_EMULATORS,
        )
    }

    private const val EMULATOR_HOST =
        "10.0.2.2"

    private const val AUTH_EMULATOR_PORT =
        9099

    private const val FIRESTORE_EMULATOR_PORT =
        8080

    private const val FUNCTIONS_EMULATOR_PORT =
        5001

    private const val FUNCTIONS_REGION =
        "europe-west1"
}

internal suspend fun <T> Task<T>.awaitFirebaseValue(): T =
    suspendCancellableCoroutine { continuation ->

        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                continuation.resume(task.result)
            } else {
                continuation.resumeWithException(
                    task.exception
                        ?: IllegalStateException(
                            "Firebase operation failed."
                        )
                )
            }
        }
    }

internal suspend fun Task<*>.awaitFirebaseCompletion(): Unit =
    suspendCancellableCoroutine { continuation ->

        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                continuation.resume(Unit)
            } else {
                continuation.resumeWithException(
                    task.exception
                        ?: IllegalStateException(
                            "Firebase operation failed."
                        )
                )
            }
        }
    }