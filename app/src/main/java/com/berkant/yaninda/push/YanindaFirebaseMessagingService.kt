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
    override fun onMessageReceived(
        message: RemoteMessage,
    ) {
        val application =
            applicationContext as?
                    YanindaApplication
                ?: return

        /*
         * Önce ALARM_DEVICE schedule hint'ini
         * kontrol ediyoruz.
         *
         * FCM bize schedule'ın kendisini vermez.
         * Sadece:
         *
         * "Yeni bir schedule olabilir,
         * Firestore'u kontrol et."
         *
         * der.
         */
        val scheduleChangedPayload =
            ScheduleChangedPushPayloadParser
                .parse(message.data)

        if (scheduleChangedPayload != null) {

            application.applicationScope.launch {
                try {
                    val selectedRole =
                        application
                            .deviceIdentityRepository
                            .selectedRole
                            .first()

                    val pairing =
                        application
                            .deviceIdentityRepository
                            .pairing
                            .first()

                    /*
                     * Başka aileye ait veya yanlış
                     * role gönderilmiş bir push,
                     * schedule sync başlatamaz.
                     */
                    if (
                        selectedRole ==
                        DeviceRole.ALARM_DEVICE &&
                        pairing?.deviceRole ==
                        DeviceRole.ALARM_DEVICE &&
                        pairing.familyId ==
                        scheduleChangedPayload.familyId
                    ) {

                        /*
                         * Burada scheduleVersion'a
                         * güvenip doğrudan uygulamıyoruz.
                         *
                         * Worker gerçek authoritative
                         * desiredVersion'ı Firestore'dan
                         * tekrar okuyacak.
                         */
                        application
                            .alarmScheduleSyncWorkScheduler
                            .requestImmediateSync()
                    }

                } catch (
                    error: CancellationException
                ) {
                    throw error

                } catch (_: Exception) {
                    /*
                     * FCM yalnızca hızlandırıcı.
                     *
                     * Bu başarısız olsa bile
                     * periodic WorkManager daha sonra
                     * schedule'ı kontrol edecek.
                     */
                }
            }

            return
        }

        /*
         * Mevcut ADMIN_DEVICE aile bildirimleri:
         *
         * ACKNOWLEDGED_TAKEN
         * NO_CONFIRMATION
         *
         * aynen çalışmaya devam ediyor.
         */
        val familyPayload =
            FamilyPushPayloadParser
                .parse(message.data)
                ?: return

        application.applicationScope.launch {
            try {
                val selectedRole =
                    application
                        .deviceIdentityRepository
                        .selectedRole
                        .first()

                if (
                    selectedRole ==
                    DeviceRole.ADMIN_DEVICE
                ) {
                    application
                        .familyPushNotificationManager
                        .show(familyPayload)
                }

            } catch (
                error: CancellationException
            ) {
                throw error

            } catch (_: Exception) {
                /*
                 * Firestore dashboard mevcut
                 * authoritative projection olmaya
                 * devam ediyor.
                 */
            }
        }
    }

}
