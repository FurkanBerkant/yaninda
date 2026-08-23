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
import com.berkant.yaninda.ui.admin.AdminHomeRoute
import com.berkant.yaninda.ui.grandfather.GrandfatherHomeRoute
import com.berkant.yaninda.ui.grandfather.GrandfatherPrototypeApp
import com.berkant.yaninda.ui.grandfather.PROTOTYPE_SCREEN_EXTRA
import com.berkant.yaninda.ui.grandfather.PrototypeScreen
import com.berkant.yaninda.ui.setup.AlarmDeviceSetupRoute
import com.berkant.yaninda.ui.setup.DeviceRoleSetupRoute
import com.berkant.yaninda.ui.theme.YanindaTheme
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val contentRequest = mutableStateOf(MainContentRequest())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val yanindaApplication = application as YanindaApplication
        var contactSyncJob: Job? = null

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
            ) { role, pairing ->
                role to pairing
            }
                .distinctUntilChanged()
                .collectLatest { (role, pairing) ->
                    if (
                        role == DeviceRole.ALARM_DEVICE &&
                        pairing?.deviceRole == DeviceRole.ALARM_DEVICE
                    ) {
                        /*
                         * First restore the LAST KNOWN-GOOD local alarms.
                         *
                         * This happens before any network operation.
                         */
                        yanindaApplication
                            .reminderCoordinator
                            .refreshUpcoming()

                        /*
                         * Then try to fetch a newer desired schedule.
                         *
                         * If internet is unavailable this coroutine retries,
                         * while the existing local alarms keep working.
                         */
                        yanindaApplication
                            .alarmScheduleSyncCoordinator
                            ?.run(
                                familyId = pairing.familyId,
                                onScheduleAccessReady = {
                                    if (contactSyncJob == null) {
                                        contactSyncJob = launch {
                                            yanindaApplication.familyRepository
                                                .observeContacts(pairing.familyId)
                                                .collect { contacts ->
                                                    val defaultContact = contacts.firstOrNull {
                                                        it.isDefault
                                                    } ?: contacts.firstOrNull()
                                                    yanindaApplication.caregiverContactRepository
                                                        .savePhoneNumber(defaultContact?.phoneNumber)
                                                }
                                        }
                                    }
                                },
                            )
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

                when (
                    resolveMainRoute(
                        roleLoaded = roleSnapshot.loaded,
                        role = roleSnapshot.role,
                        pairingLoaded = pairingSnapshot.loaded,
                        pairing = pairingSnapshot.pairing,
                    )
                ) {
                    MainRoute.LOADING -> MainLoadingScreen()

                    MainRoute.ROLE_SETUP -> DeviceRoleSetupRoute()

                    MainRoute.ADMIN_HOME -> {
                        AdminHomeRoute(
                            onSignOut = {
                                // Admin sign-out will be wired at the admin layer later.
                            }
                        )
                    }

                    MainRoute.ALARM_DEVICE_SETUP -> {
                        AlarmDeviceSetupRoute()
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