package com.berkant.yaninda.ui.admin

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.berkant.yaninda.YanindaApplication
import com.berkant.yaninda.push.FamilyPushNotificationManager
import com.berkant.yaninda.ui.components.YanindaBottomTabs
import com.berkant.yaninda.ui.components.YanindaCard
import com.berkant.yaninda.ui.components.YanindaIconType
import com.berkant.yaninda.ui.components.YanindaListRow
import com.berkant.yaninda.ui.components.YanindaOutlinedButton
import com.berkant.yaninda.ui.components.YanindaSectionTitle
import com.berkant.yaninda.ui.components.YanindaTabItem
import com.berkant.yaninda.ui.family.FamilyAccessViewModel
import kotlinx.coroutines.flow.flowOf

enum class AdminTab {
    HOME,
    MEDICATIONS,
    HISTORY,
    SETTINGS,
}

private enum class AdminSettingsPage {
    LIST,
    FAMILY_CONTACTS,
    FAMILY_DEVICES,
    NOTIFICATIONS,
}

@Composable
fun AdminHomeRoute() {
    YanindaAdminTheme {
        AdminHomeContent()
    }
}

@Composable
private fun AdminHomeContent() {
    val context = LocalContext.current
    val application = context.applicationContext as YanindaApplication

    val familyFactory = remember(application) {
        FamilyAccessViewModel.Factory(
            authRepository = application.familyAuthRepository,
            familyRepository = application.familyRepository,
            pushRegistrationRepository = application.familyPushRegistrationRepository,
            deviceIdentityRepository = application.deviceIdentityRepository,
        )
    }
    val familyViewModel: FamilyAccessViewModel = viewModel(factory = familyFactory)
    val familyState by familyViewModel.state.collectAsStateWithLifecycle()
    val familyId = familyState.memberships.firstOrNull()?.familyId

    val scheduleFlow = remember(application, familyId) {
        familyId
            ?.let(application.adminScheduleRepository::observeCurrentSchedule)
            ?: flowOf(null)
    }
    val schedule by scheduleFlow.collectAsStateWithLifecycle(initialValue = null)

    var selectedTab by rememberSaveable {
        mutableStateOf(AdminTab.HOME)
    }
    var settingsPage by rememberSaveable {
        mutableStateOf(AdminSettingsPage.LIST)
    }

    BackHandler(
        enabled = selectedTab != AdminTab.HOME || settingsPage != AdminSettingsPage.LIST,
    ) {
        if (selectedTab == AdminTab.SETTINGS && settingsPage != AdminSettingsPage.LIST) {
            settingsPage = AdminSettingsPage.LIST
        } else {
            selectedTab = AdminTab.HOME
            settingsPage = AdminSettingsPage.LIST
        }
    }

    val tabs = listOf(
        YanindaTabItem(
            label = "Ana Sayfa",
            icon = YanindaIconType.HOME,
        ),
        YanindaTabItem(
            label = "İlaçlar",
            icon = YanindaIconType.MEDICATION,
        ),
        YanindaTabItem(
            label = "Geçmiş",
            icon = YanindaIconType.HISTORY,
        ),
        YanindaTabItem(
            label = "Ayarlar",
            icon = YanindaIconType.SETTINGS,
        ),
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            YanindaBottomTabs(
                items = tabs,
                selectedIndex = selectedTab.ordinal,
                onSelected = { index ->
                    val newTab = AdminTab.entries[index]
                    if (newTab != AdminTab.SETTINGS) {
                        settingsPage = AdminSettingsPage.LIST
                    }
                    selectedTab = newTab
                },
                modifier = Modifier.navigationBarsPadding(),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (selectedTab) {
                AdminTab.HOME -> AdminDashboardScreen(
                    occurrences = familyState.occurrences,
                    devices = familyState.devices,
                    schedule = schedule,
                )

                AdminTab.MEDICATIONS -> AdminMedicationsRoute(
                    familyId = familyId,
                )

                AdminTab.HISTORY -> AdminHistoryScreen(
                    occurrences = familyState.occurrences,
                )

                AdminTab.SETTINGS -> when (settingsPage) {
                    AdminSettingsPage.LIST -> AdminSettingsScreen(
                        onFamilyContacts = {
                            settingsPage = AdminSettingsPage.FAMILY_CONTACTS
                        },
                        onDevices = {
                            settingsPage = AdminSettingsPage.FAMILY_DEVICES
                        },
                        onNotifications = {
                            settingsPage = AdminSettingsPage.NOTIFICATIONS
                        },
                    )

                    AdminSettingsPage.FAMILY_CONTACTS -> AdminFamilyContactsRoute(
                        familyId = familyId,
                        onBack = {
                            settingsPage = AdminSettingsPage.LIST
                        },
                    )

                    AdminSettingsPage.FAMILY_DEVICES -> AdminFamilyDevicesScreen(
                        devices = familyState.devices,
                        pendingApprovals = familyState.pendingDeviceApprovals,
                        approvingDeviceUid = familyState.approvingDeviceUid,
                        removingDeviceId = familyState.removingDeviceId,
                        currentDeviceId = familyState.currentDeviceId,
                        approvalMessage = familyState.deviceApprovalMessage,
                        onApprove = familyViewModel::approveDevice,
                        onRemove = familyViewModel::removeDevice,
                        onBack = {
                            settingsPage = AdminSettingsPage.LIST
                        },
                    )

                    AdminSettingsPage.NOTIFICATIONS -> AdminNotificationSettingsScreen(
                        onBack = {
                            settingsPage = AdminSettingsPage.LIST
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminSettingsScreen(
    onFamilyContacts: () -> Unit,
    onDevices: () -> Unit,
    onNotifications: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Ayarlar",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = "Aile kişileri, telefonlar ve bildirimler.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            YanindaListRow(
                title = "Aile Kişileri",
                supportingText = "AİLEYİ ARA düğmesinde kullanılacak kişiler",
                icon = YanindaIconType.PHONE,
                onClick = onFamilyContacts,
            )
        }

        item {
            YanindaListRow(
                title = "Cihazlar",
                supportingText = "Dede, Anneanne ve yönetici telefonları",
                icon = YanindaIconType.DEVICE,
                onClick = onDevices,
            )
        }

        item {
            YanindaListRow(
                title = "Bildirimler",
                supportingText = "İzin, ses ve Android bildirim ayarları",
                icon = YanindaIconType.ALARM,
                onClick = onNotifications,
            )
        }
    }
}

@Composable
private fun AdminNotificationSettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var notificationsGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsGranted = granted
    }
    val appNotificationsEnabled = context.getSystemService(
        android.app.NotificationManager::class.java,
    )?.areNotificationsEnabled() == true

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            YanindaOutlinedButton(
                text = "GERİ",
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                icon = YanindaIconType.BACK,
            )
        }

        item {
            YanindaSectionTitle(
                title = "Bildirimler",
                subtitle = "Yönetici telefonundaki aile durumu bildirimleri",
                icon = YanindaIconType.ALARM,
            )
        }

        item {
            YanindaListRow(
                title = "Bildirim izni",
                supportingText = if (notificationsGranted) {
                    "İzin verildi"
                } else {
                    "İzin gerekli"
                },
                trailingText = if (notificationsGranted) "Açık" else "Kontrol et",
                icon = YanindaIconType.ALARM,
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(
                            Manifest.permission.POST_NOTIFICATIONS
                        )
                    }
                },
            )
        }

        item {
            YanindaListRow(
                title = "Uygulama bildirimleri",
                supportingText = if (appNotificationsEnabled) {
                    "Android bildirimleri açık"
                } else {
                    "Android bildirimleri kapalı"
                },
                trailingText = if (appNotificationsEnabled) "Açık" else "Kapalı",
                icon = YanindaIconType.SETTINGS,
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                    )
                },
            )
        }

        item {
            YanindaCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    YanindaSectionTitle(
                        title = "Bildirim sesi",
                        icon = YanindaIconType.ALARM,
                    )
                    Text(
                        text = "Aile durumu bildirimleri Android'in bildirim kanalını kullanır.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    YanindaOutlinedButton(
                        text = "SES AYARLARINI AÇ",
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    putExtra(
                                        Settings.EXTRA_CHANNEL_ID,
                                        FamilyPushNotificationManager.CHANNEL_ID,
                                    )
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        icon = YanindaIconType.SETTINGS,
                    )
                }
            }
        }
    }
}
