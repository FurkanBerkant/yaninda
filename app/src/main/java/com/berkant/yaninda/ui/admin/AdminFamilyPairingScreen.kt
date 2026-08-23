package com.berkant.yaninda.ui.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.berkant.yaninda.domain.family.DeviceRole
import com.berkant.yaninda.domain.family.PairingCodeNormalizer
import com.berkant.yaninda.domain.family.PairingInvitation
import com.berkant.yaninda.ui.components.YanindaCard
import com.berkant.yaninda.ui.components.YanindaIconType
import com.berkant.yaninda.ui.components.YanindaOutlinedButton
import com.berkant.yaninda.ui.components.YanindaPrimaryButton
import com.berkant.yaninda.ui.components.YanindaSectionTitle
import com.berkant.yaninda.ui.family.FamilyAccessMessage
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AdminFamilyPairingScreen(
    invitation: PairingInvitation?,
    message: FamilyAccessMessage?,
    isWorking: Boolean,
    onBack: () -> Unit,
    onCreateAlarmDeviceInvitation: () -> Unit,
    onCreateAdminDeviceInvitation: () -> Unit,
    onDismissInvitation: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    BackHandler {
        onBack()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                horizontal = 20.dp,
                vertical = 20.dp,
            ),
        verticalArrangement =
            Arrangement.spacedBy(16.dp),
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
            Text(
                text = "Yeni cihaz eşleştir",
                style =
                    MaterialTheme.typography
                        .headlineLarge,
            )

            Text(
                text =
                    "Bağlamak istediğin telefonun türünü seç. Oluşturulan kod tek kullanımlıktır.",
                style =
                    MaterialTheme.typography.bodyLarge,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
                modifier =
                    Modifier.padding(top = 6.dp),
            )
        }

        item {
            YanindaCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(12.dp),
                ) {
                    YanindaSectionTitle(
                        title = "Alarm telefonu",
                        icon = YanindaIconType.ALARM,
                    )

                    Text(
                        text =
                            "Dede veya anneanne telefonu için kullanılır. İlaç programını indirir ve kendi alarmını bağımsız olarak çalıştırır.",
                        style =
                            MaterialTheme.typography
                                .bodyLarge,
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                    )

                    YanindaPrimaryButton(
                        text =
                            "ALARM TELEFONU KODU OLUŞTUR",
                        onClick =
                            onCreateAlarmDeviceInvitation,
                        modifier =
                            Modifier.fillMaxWidth(),
                        enabled = !isWorking,
                        icon = YanindaIconType.ALARM,
                    )
                }
            }
        }

        item {
            YanindaCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(12.dp),
                ) {
                    YanindaSectionTitle(
                        title = "Aile telefonu",
                        icon = YanindaIconType.FAMILY,
                    )

                    Text(
                        text =
                            "Başka bir güvenilir aile telefonunu yönetici cihazı olarak bağlamak için kullanılır.",
                        style =
                            MaterialTheme.typography
                                .bodyLarge,
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                    )

                    YanindaOutlinedButton(
                        text =
                            "AİLE TELEFONU KODU OLUŞTUR",
                        onClick =
                            onCreateAdminDeviceInvitation,
                        modifier =
                            Modifier.fillMaxWidth(),
                        enabled = !isWorking,
                        icon = YanindaIconType.FAMILY,
                    )
                }
            }
        }

        if (isWorking) {
            item {
                Text(
                    text =
                        "Eşleştirme kodu hazırlanıyor...",
                    style =
                        MaterialTheme.typography
                            .bodyMedium,
                    color =
                        MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    invitation?.let {
        AdminPairingInvitationDialog(
            invitation = it,
            onDismiss = onDismissInvitation,
        )
    }

    message?.let {
        AlertDialog(
            onDismissRequest = onDismissMessage,
            title = {
                Text(
                    text =
                        it.pairingMessageTitle()
                )
            },
            text = {
                Text(
                    text =
                        it.pairingMessageBody()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onDismissMessage,
                ) {
                    Text("TAMAM")
                }
            },
        )
    }
}

@Composable
private fun AdminPairingInvitationDialog(
    invitation: PairingInvitation,
    onDismiss: () -> Unit,
) {
    val context =
        LocalContext.current

    val displayCode =
        PairingCodeNormalizer.display(
            invitation.code
        )

    val expiration =
        PAIRING_TIME_FORMAT.format(
            invitation.expiresAt.atZone(
                ZoneId.systemDefault()
            )
        )

    val deviceLabel =
        when (invitation.targetRole) {
            DeviceRole.ALARM_DEVICE ->
                "alarm telefonu"

            DeviceRole.ADMIN_DEVICE ->
                "aile telefonu"
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Eşleştirme kodu hazır")
        },
        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text =
                        "Bu kodu $deviceLabel üzerinde Yanında uygulamasına gir.",
                )

                Surface(
                    shape =
                        RoundedCornerShape(16.dp),
                    color =
                        MaterialTheme.colorScheme
                            .primaryContainer,
                    modifier =
                        Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = displayCode,
                        style =
                            MaterialTheme.typography
                                .headlineMedium,
                        modifier =
                            Modifier.padding(18.dp),
                    )
                }

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            copyPairingCode(
                                context = context,
                                code = displayCode,
                            )
                        },
                        modifier =
                            Modifier.weight(1f),
                    ) {
                        Text("KOPYALA")
                    }

                    OutlinedButton(
                        onClick = {
                            sharePairingCode(
                                context = context,
                                code = displayCode,
                                expiration = expiration,
                                deviceLabel =
                                    deviceLabel,
                            )
                        },
                        modifier =
                            Modifier.weight(1f),
                    ) {
                        Text("PAYLAŞ")
                    }
                }

                Text(
                    text =
                        "Kod tek kullanımlıktır ve $expiration saatine kadar geçerlidir.",
                    style =
                        MaterialTheme.typography
                            .bodyMedium,
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text("TAMAM")
            }
        },
    )
}

