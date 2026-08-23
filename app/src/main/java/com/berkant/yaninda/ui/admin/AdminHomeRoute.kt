package com.berkant.yaninda.ui.admin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.berkant.yaninda.YanindaApplication
import com.berkant.yaninda.ui.components.YanindaBottomTabs
import com.berkant.yaninda.ui.components.YanindaIconType
import com.berkant.yaninda.ui.components.YanindaTabItem
import com.berkant.yaninda.ui.family.FamilyAccessRoute
import com.berkant.yaninda.ui.family.FamilyAccessViewModel
import com.berkant.yaninda.schedule.PublishedScheduleVersion
import kotlinx.coroutines.flow.flowOf
import com.berkant.yaninda.ui.components.YanindaCard
import com.berkant.yaninda.ui.components.YanindaIconBadge
import com.berkant.yaninda.ui.components.YanindaSectionTitle
import com.berkant.yaninda.ui.components.YanindaIcon
import com.berkant.yaninda.ui.components.YanindaOutlinedButton

enum class AdminTab {
    HOME,
    MEDICATIONS,
    LOCATION,
    SETTINGS,
}

private enum class AdminSettingsPage {
    LIST,
    FAMILY,
    NOTIFICATIONS,
}

@Composable
private fun AdminLocationScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = "Konum",
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = "Dedenin son konum bilgisi",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            YanindaCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    YanindaSectionTitle(
                        title = "Konum hizmeti hazır değil",
                        icon = YanindaIconType.DEVICE,
                    )
                    Text(
                        text = "Konum ve güvenlik özelliği henüz bu sürümde etkin değil.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun AdminHomeRoute(
    onSignOut: () -> Unit,
) {
    YanindaAdminTheme {
        AdminHomeContent(
            onSignOut = onSignOut,
        )
    }
}

@Composable
private fun AdminHomeContent(
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    val application = context.applicationContext as YanindaApplication

    val familyFactory = remember(application) {
        FamilyAccessViewModel.Factory(
            authRepository = application.familyAuthRepository,
            familyRepository = application.familyRepository,
            pairingService = application.devicePairingService,
            pushRegistrationRepository = application.familyPushRegistrationRepository,
            secondaryReminderCoordinator = application.secondaryReminderCoordinator,
        )
    }

    val familyViewModel: FamilyAccessViewModel =
        viewModel(factory = familyFactory)

    val familyState by familyViewModel.state.collectAsStateWithLifecycle()

    val familyId = familyState.memberships.firstOrNull()?.familyId
    val scheduleFlow = remember(application, familyId) {
        familyId?.let(application.adminScheduleRepository::observeCurrentSchedule)
            ?: flowOf(null)
    }
    val schedule by scheduleFlow.collectAsStateWithLifecycle(initialValue = null)

    var selectedTab by rememberSaveable {
        mutableStateOf(AdminTab.HOME)
    }
    var settingsPage by rememberSaveable {
        mutableStateOf(AdminSettingsPage.LIST)
    }
    var unavailableSetting by rememberSaveable { mutableStateOf<String?>(null) }

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
            label = "Konum",
            icon = YanindaIconType.LOCATION,
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
                    selectedTab = AdminTab.entries[index]
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

                AdminTab.HOME -> {
                    AdminDashboardScreen(
                        occurrences = familyState.occurrences,
                        devices = familyState.devices,
                        schedule = schedule,
                    )
                }

                AdminTab.MEDICATIONS -> {
                    AdminMedicationsRoute(
                        familyId = familyId,
                    )
                }

                AdminTab.LOCATION -> {
                    AdminLocationScreen()
                }

                AdminTab.SETTINGS -> {
                    when (settingsPage) {
                        AdminSettingsPage.LIST -> AdminSettingsScreen(
                            onMedicationSettings = { selectedTab = AdminTab.MEDICATIONS },
                            onLocationSettings = { selectedTab = AdminTab.LOCATION },
                            onNotificationSettings = {
                                settingsPage = AdminSettingsPage.NOTIFICATIONS
                            },
                            onFamilyConnection = {
                                settingsPage = AdminSettingsPage.FAMILY
                            },
                            onUnavailableSetting = { unavailableSetting = it },
                        )

                        AdminSettingsPage.FAMILY -> FamilyAccessRoute(
                            onBack = { settingsPage = AdminSettingsPage.LIST },
                        )
                        AdminSettingsPage.NOTIFICATIONS -> AdminNotificationSettingsScreen(
                            onBack = { settingsPage = AdminSettingsPage.LIST },
                        )
                    }
                }
            }
        }
    }

    unavailableSetting?.let { setting ->
        AlertDialog(
            onDismissRequest = { unavailableSetting = null },
            title = { Text(setting) },
            text = { Text("Bu bölüm henüz etkin değil.") },
            confirmButton = {
                TextButton(onClick = { unavailableSetting = null }) {
                    Text("TAMAM")
                }
            },
        )
    }
}

