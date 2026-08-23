package com.berkant.yaninda.firebase

import android.content.Context
import com.berkant.yaninda.BuildConfig
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

data class FirebaseRuntime(
    val app: FirebaseApp,
    val auth: FirebaseAuth,
    val firestore: FirebaseFirestore,
    val usesLocalEmulators: Boolean,
)

object FirebaseRuntimeFactory {
    fun create(context: Context): FirebaseRuntime? {
        val app = FirebaseApp.getApps(context).firstOrNull()
            ?: FirebaseApp.initializeApp(context)
            ?: if (BuildConfig.DEBUG) initializeDemoApp(context) else null
            ?: return null
        val auth = FirebaseAuth.getInstance(app)
        val firestore = FirebaseFirestore.getInstance(app)
        if (BuildConfig.USE_FIREBASE_EMULATORS) {
            auth.useEmulator(EMULATOR_HOST, AUTH_EMULATOR_PORT)
            firestore.useEmulator(EMULATOR_HOST, FIRESTORE_EMULATOR_PORT)
        }
        return FirebaseRuntime(
            app = app,
            auth = auth,
            firestore = firestore,
            usesLocalEmulators = BuildConfig.USE_FIREBASE_EMULATORS,
        )
    }

    private fun initializeDemoApp(context: Context): FirebaseApp = FirebaseApp.initializeApp(
        context,
        FirebaseOptions.Builder()
            .setApplicationId(DEMO_APPLICATION_ID)
            .setApiKey(DEMO_API_KEY)
            .setProjectId(DEMO_PROJECT_ID)
            .build(),
    )

    private const val DEMO_PROJECT_ID = "demo-yaninda"
    private const val DEMO_APPLICATION_ID = "1:1234567890:android:demo-yaninda"
    private const val DEMO_API_KEY = "demo-api-key"
    private const val EMULATOR_HOST = "10.0.2.2"
    private const val AUTH_EMULATOR_PORT = 9099
    private const val FIRESTORE_EMULATOR_PORT = 8080
}

internal suspend fun <T> Task<T>.awaitFirebaseValue(): T = suspendCancellableCoroutine {
    continuation ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) {
            continuation.resume(task.result)
        } else {
            continuation.resumeWithException(
                task.exception ?: IllegalStateException("Firebase operation failed.")
            )
        }
    }
}

internal suspend fun Task<*>.awaitFirebaseCompletion(): Unit = suspendCancellableCoroutine {
    continuation ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) {
            continuation.resume(Unit)
        } else {
            continuation.resumeWithException(
                task.exception ?: IllegalStateException("Firebase operation failed.")
            )
        }
    }
}
