package com.berkant.yaninda.ui.setup

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.berkant.yaninda.YanindaApplication
import com.berkant.yaninda.notification.FullScreenIntentCapability
import com.berkant.yaninda.notification.NotificationCapability
import com.berkant.yaninda.ui.components.YanindaCard
import com.berkant.yaninda.ui.components.YanindaIconType
import com.berkant.yaninda.ui.components.YanindaOutlinedButton
import com.berkant.yaninda.ui.components.YanindaPrimaryButton

internal enum class AlarmDeviceReadiness {
    READY,
    NOTIFICATION_ACTION_REQUIRED,
    FULL_SCREEN_ACTION_REQUIRED,
}

internal fun resolveAlarmDeviceReadiness(
    notificationCapability: NotificationCapability,
    fullScreenIntentCapability: FullScreenIntentCapability,
): AlarmDeviceReadiness = when {
    notificationCapability != NotificationCapability.AVAILABLE ->
        AlarmDeviceReadiness.NOTIFICATION_ACTION_REQUIRED

    fullScreenIntentCapability != FullScreenIntentCapability.AVAILABLE ->
        AlarmDeviceReadiness.FULL_SCREEN_ACTION_REQUIRED

    else -> AlarmDeviceReadiness.READY
}

@Composable
fun AlarmDeviceReadinessGate(
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val application = context.applicationContext as YanindaApplication
    val lifecycleOwner = LocalLifecycleOwner.current

    var notificationCapability by remember {
        mutableStateOf(application.reminderNotifier.capability())
    }
    var fullScreenIntentCapability by remember {
        mutableStateOf(application.reminderNotifier.fullScreenIntentCapability())
    }

    fun refreshCapabilities() {
        notificationCapability = application.reminderNotifier.capability()
        fullScreenIntentCapability =
            application.reminderNotifier.fullScreenIntentCapability()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        refreshCapabilities()
    }

    DisposableEffect(lifecycleOwner, application) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshCapabilities()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val readiness = resolveAlarmDeviceReadiness(
        notificationCapability = notificationCapability,
        fullScreenIntentCapability = fullScreenIntentCapability,
    )

    LaunchedEffect(readiness) {
        if (readiness == AlarmDeviceReadiness.READY) {
            application.reminderCoordinator.refreshUpcoming()
        }
    }

    if (readiness == AlarmDeviceReadiness.READY) {
        content()
        return
    }

    AlarmDeviceReadinessScreen(
        notificationCapability = notificationCapability,
        fullScreenIntentCapability = fullScreenIntentCapability,
        onRequestNotificationPermission = {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(
                    Manifest.permission.POST_NOTIFICATIONS
                )
            } else {
                openAppNotificationSettings(context)
            }
        },
        onOpenNotificationSettings = {
            openAppNotificationSettings(context)
        },
        onOpenFullScreenSettings = {
            openFullScreenAlarmSettings(context)
        },
        onCheckAgain = ::refreshCapabilities,
    )
}

@Composable
private fun AlarmDeviceReadinessScreen(
    notificationCapability: NotificationCapability,
    fullScreenIntentCapability: FullScreenIntentCapability,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenFullScreenSettings: () -> Unit,
    onCheckAgain: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Alarmı hazırlayalım",
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = "Bu iki ayarı telefonu hazırlayan bir aile üyesi tamamlamalı.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                ReadinessCard(
                    title = "1. Bildirimlere izin ver",
                    description = if (
                        notificationCapability == NotificationCapability.AVAILABLE
                    ) {
                        "Hazır. İlaç bildirimi ekranda görünebilir."
                    } else {
                        "İlaç zamanı ekranına ulaşmak ve alarmı durdurmak için gereklidir."
                    },
                    ready = notificationCapability == NotificationCapability.AVAILABLE,
                    primaryButtonText = when (notificationCapability) {
                        NotificationCapability.RUNTIME_PERMISSION_REQUIRED ->
                            "BİLDİRİM İZNİ VER"

                        else -> "BİLDİRİM AYARLARINI AÇ"
                    },
                    onPrimaryAction = when (notificationCapability) {
                        NotificationCapability.RUNTIME_PERMISSION_REQUIRED ->
                            onRequestNotificationPermission

                        else -> onOpenNotificationSettings
                    },
                    secondaryButtonText = if (
                        notificationCapability == NotificationCapability.RUNTIME_PERMISSION_REQUIRED
                    ) {
                        "ANDROID AYARLARINI AÇ"
                    } else {
                        null
                    },
                    onSecondaryAction = onOpenNotificationSettings,
                )
            }

            item {
                ReadinessCard(
                    title = "2. Tam ekran alarmı aç",
                    description = if (
                        fullScreenIntentCapability == FullScreenIntentCapability.AVAILABLE
                    ) {
                        "Hazır. Kilitli ekranda alarm ekranı açılabilir."
                    } else {
                        "Telefon kilitliyken İLACIMI ALDIM ekranının açılması için gereklidir."
                    },
                    ready = fullScreenIntentCapability == FullScreenIntentCapability.AVAILABLE,
                    primaryButtonText = "TAM EKRAN AYARINI AÇ",
                    onPrimaryAction = onOpenFullScreenSettings,
                )
            }

            item {
                YanindaCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Bu ayarlar tamamlanmadan ilaç alarmına güvenmeyin.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = "İzin yoksa uygulama kontrol edilemeyen uzun bir zil sesi başlatmaz.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            item {
                YanindaOutlinedButton(
                    text = "TEKRAR KONTROL ET",
                    onClick = onCheckAgain,
                    modifier = Modifier.fillMaxWidth(),
                    icon = YanindaIconType.CHECK,
                    minHeight = 64.dp,
                )
            }
        }
    }
}

@Composable
private fun ReadinessCard(
    title: String,
    description: String,
    ready: Boolean,
    primaryButtonText: String,
    onPrimaryAction: () -> Unit,
    secondaryButtonText: String? = null,
    onSecondaryAction: () -> Unit = {},
) {
    YanindaCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (ready) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (ready) {
                Text(
                    text = "HAZIR",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                YanindaPrimaryButton(
                    text = primaryButtonText,
                    onClick = onPrimaryAction,
                    modifier = Modifier.fillMaxWidth(),
                    icon = YanindaIconType.SETTINGS,
                )
                secondaryButtonText?.let { text ->
                    YanindaOutlinedButton(
                        text = text,
                        onClick = onSecondaryAction,
                        modifier = Modifier.fillMaxWidth(),
                        icon = YanindaIconType.SETTINGS,
                    )
                }
            }
        }
    }
}

private fun openAppNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }
    startSettingsOrAppDetails(context, intent)
}

private fun openFullScreenAlarmSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        Intent(
            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
            Uri.parse("package:${context.packageName}"),
        )
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }
    startSettingsOrAppDetails(context, intent)
}

private fun startSettingsOrAppDetails(
    context: Context,
    preferredIntent: Intent,
) {
    val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    val activityContext = context as? Activity

    runCatching {
        (activityContext ?: context).startActivity(
            preferredIntent.addFlags(
                if (activityContext == null) Intent.FLAG_ACTIVITY_NEW_TASK else 0
            )
        )
    }.recoverCatching {
        (activityContext ?: context).startActivity(
            fallbackIntent.addFlags(
                if (activityContext == null) Intent.FLAG_ACTIVITY_NEW_TASK else 0
            )
        )
    }
}
