package com.berkant.yaninda.push

import com.berkant.yaninda.data.device.DeviceIdentityRepository
import com.berkant.yaninda.domain.family.DeviceRole
import com.berkant.yaninda.firebase.awaitFirebaseCompletion
import com.berkant.yaninda.firebase.awaitFirebaseValue
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

interface FamilyPushRegistrationRepository {
    suspend fun requestRegistration()

    suspend fun registerInstallationId(installationId: String)

    suspend fun unregisterCurrentInstallation()
}

object UnavailableFamilyPushRegistrationRepository : FamilyPushRegistrationRepository {
    override suspend fun requestRegistration() = Unit

    override suspend fun registerInstallationId(installationId: String) = Unit

    override suspend fun unregisterCurrentInstallation() = Unit
}

class FirestoreFamilyPushRegistrationRepository(
    firebaseApp: FirebaseApp,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val deviceIdentityRepository: DeviceIdentityRepository,
    private val appVersion: String,
    private val messaging: FirebaseMessaging =
        FirebaseMessaging.getInstance(),
    private val installations: FirebaseInstallations =
        FirebaseInstallations.getInstance(
            firebaseApp
        ),
) : FamilyPushRegistrationRepository {

    override suspend fun requestRegistration() {
        val user =
            auth.currentUser ?: return

        val pairing =
            deviceIdentityRepository
                .pairing
                .first()
                ?: return

        if (
            pairing.deviceRole !=
            DeviceRole.ADMIN_DEVICE &&
            pairing.deviceRole !=
            DeviceRole.ALARM_DEVICE
        ) {
            return
        }

        /*
         * ALARM_DEVICE anonymous Firebase user
         * olabilir.
         *
         * Bu yüzden artık:
         *
         * user.isAnonymous
         *
         * kontrolü yapmıyoruz.
         *
         * Yetkiyi aşağıda Firestore'daki gerçek
         * device kaydı üzerinden doğruluyoruz.
         */
        if (user.uid.isBlank()) return

        messaging
            .register()
            .awaitFirebaseCompletion()
    }

    override suspend fun registerInstallationId(
        installationId: String,
    ) {
        if (
            !INSTALLATION_ID_PATTERN
                .matches(installationId)
        ) {
            return
        }

        val user =
            auth.currentUser ?: return

        val pairing =
            deviceIdentityRepository
                .pairing
                .first()
                ?: return

        if (
            pairing.deviceRole !=
            DeviceRole.ADMIN_DEVICE &&
            pairing.deviceRole !=
            DeviceRole.ALARM_DEVICE
        ) {
            return
        }

        val deviceId =
            deviceIdentityRepository
                .getOrCreateDeviceId()

        val registrationId =
            installationId.sha256()

        firestore.runTransaction {
                transaction ->

            val family =
                firestore
                    .collection(FAMILIES)
                    .document(
                        pairing.familyId
                    )

            val deviceReference =
                family
                    .collection(DEVICES)
                    .document(deviceId)

            val registrationReference =
                family
                    .collection(
                        PUSH_REGISTRATIONS
                    )
                    .document(
                        registrationId
                    )

            val device =
                transaction.get(
                    deviceReference
                )

            val existing =
                transaction.get(
                    registrationReference
                )

            /*
             * Client'taki pairing bilgisinin
             * kendisine güvenmiyoruz.
             *
             * Firestore'daki device:
             *
             * - gerçekten bu uid'ye ait olmalı
             * - gerçekten aynı role sahip olmalı
             */
            check(
                device.exists() &&
                        device.getString(
                            OWNER_UID
                        ) == user.uid &&
                        device.getString(
                            ROLE
                        ) ==
                        pairing.deviceRole.name
            ) {
                "The device push registration is unavailable."
            }

            transaction.set(
                registrationReference,
                mapOf(
                    REGISTRATION_ID to
                            registrationId,

                    FAMILY_ID to
                            pairing.familyId,

                    INSTALLATION_ID to
                            installationId,

                    DEVICE_ID to
                            deviceId,

                    OWNER_UID to
                            user.uid,

                    /*
                     * Cloud Function bununla
                     * ADMIN ve ALARM push'larını
                     * birbirinden ayıracak.
                     */
                    ROLE to
                            pairing.deviceRole.name,

                    PLATFORM to
                            ANDROID,

                    APP_VERSION to
                            appVersion,

                    CREATED_AT to
                            (
                                    existing
                                        .getTimestamp(
                                            CREATED_AT
                                        )
                                        ?: FieldValue
                                            .serverTimestamp()
                                    ),

                    UPDATED_AT to
                            FieldValue
                                .serverTimestamp(),

                    VERSION to
                            (
                                    existing
                                        .getLong(
                                            VERSION
                                        )
                                        ?: 0L
                                    ) + 1L,
                ),
            )

            Unit
        }.awaitFirebaseValue<Unit>()
    }

    override suspend fun unregisterCurrentInstallation() {
        val installationId =
            try {
                installations
                    .id
                    .awaitFirebaseValue()

            } catch (
                error: CancellationException
            ) {
                throw error

            } catch (_: Exception) {
                null
            }

        val user =
            auth.currentUser

        val pairing =
            deviceIdentityRepository
                .pairing
                .first()

        if (
            installationId != null &&
            user != null &&
            pairing != null
        ) {
            try {
                firestore
                    .collection(FAMILIES)
                    .document(
                        pairing.familyId
                    )
                    .collection(
                        PUSH_REGISTRATIONS
                    )
                    .document(
                        installationId
                            .sha256()
                    )
                    .delete()
                    .awaitFirebaseCompletion()

            } catch (
                error: CancellationException
            ) {
                throw error

            } catch (_: Exception) {
                /*
                 * FCM unregister yine çalışsın.
                 */
            }
        }

        try {
            messaging
                .unregister()
                .awaitFirebaseCompletion()

        } catch (
            error: CancellationException
        ) {
            throw error

        } catch (_: Exception) {
            /*
             * Sign-out / cihaz temizliği
             * network yüzünden bloke olmasın.
             */
        }
    }

    private fun String.sha256():
            String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(
                toByteArray(
                    Charsets.UTF_8
                )
            )
            .joinToString(
                separator = ""
            ) { byte ->
                (byte.toInt() and 0xff)
                    .toString(16)
                    .padStart(2, '0')
            }

    private companion object {
        val INSTALLATION_ID_PATTERN =
            Regex(
                "^[A-Za-z0-9_-]{10,256}$"
            )

        const val FAMILIES =
            "families"

        const val DEVICES =
            "devices"

        const val PUSH_REGISTRATIONS =
            "pushRegistrations"

        const val REGISTRATION_ID =
            "registrationId"

        const val FAMILY_ID =
            "familyId"

        const val INSTALLATION_ID =
            "installationId"

        const val DEVICE_ID =
            "deviceId"

        const val OWNER_UID =
            "ownerUid"

        const val ROLE =
            "role"

        const val PLATFORM =
            "platform"

        const val ANDROID =
            "ANDROID"

        const val APP_VERSION =
            "appVersion"

        const val CREATED_AT =
            "createdAt"

        const val UPDATED_AT =
            "updatedAt"

        const val VERSION =
            "version"
    }
}
