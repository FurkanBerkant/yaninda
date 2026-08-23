package com.berkant.yaninda.ui.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.berkant.yaninda.auth.FamilyAuthState
import com.berkant.yaninda.domain.family.FamilyMemberRole
import com.berkant.yaninda.ui.components.YanindaCard
import com.berkant.yaninda.ui.components.YanindaIconType
import com.berkant.yaninda.ui.components.YanindaOutlinedButton
import com.berkant.yaninda.ui.components.YanindaPrimaryButton
import com.berkant.yaninda.ui.components.YanindaSectionTitle
import com.berkant.yaninda.ui.family.FamilyAccessMessage
import com.berkant.yaninda.ui.family.FamilyAccessUiState

@Composable
fun AdminFamilyAccountScreen(
    state: FamilyAccessUiState,
    onBack: () -> Unit,
    onCreateAccount: (
        email: String,
        password: String,
    ) -> Unit,
    onSignIn: (
        email: String,
        password: String,
    ) -> Unit,
    onPasswordReset: (String) -> Unit,
    onCreateFamily: (
        familyName: String,
        displayName: String,
    ) -> Unit,
    onJoinFamily: (
        invitationCode: String,
        deviceDisplayName: String,
    ) -> Unit,
    onSignOut: () -> Unit,
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
                text = "Hesap",
                style =
                    MaterialTheme.typography.headlineLarge,
            )
        }

        when (val authState = state.authState) {

            FamilyAuthState.Unavailable -> {
                item {
                    YanindaCard(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            verticalArrangement =
                                Arrangement.spacedBy(8.dp),
                        ) {
                            YanindaSectionTitle(
                                title = "Hesap kullanılamıyor",
                                icon =
                                    YanindaIconType.WARNING,
                            )

                            Text(
                                text =
                                    "Firebase bağlantısı bu uygulama sürümünde hazır değil.",
                                style =
                                    MaterialTheme.typography
                                        .bodyLarge,
                            )
                        }
                    }
                }
            }

            FamilyAuthState.SignedOut -> {
                item {
                    FamilySignInCard(
                        isWorking = state.isWorking,
                        onCreateAccount =
                            onCreateAccount,
                        onSignIn = onSignIn,
                        onPasswordReset =
                            onPasswordReset,
                    )
                }
            }

            is FamilyAuthState.SignedIn -> {

                if (authState.isAnonymous) {
                    item {
                        YanindaCard(
                            modifier =
                                Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                verticalArrangement =
                                    Arrangement.spacedBy(
                                        12.dp
                                    ),
                            ) {
                                Text(
                                    text =
                                        "Anonim cihaz oturumu",
                                    style =
                                        MaterialTheme.typography
                                            .titleLarge,
                                )

                                Text(
                                    text =
                                        "Bu oturum aile yöneticisi hesabı değildir.",
                                    style =
                                        MaterialTheme.typography
                                            .bodyLarge,
                                )

                                YanindaOutlinedButton(
                                    text =
                                        "OTURUMU KAPAT",
                                    onClick = onSignOut,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth(),
                                    enabled =
                                        !state.isWorking,
                                    icon =
                                        YanindaIconType.LOCK,
                                )
                            }
                        }
                    }

                } else if (
                    state.memberships.isEmpty()
                ) {
                    item {
                        SignedInWithoutFamilyCard(
                            email =
                                authState.email
                                    ?: "-",
                            isWorking =
                                state.isWorking,
                            onCreateFamily =
                                onCreateFamily,
                            onJoinFamily =
                                onJoinFamily,
                            onSignOut =
                                onSignOut,
                        )
                    }

                } else {
                    val membership =
                        state.memberships.first()

                    item {
                        YanindaCard(
                            modifier =
                                Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                verticalArrangement =
                                    Arrangement.spacedBy(
                                        12.dp
                                    ),
                            ) {
                                YanindaSectionTitle(
                                    title =
                                        "Hesap bilgileri",
                                    icon =
                                        YanindaIconType.PERSON,
                                )

                                AccountValue(
                                    label = "E-posta",
                                    value =
                                        authState.email
                                            ?: "-",
                                )

                                AccountValue(
                                    label = "Ad",
                                    value =
                                        membership.displayName,
                                )

                                AccountValue(
                                    label = "Aile",
                                    value =
                                        membership.familyName,
                                )

                                AccountValue(
                                    label = "Yetki",
                                    value =
                                        when (
                                            membership.role
                                        ) {
                                            FamilyMemberRole.ADMIN ->
                                                "Yönetici"

                                            FamilyMemberRole.CAREGIVER_VIEWER ->
                                                "Aile üyesi"
                                        },
                                )
                            }
                        }
                    }

                    item {
                        YanindaOutlinedButton(
                            text = "OTURUMU KAPAT",
                            onClick = onSignOut,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(
                                        min = 56.dp
                                    ),
                            enabled =
                                !state.isWorking,
                            icon =
                                YanindaIconType.LOCK,
                        )
                    }
                }
            }
        }
    }

    state.message?.let { message ->
        AlertDialog(
            onDismissRequest =
                onDismissMessage,
            title = {
                Text(
                    text =
                        message.accountTitle()
                )
            },
            text = {
                Text(
                    text =
                        message.accountBody()
                )
            },
            confirmButton = {
                TextButton(
                    onClick =
                        onDismissMessage,
                ) {
                    Text("TAMAM")
                }
            },
        )
    }
}

