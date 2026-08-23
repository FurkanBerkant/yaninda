package com.berkant.yaninda.push

import android.annotation.SuppressLint
import com.berkant.yaninda.YanindaApplication
import com.berkant.yaninda.domain.family.DeviceRole
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// This Firebase SDK uses Installation IDs: onRegistered is the rotation callback and raw
// legacy FCM tokens are intentionally neither requested nor persisted.
@SuppressLint("MissingFirebaseInstanceTokenRefresh")
class YanindaFirebaseMessagingService : FirebaseMessagingService() {
    override fun onRegistered(installationId: String) {
        val application = applicationContext as? YanindaApplication ?: return
        application.applicationScope.launch {
            try {
                application.familyPushRegistrationRepository.registerInstallationId(
                    installationId
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // A later registration callback or sign-in refresh retries the durable upload.
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val payload = FamilyPushPayloadParser.parse(message.data) ?: return
        val application = applicationContext as? YanindaApplication ?: return
        application.applicationScope.launch {
            try {
                val selectedRole = application.deviceIdentityRepository.selectedRole.first()
                if (selectedRole == DeviceRole.ADMIN_DEVICE) {
                    application.familyPushNotificationManager.show(payload)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // The Firestore family dashboard remains the authoritative remote projection.
            }
        }
    }
}
