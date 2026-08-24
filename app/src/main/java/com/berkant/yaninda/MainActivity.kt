package com.berkant.yaninda

import android.content.pm.ApplicationInfo
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.berkant.yaninda.core.phone.openPhoneDialer
import com.berkant.yaninda.domain.family.DeviceRole
import com.berkant.yaninda.domain.family.FamilyPairing
import com.berkant.yaninda.family.private.PrivateDeviceProfile
import com.berkant.yaninda.family.private.PrivateFamilyProvisioningResult
import com.berkant.yaninda.ui.admin.AdminHomeRoute
import com.berkant.yaninda.ui.grandfather.GrandfatherHomeRoute
import com.berkant.yaninda.ui.grandfather.GrandfatherPrototypeApp
import com.berkant.yaninda.ui.grandfather.PROTOTYPE_SCREEN_EXTRA
import com.berkant.yaninda.ui.grandfather.PrototypeScreen
import com.berkant.yaninda.ui.setup.AlarmDeviceReadinessGate
import com.berkant.yaninda.ui.setup.DeviceRoleSetupRoute
import com.berkant.yaninda.ui.theme.YanindaTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val contentRequest = mutableStateOf(MainContentRequest())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val yanindaApplication = application as YanindaApplication

        /*
         * Only a paired ALARM_DEVICE is allowed to restore/schedule
         * medication alarms.
         *
         * ADMIN_DEVICE must never schedule medication alarms.
         */
        lifecycleScope.launch {
            combine(
                yanindaApplication.deviceIdentityRepository.selectedRole,
                yanindaApplication.deviceIdentityRepository.pairing,
                yanindaApplication.privateDeviceProfileRepository.profile,
            ) { role, pairing, profile ->
                DeviceConfiguration(
                    role = role,
                    pairing = pairing,
                    profile = profile,
                )
            }
                .distinctUntilChanged()
                .collectLatest { configuration ->
                    val role = configuration.role
                        ?: return@collectLatest
                    val pairing = configuration.pairing
                        ?: return@collectLatest
                    val profile = configuration.profile
                        ?: return@collectLatest

                    if (
                        role != pairing.deviceRole ||
                        role != profile.role
                    ) {
                        return@collectLatest
                    }

                    if (role == DeviceRole.ALARM_DEVICE) {
                        /*
                         * Cloud oturumu bozuk veya internet kapalı olsa bile
                         * son çalışan lokal programı önce geri kur.
                         */
                        yanindaApplication
                            .reminderCoordinator
                            .refreshUpcoming()
                    }

                    var provisioningResult: PrivateFamilyProvisioningResult
                    do {
                        provisioningResult =
                            yanindaApplication
                                .privateFamilyProvisioningService
                                .provision(profile)

                        when (provisioningResult) {
                            PrivateFamilyProvisioningResult.Success -> Unit

                            PrivateFamilyProvisioningResult.AuthenticationFailed,
                            PrivateFamilyProvisioningResult.BackendUnavailable,
                            -> delay(PRIVATE_PROVISION_RETRY_MILLIS)

                            PrivateFamilyProvisioningResult.AuthorizationDenied,
                            PrivateFamilyProvisioningResult.ProvisioningFailed,
                            -> return@collectLatest

                            is PrivateFamilyProvisioningResult.ApprovalRequired ->
                                return@collectLatest
                        }
                    } while (
                        provisioningResult != PrivateFamilyProvisioningResult.Success &&
                        currentCoroutineContext().isActive
                    )

                    /*
                     * Cihaz pairing tamamladıktan sonra
                     * kendi FCM installation registration'ını
                     * başlat.
                     *
                     * Repository:
                     *
                     * - gerçek Firebase build'de registration açar
                     * - emulator/debug ortamında no-op olabilir
                     */
                    if (
                        role == pairing.deviceRole
                    ) {
                        try {
                            yanindaApplication
                                .familyPushRegistrationRepository
                                .requestRegistration()

                        } catch (
                            error: CancellationException
                        ) {
                            throw error

                        } catch (_: Exception) {
                            /*
                             * FCM kritik alarm yolu değildir.
                             *
                             * Registration başarısız olsa bile
                             * Room + AlarmManager + periodic
                             * WorkManager çalışmaya devam eder.
                             */
                        }
                    }

                    if (role == DeviceRole.ALARM_DEVICE) {
                        /*
                         * Uygulama açıkken Firestore
                         * schedule listener çalışsın.
                         */
                        coroutineScope {

                            launch {
                                yanindaApplication
                                    .alarmScheduleSyncCoordinator
                                    ?.run(
                                        familyId =
                                            pairing.familyId,
                                    )
                            }

                            launch {
                                while (
                                    currentCoroutineContext()
                                        .isActive
                                ) {
                                    try {
                                        yanindaApplication
                                            .familyRepository
                                            .observeContacts(
                                                pairing.familyId
                                            )
                                            .collect { contacts ->

                                                val defaultContact =
                                                    contacts
                                                        .firstOrNull {
                                                            it.isDefault
                                                        }
                                                        ?: contacts
                                                            .firstOrNull()

                                                /*
                                                 * Firestore bize başarılı
                                                 * şekilde boş liste döndürdüyse
                                                 * gerçekten kayıtlı aile
                                                 * kişisi kalmamış demektir.
                                                 *
                                                 * Bu durumda lokal telefonu
                                                 * temizlemek doğru.
                                                 */
                                                yanindaApplication
                                                    .caregiverContactRepository
                                                    .savePhoneNumber(
                                                        defaultContact
                                                            ?.phoneNumber
                                                    )
                                            }

                                    } catch (
                                        error: CancellationException
                                    ) {
                                        throw error

                                    } catch (_: Exception) {
                                        /*
                                         * Önemli:
                                         *
                                         * Cloud/contact sync hatasında
                                         * mevcut lokal telefon numarasını
                                         * SİLMİYORUZ.
                                         *
                                         * Böylece internet geçici olarak
                                         * kesilirse "AİLEYİ ARA" son
                                         * bilinen geçerli numarayla
                                         * çalışmaya devam edebilir.
                                         *
                                         * Schedule sync de bu hatadan
                                         * etkilenmez.
                                         */
                                    }

                                    delay(
                                        CONTACT_SYNC_RETRY_MILLIS
                                    )
                                }
                            }
                        }
                    }
                }
        }

        contentRequest.value = intent.toContentRequest()

        setContent {
            YanindaTheme {
                val request = contentRequest.value

                val roleSnapshot by produceState(
                    initialValue = DeviceRoleSnapshot(),
                    key1 = yanindaApplication,
                ) {
                    yanindaApplication.deviceIdentityRepository.selectedRole.collect { role ->
                        value = DeviceRoleSnapshot(
                            loaded = true,
                            role = role,
                        )
                    }
                }

                val pairingSnapshot by produceState(
                    initialValue = DevicePairingSnapshot(),
                    key1 = yanindaApplication,
                ) {
                    yanindaApplication.deviceIdentityRepository.pairing.collect { pairing ->
                        value = DevicePairingSnapshot(
                            loaded = true,
                            pairing = pairing,
                        )
                    }
                }

                val profileSnapshot by produceState(
                    initialValue = DeviceProfileSnapshot(),
                    key1 = yanindaApplication,
                ) {
                    yanindaApplication.privateDeviceProfileRepository.profile.collect { profile ->
                        value = DeviceProfileSnapshot(
                            loaded = true,
                            profile = profile,
                        )
                    }
                }

                when (
                    resolveMainRoute(
                        roleLoaded = roleSnapshot.loaded,
                        role = roleSnapshot.role,
                        pairingLoaded = pairingSnapshot.loaded,
                        pairing = pairingSnapshot.pairing,
                        profileLoaded = profileSnapshot.loaded,
                        profile = profileSnapshot.profile,
                    )
                ) {
                    MainRoute.LOADING -> MainLoadingScreen()

                    MainRoute.ROLE_SETUP -> DeviceRoleSetupRoute()

                    MainRoute.ADMIN_HOME -> {
                        AdminHomeRoute()
                    }

                    MainRoute.ALARM_DEVICE_HOME -> {
                        val caregiverPhoneNumber by
                        yanindaApplication.caregiverContactRepository.phoneNumber
                            .collectAsStateWithLifecycle(initialValue = null)

                        val callFamily = caregiverPhoneNumber?.let { phoneNumber ->
                            {
                                openPhoneDialer(phoneNumber)
                            }
                        }

                        AlarmDeviceReadinessGate {
                            request.prototypeScreen?.let { prototypeScreen ->
                                GrandfatherPrototypeApp(
                                    initialScreen = prototypeScreen,
                                    onCallFamily = callFamily ?: { false },
                                )
                            } ?: GrandfatherHomeRoute(
                                onCallFamily = callFamily,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        contentRequest.value = intent.toContentRequest()
    }

    private fun Intent.toContentRequest(): MainContentRequest {
        val requestedScreen = getStringExtra(PROTOTYPE_SCREEN_EXTRA)
        val isDebuggable =
            applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

        return MainContentRequest(
            prototypeScreen = if (isDebuggable) {
                PrototypeScreen.fromExtraOrNull(requestedScreen)
            } else {
                null
            },
        )
    }
    private companion object {
        const val CONTACT_SYNC_RETRY_MILLIS =
            15_000L

        const val PRIVATE_PROVISION_RETRY_MILLIS =
            15_000L
    }
}

@androidx.compose.runtime.Composable
private fun MainLoadingScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    }
}

private data class MainContentRequest(
    val prototypeScreen: PrototypeScreen? = null,
)

private data class DeviceRoleSnapshot(
    val loaded: Boolean = false,
    val role: DeviceRole? = null,
)

private data class DevicePairingSnapshot(
    val loaded: Boolean = false,
    val pairing: FamilyPairing? = null,
)

private data class DeviceProfileSnapshot(
    val loaded: Boolean = false,
    val profile: PrivateDeviceProfile? = null,
)

private data class DeviceConfiguration(
    val role: DeviceRole?,
    val pairing: FamilyPairing?,
    val profile: PrivateDeviceProfile?,
)