private fun copyPairingCode(
    context: Context,
    code: String,
) {
    val clipboard =
        context.getSystemService(
            Context.CLIPBOARD_SERVICE
        ) as ClipboardManager

    clipboard.setPrimaryClip(
        ClipData.newPlainText(
            "Yanında eşleştirme kodu",
            code,
        )
    )
}

private fun sharePairingCode(
    context: Context,
    code: String,
    expiration: String,
    deviceLabel: String,
) {
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"

            putExtra(
                Intent.EXTRA_TEXT,
                """
                Yanında $deviceLabel eşleştirme kodu:

                $code

                Kod tek kullanımlıktır ve $expiration saatine kadar geçerlidir.
                """.trimIndent(),
            )
        }

    context.startActivity(
        Intent.createChooser(
            intent,
            "Eşleştirme kodunu paylaş",
        )
    )
}

private fun FamilyAccessMessage
        .pairingMessageTitle(): String =
    when (this) {
        FamilyAccessMessage.PERMISSION_DENIED ->
            "Yetki yok"

        FamilyAccessMessage.NETWORK_UNAVAILABLE ->
            "Bağlantı sorunu"

        FamilyAccessMessage.FIREBASE_NOT_CONFIGURED ->
            "Bağlantı hazır değil"

        FamilyAccessMessage.INVITATION_INVALID,
        FamilyAccessMessage.INVITATION_EXPIRED,
        FamilyAccessMessage.INVITATION_ALREADY_USED,
        FamilyAccessMessage.WRONG_DEVICE_ROLE,
            ->
            "Eşleştirme sorunu"

        else ->
            "İşlem tamamlanamadı"
    }

private fun FamilyAccessMessage
        .pairingMessageBody(): String =
    when (this) {
        FamilyAccessMessage.PERMISSION_DENIED ->
            "Bu ailede yeni cihaz eşleştirme yetkin bulunmuyor."

        FamilyAccessMessage.NETWORK_UNAVAILABLE ->
            "İnternet bağlantısı kurulamadı. Tekrar deneyebilirsin."

        FamilyAccessMessage.FIREBASE_NOT_CONFIGURED ->
            "Firebase bağlantısı bu sürümde hazır değil."

        FamilyAccessMessage.INVITATION_INVALID ->
            "Eşleştirme kodu geçerli değil."

        FamilyAccessMessage.INVITATION_EXPIRED ->
            "Eşleştirme kodunun süresi dolmuş."

        FamilyAccessMessage.INVITATION_ALREADY_USED ->
            "Bu eşleştirme kodu daha önce kullanılmış."

        FamilyAccessMessage.WRONG_DEVICE_ROLE ->
            "Kod farklı bir cihaz türü için oluşturulmuş."

        FamilyAccessMessage.INVALID_INPUT ->
            "Girilen bilgileri kontrol et."

        FamilyAccessMessage.WEAK_PASSWORD,
        FamilyAccessMessage.INVALID_CREDENTIALS,
        FamilyAccessMessage.ACCOUNT_UNAVAILABLE,
        FamilyAccessMessage.PASSWORD_RESET_SENT,
        FamilyAccessMessage.UNKNOWN_FAILURE,
            ->
            "Beklenmeyen bir hata oluştu. Tekrar deneyebilirsin."
    }

private val PAIRING_TIME_FORMAT =
    DateTimeFormatter.ofPattern("HH:mm")