@Composable
private fun FamilySignInCard(
    isWorking: Boolean,
    onCreateAccount: (
        String,
        String,
    ) -> Unit,
    onSignIn: (
        String,
        String,
    ) -> Unit,
    onPasswordReset: (String) -> Unit,
) {
    var createMode by rememberSaveable {
        mutableStateOf(false)
    }

    var email by rememberSaveable {
        mutableStateOf("")
    }

    var password by rememberSaveable {
        mutableStateOf("")
    }

    YanindaCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement =
                Arrangement.spacedBy(12.dp),
        ) {
            YanindaSectionTitle(
                title =
                    if (createMode) {
                        "Yeni hesap oluştur"
                    } else {
                        "Aile hesabına giriş yap"
                    },
                icon = YanindaIconType.PERSON,
            )

            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it.take(254)
                },
                modifier =
                    Modifier.fillMaxWidth(),
                label = {
                    Text("E-posta")
                },
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType =
                            KeyboardType.Email
                    ),
                singleLine = true,
                enabled = !isWorking,
            )

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it.take(128)
                },
                modifier =
                    Modifier.fillMaxWidth(),
                label = {
                    Text("Şifre")
                },
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType =
                            KeyboardType.Password
                    ),
                visualTransformation =
                    PasswordVisualTransformation(),
                singleLine = true,
                enabled = !isWorking,
            )

            YanindaPrimaryButton(
                text =
                    if (createMode) {
                        "HESAP OLUŞTUR"
                    } else {
                        "GİRİŞ YAP"
                    },
                onClick = {
                    if (createMode) {
                        onCreateAccount(
                            email,
                            password,
                        )
                    } else {
                        onSignIn(
                            email,
                            password,
                        )
                    }
                },
                modifier =
                    Modifier.fillMaxWidth(),
                enabled =
                    !isWorking &&
                            email.isNotBlank() &&
                            password.isNotBlank(),
                icon = YanindaIconType.CHECK,
            )

            YanindaOutlinedButton(
                text =
                    if (createMode) {
                        "ZATEN HESABIM VAR"
                    } else {
                        "YENİ HESAP OLUŞTUR"
                    },
                onClick = {
                    createMode =
                        !createMode
                },
                modifier =
                    Modifier.fillMaxWidth(),
                enabled = !isWorking,
                icon = YanindaIconType.PERSON,
            )

            if (!createMode) {
                YanindaOutlinedButton(
                    text =
                        "ŞİFREMİ UNUTTUM",
                    onClick = {
                        onPasswordReset(email)
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                    enabled =
                        !isWorking &&
                                email.isNotBlank(),
                    icon =
                        YanindaIconType.LOCK,
                )
            }
        }
    }
}

@Composable
private fun SignedInWithoutFamilyCard(
    email: String,
    isWorking: Boolean,
    onCreateFamily: (
        String,
        String,
    ) -> Unit,
    onJoinFamily: (
        String,
        String,
    ) -> Unit,
    onSignOut: () -> Unit,
) {
    var familyName by rememberSaveable {
        mutableStateOf("")
    }

    var displayName by rememberSaveable {
        mutableStateOf("")
    }

    var invitationCode by rememberSaveable {
        mutableStateOf("")
    }

    var deviceName by rememberSaveable {
        mutableStateOf("")
    }

    YanindaCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement =
                Arrangement.spacedBy(14.dp),
        ) {
            YanindaSectionTitle(
                title = "Aile bağlantısı kur",
                icon = YanindaIconType.FAMILY,
            )

            Text(
                text = email,
                style =
                    MaterialTheme.typography
                        .bodyMedium,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
            )

            Text(
                text = "Yeni aile oluştur",
                style =
                    MaterialTheme.typography
                        .titleLarge,
            )

            OutlinedTextField(
                value = familyName,
                onValueChange = {
                    familyName = it.take(80)
                },
                modifier =
                    Modifier.fillMaxWidth(),
                label = {
                    Text("Aile adı")
                },
                singleLine = true,
                enabled = !isWorking,
            )

            OutlinedTextField(
                value = displayName,
                onValueChange = {
                    displayName = it.take(80)
                },
                modifier =
                    Modifier.fillMaxWidth(),
                label = {
                    Text("Senin adın")
                },
                singleLine = true,
                enabled = !isWorking,
            )

            YanindaPrimaryButton(
                text = "AİLE OLUŞTUR",
                onClick = {
                    onCreateFamily(
                        familyName,
                        displayName,
                    )
                },
                modifier =
                    Modifier.fillMaxWidth(),
                enabled =
                    !isWorking &&
                            familyName.isNotBlank() &&
                            displayName.isNotBlank(),
                icon = YanindaIconType.FAMILY,
            )

            Text(
                text = "veya",
                style =
                    MaterialTheme.typography
                        .bodyLarge,
            )

            Text(
                text = "Mevcut aileye katıl",
                style =
                    MaterialTheme.typography
                        .titleLarge,
            )

            OutlinedTextField(
                value = invitationCode,
                onValueChange = {
                    invitationCode =
                        it.take(32)
                },
                modifier =
                    Modifier.fillMaxWidth(),
                label = {
                    Text("Eşleştirme kodu")
                },
                singleLine = true,
                enabled = !isWorking,
            )

            OutlinedTextField(
                value = deviceName,
                onValueChange = {
                    deviceName =
                        it.take(80)
                },
                modifier =
                    Modifier.fillMaxWidth(),
                label = {
                    Text("Bu telefonun adı")
                },
                singleLine = true,
                enabled = !isWorking,
            )

            YanindaOutlinedButton(
                text = "AİLEYE KATIL",
                onClick = {
                    onJoinFamily(
                        invitationCode,
                        deviceName,
                    )
                },
                modifier =
                    Modifier.fillMaxWidth(),
                enabled =
                    !isWorking &&
                            invitationCode.isNotBlank() &&
                            deviceName.isNotBlank(),
                icon = YanindaIconType.FAMILY,
            )

            YanindaOutlinedButton(
                text = "OTURUMU KAPAT",
                onClick = onSignOut,
                modifier =
                    Modifier.fillMaxWidth(),
                enabled = !isWorking,
                icon = YanindaIconType.LOCK,
            )
        }
    }
}