@Composable
private fun AdminSettingsScreen(
    onMedicationSettings: () -> Unit,
    onLocationSettings: () -> Unit,
    onNotificationSettings: () -> Unit,
    onFamilyConnection: () -> Unit,
    onUnavailableSetting: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = "Ayarlar",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        item {
            AdminSettingsRow(
                icon = YanindaIconType.PERSON,
                title = "Kullanıcı profili",
                subtitle = "Dede",
                onClick = { onUnavailableSetting("Kullanıcı profili") },
            )
        }
        item {
            AdminSettingsRow(
                icon = YanindaIconType.MEDICATION,
                title = "İlaç ayarları",
                onClick = onMedicationSettings,
            )
        }
        item {
            AdminSettingsRow(
                icon = YanindaIconType.ALARM,
                title = "Bildirim ve ses",
                subtitle = "Yönetici bildirimleri",
                onClick = onNotificationSettings,
            )
        }
        item {
            AdminSettingsRow(
                icon = YanindaIconType.DEVICE,
                title = "Konum ve güvenlik",
                subtitle = "Konum özelliği hazır değil",
                onClick = onLocationSettings,
            )
        }
        item {
            AdminSettingsRow(
                icon = YanindaIconType.FAMILY,
                title = "Aile bağlantısı",
                subtitle = "Cihazlar ve senkronizasyon",
                onClick = onFamilyConnection,
            )
        }
        item {
            AdminSettingsRow(
                icon = YanindaIconType.SETTINGS,
                title = "Uygulama kontrolü",
                subtitle = "Bağlantı ve uygulama durumu",
                onClick = { onUnavailableSetting("Uygulama kontrolü") },
            )
        }
        item {
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
                ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsGranted = granted
    }
    val appNotificationsEnabled =
        context.getSystemService(android.app.NotificationManager::class.java)
            ?.areNotificationsEnabled() == true

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
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
            Text("Bildirim ve ses", style = MaterialTheme.typography.headlineLarge)
        }
        item {
            AdminSettingsRow(
                icon = YanindaIconType.ALARM,
                title = "Bildirim izni",
                subtitle = if (notificationsGranted) "İzin verildi" else "İzin gerekli",
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
        }
        item {
            AdminSettingsRow(
                icon = YanindaIconType.SETTINGS,
                title = "Uygulama bildirimleri",
                subtitle = if (appNotificationsEnabled) "Bildirimler açık" else "Bildirimler kapalı",
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        },
                    )
                },
            )
        }
        item {
            YanindaCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    YanindaSectionTitle(
                        title = "Ses",
                        icon = YanindaIconType.ALARM,
                    )
                    Text(
                        text = "Aile durum bildirimleri Android'in bildirim sesi kanalını kullanır.",
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
                                        com.berkant.yaninda.push.FamilyPushNotificationManager.CHANNEL_ID,
                                    )
                                },
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

@Composable
private fun AdminSettingsRow(
    icon: YanindaIconType,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
) {
    YanindaCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            YanindaIconBadge(icon = icon, size = 46.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (onClick != null) {
                YanindaIcon(
                    type = YanindaIconType.CHEVRON,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}