@Composable
private fun AccountValue(
    label: String,
    value: String,
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style =
                MaterialTheme.typography
                    .labelLarge,
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant,
        )

        Text(
            text = value,
            style =
                MaterialTheme.typography
                    .bodyLarge,
        )
    }
}

private fun FamilyAccessMessage
        .accountTitle(): String =
    when (this) {
        FamilyAccessMessage.PASSWORD_RESET_SENT ->
            "E-posta gönderildi"

        FamilyAccessMessage.WEAK_PASSWORD ->
            "Şifre çok zayıf"

        FamilyAccessMessage.INVALID_CREDENTIALS ->
            "Giriş bilgileri hatalı"

        FamilyAccessMessage.ACCOUNT_UNAVAILABLE ->
            "Hesap kullanılamıyor"

        FamilyAccessMessage.NETWORK_UNAVAILABLE ->
            "Bağlantı sorunu"

        FamilyAccessMessage.PERMISSION_DENIED ->
            "Yetki yok"

        FamilyAccessMessage.INVALID_INPUT ->
            "Bilgileri kontrol et"

        FamilyAccessMessage.FIREBASE_NOT_CONFIGURED ->
            "Bağlantı hazır değil"

        FamilyAccessMessage.INVITATION_INVALID,
        FamilyAccessMessage.INVITATION_EXPIRED,
        FamilyAccessMessage.INVITATION_ALREADY_USED,
        FamilyAccessMessage.WRONG_DEVICE_ROLE,
            ->
            "Aile bağlantısı kurulamadı"

        FamilyAccessMessage.UNKNOWN_FAILURE ->
            "İşlem tamamlanamadı"
    }

private fun FamilyAccessMessage
        .accountBody(): String =
    when (this) {
        FamilyAccessMessage.PASSWORD_RESET_SENT ->
            "Şifre yenileme bağlantısı e-posta adresine gönderildi."

        FamilyAccessMessage.WEAK_PASSWORD ->
            "Daha güçlü bir şifre gir."

        FamilyAccessMessage.INVALID_CREDENTIALS ->
            "E-posta veya şifreyi kontrol edip tekrar dene."

        FamilyAccessMessage.ACCOUNT_UNAVAILABLE ->
            "Bu e-posta adresi başka bir hesapta kullanılıyor olabilir."

        FamilyAccessMessage.NETWORK_UNAVAILABLE ->
            "İnternet bağlantısını kontrol edip tekrar dene."

        FamilyAccessMessage.PERMISSION_DENIED ->
            "Bu işlemi yapmaya yetkin bulunmuyor."

        FamilyAccessMessage.INVALID_INPUT ->
            "Girilen bilgileri kontrol edip tekrar dene."

        FamilyAccessMessage.FIREBASE_NOT_CONFIGURED ->
            "Firebase bağlantısı bu sürümde hazır değil."

        FamilyAccessMessage.INVITATION_INVALID ->
            "Eşleştirme kodu geçerli değil."

        FamilyAccessMessage.INVITATION_EXPIRED ->
            "Eşleştirme kodunun süresi dolmuş."

        FamilyAccessMessage.INVITATION_ALREADY_USED ->
            "Bu eşleştirme kodu daha önce kullanılmış."

        FamilyAccessMessage.WRONG_DEVICE_ROLE ->
            "Bu kod farklı bir cihaz türü için oluşturulmuş."

        FamilyAccessMessage.UNKNOWN_FAILURE ->
            "Beklenmeyen bir hata oluştu."
    }