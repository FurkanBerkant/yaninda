package com.berkant.yaninda.ui.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.berkant.yaninda.YanindaApplication
import com.berkant.yaninda.domain.family.FamilyContact
import com.berkant.yaninda.ui.components.YanindaCard
import com.berkant.yaninda.ui.components.YanindaIconBadge
import com.berkant.yaninda.ui.components.YanindaIconType
import com.berkant.yaninda.ui.components.YanindaOutlinedButton
import com.berkant.yaninda.ui.components.YanindaPrimaryButton
import com.berkant.yaninda.ui.components.YanindaSectionTitle

@Composable
fun AdminFamilyContactsRoute(
    familyId: String?,
    onBack: () -> Unit,
) {
    val application =
        LocalContext.current.applicationContext as YanindaApplication

    val factory =
        remember(application, familyId) {
            AdminFamilyContactsViewModel.Factory(
                familyId = familyId,
                familyRepository =
                    application.familyRepository,
            )
        }

    val viewModel: AdminFamilyContactsViewModel =
        viewModel(
            key = "family-contacts-${familyId ?: "none"}",
            factory = factory,
        )

    val state by
    viewModel.state.collectAsStateWithLifecycle()

    AdminFamilyContactsScreen(
        state = state,
        onBack = onBack,
        onAddContact = viewModel::addContact,
        onMakeDefault = viewModel::makeDefault,
        onDeleteContact = viewModel::deleteContact,
        onDismissMessage = viewModel::clearMessage,
    )
}

@Composable
private fun AdminFamilyContactsScreen(
    state: AdminFamilyContactsUiState,
    onBack: () -> Unit,
    onAddContact: (
        displayName: String,
        phoneNumber: String,
    ) -> Unit,
    onMakeDefault: (FamilyContact) -> Unit,
    onDeleteContact: (FamilyContact) -> Unit,
    onDismissMessage: () -> Unit,
) {
    var showAddDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var contactToDelete by remember {
        mutableStateOf<FamilyContact?>(null)
    }

    BackHandler {
        onBack()
    }

    LaunchedEffect(state.message) {
        if (
            state.message ==
            AdminFamilyContactMessage.SAVED
        ) {
            showAddDialog = false
        }

        if (
            state.message ==
            AdminFamilyContactMessage.DELETED
        ) {
            contactToDelete = null
        }
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
                text = "Aile Kişileri",
                style =
                    MaterialTheme.typography
                        .headlineLarge,
            )

            Text(
                text =
                    "Dede telefonundaki AİLEYİ ARA düğmesinde kullanılacak aile kişilerini buradan yönetebilirsin.",
                style =
                    MaterialTheme.typography.bodyLarge,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
                modifier =
                    Modifier.padding(top = 6.dp),
            )
        }

        if (state.isLoading) {
            item {
                Text(
                    text = "Aile kişileri yükleniyor...",
                    style =
                        MaterialTheme.typography.bodyLarge,
                )
            }
        }

        if (
            !state.isLoading &&
            state.contacts.isEmpty()
        ) {
            item {
                YanindaCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically,
                        horizontalArrangement =
                            Arrangement.spacedBy(14.dp),
                    ) {
                        YanindaIconBadge(
                            icon = YanindaIconType.FAMILY,
                            size = 56.dp,
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement =
                                Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text =
                                    "Henüz aile kişisi yok",
                                style =
                                    MaterialTheme.typography
                                        .titleLarge,
                            )

                            Text(
                                text =
                                    "AİLEYİ ARA özelliğini kullanmak için en az bir kişi ekle.",
                                style =
                                    MaterialTheme.typography
                                        .bodyMedium,
                                color =
                                    MaterialTheme.colorScheme
                                        .onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        items(
            items = state.contacts,
            key = { it.contactId },
        ) { contact ->

            FamilyContactCard(
                contact = contact,
                isWorking = state.isWorking,
                onMakeDefault = {
                    onMakeDefault(contact)
                },
                onDelete = {
                    contactToDelete = contact
                },
            )
        }

        item {
            YanindaPrimaryButton(
                text = "YENİ KİŞİ EKLE",
                onClick = {
                    showAddDialog = true
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp),
                enabled = !state.isWorking,
                icon = YanindaIconType.ADD,
            )
        }
    }

    if (showAddDialog) {
        AddFamilyContactDialog(
            isWorking = state.isWorking,
            onDismiss = {
                if (!state.isWorking) {
                    showAddDialog = false
                }
            },
            onSave = onAddContact,
        )
    }

    contactToDelete?.let { contact ->
        AlertDialog(
            onDismissRequest = {
                if (!state.isWorking) {
                    contactToDelete = null
                }
            },
            title = {
                Text("Aile kişisini sil")
            },
            text = {
                Text(
                    "${contact.displayName} kişisini silmek istediğine emin misin?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteContact(contact)
                    },
                    enabled = !state.isWorking,
                ) {
                    Text("SİL")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        contactToDelete = null
                    },
                    enabled = !state.isWorking,
                ) {
                    Text("VAZGEÇ")
                }
            },
        )
    }

    state.message?.let { message ->

        /*
         * Save dialog açıkken INVALID_INPUT oluşursa
         * formu kapatmıyoruz; kullanıcı bilgiyi
         * düzeltebilsin.
         */
        if (
            message !=
            AdminFamilyContactMessage.SAVED
        ) {
            AlertDialog(
                onDismissRequest = onDismissMessage,
                title = {
                    Text(
                        message.titleText()
                    )
                },
                text = {
                    Text(
                        message.bodyText()
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = onDismissMessage
                    ) {
                        Text("TAMAM")
                    }
                },
            )
        }
    }
}

@Composable
private fun FamilyContactCard(
    contact: FamilyContact,
    isWorking: Boolean,
    onMakeDefault: () -> Unit,
    onDelete: () -> Unit,
) {
    YanindaCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement =
                Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.spacedBy(14.dp),
            ) {
                YanindaIconBadge(
                    icon = YanindaIconType.PERSON,
                    size = 56.dp,
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement =
                        Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = contact.displayName,
                        style =
                            MaterialTheme.typography
                                .titleLarge,
                    )

                    Text(
                        text = contact.phoneNumber,
                        style =
                            MaterialTheme.typography
                                .bodyLarge,
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                    )
                }
            }

            if (contact.isDefault) {
                Surface(
                    shape =
                        MaterialTheme.shapes.large,
                    color =
                        MaterialTheme.colorScheme
                            .primaryContainer,
                    contentColor =
                        MaterialTheme.colorScheme
                            .onPrimaryContainer,
                ) {
                    Text(
                        text = "✓ AİLEYİ ARA kişisi",
                        style =
                            MaterialTheme.typography
                                .titleMedium,
                        modifier =
                            Modifier.padding(
                                horizontal = 14.dp,
                                vertical = 10.dp,
                            ),
                    )
                }

            } else {
                YanindaOutlinedButton(
                    text = "ARAMA KİŞİSİ YAP",
                    onClick = onMakeDefault,
                    modifier =
                        Modifier.fillMaxWidth(),
                    enabled = !isWorking,
                    icon = YanindaIconType.CHECK,
                )
            }

            YanindaOutlinedButton(
                text = "SİL",
                onClick = onDelete,
                modifier =
                    Modifier.fillMaxWidth(),
                enabled = !isWorking,
                icon = YanindaIconType.WARNING,
            )
        }
    }
}

@Composable
private fun AddFamilyContactDialog(
    isWorking: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        displayName: String,
        phoneNumber: String,
    ) -> Unit,
) {
    var displayName by rememberSaveable {
        mutableStateOf("")
    }

    var phoneNumber by rememberSaveable {
        mutableStateOf("")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Yeni aile kişisi")
        },
        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text =
                        "Dede gerektiğinde bu kişiyi arayabilir.",
                    style =
                        MaterialTheme.typography
                            .bodyMedium,
                )

                OutlinedTextField(
                    value = displayName,
                    onValueChange = {
                        displayName =
                            it.take(80)
                    },
                    label = {
                        Text("İsim")
                    },
                    singleLine = true,
                    enabled = !isWorking,
                    modifier =
                        Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = {
                        phoneNumber =
                            it.take(24)
                    },
                    label = {
                        Text("Telefon numarası")
                    },
                    placeholder = {
                        Text(
                            "+90 5xx xxx xx xx"
                        )
                    },
                    supportingText = {
                        Text(
                            "Örnek: 05321234567 veya +905321234567"
                        )
                    },
                    singleLine = true,
                    enabled = !isWorking,
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType =
                                KeyboardType.Phone
                        ),
                    modifier =
                        Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        displayName,
                        phoneNumber,
                    )
                },
                enabled =
                    !isWorking &&
                            displayName.isNotBlank() &&
                            phoneNumber.isNotBlank(),
            ) {
                Text(
                    if (isWorking) {
                        "KAYDEDİLİYOR..."
                    } else {
                        "KAYDET"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isWorking,
            ) {
                Text("VAZGEÇ")
            }
        },
    )
}

private fun AdminFamilyContactMessage
        .titleText(): String =
    when (this) {
        AdminFamilyContactMessage.SAVED ->
            "Kişi kaydedildi"

        AdminFamilyContactMessage.DELETED ->
            "Kişi silindi"

        AdminFamilyContactMessage.DEFAULT_CHANGED ->
            "Arama kişisi değiştirildi"

        AdminFamilyContactMessage.INVALID_INPUT ->
            "Bilgileri kontrol et"

        AdminFamilyContactMessage.NO_FAMILY ->
            "Aile bağlantısı yok"

        AdminFamilyContactMessage.NOT_AUTHENTICATED ->
            "Oturum gerekli"

        AdminFamilyContactMessage.PERMISSION_DENIED ->
            "Yetki yok"

        AdminFamilyContactMessage.NETWORK_UNAVAILABLE ->
            "Bağlantı sorunu"

        AdminFamilyContactMessage.NOT_CONFIGURED ->
            "Bağlantı hazır değil"

        AdminFamilyContactMessage.UNKNOWN_FAILURE ->
            "İşlem tamamlanamadı"
    }

private fun AdminFamilyContactMessage
        .bodyText(): String =
    when (this) {
        AdminFamilyContactMessage.SAVED ->
            "Aile kişisi kaydedildi."

        AdminFamilyContactMessage.DELETED ->
            "Aile kişisi silindi."

        AdminFamilyContactMessage.DEFAULT_CHANGED ->
            "Dede telefonundaki AİLEYİ ARA düğmesi artık bu kişiyi kullanacak."

        AdminFamilyContactMessage.INVALID_INPUT ->
            "İsim ve telefon numarasını kontrol edip tekrar dene."

        AdminFamilyContactMessage.NO_FAMILY ->
            "Önce bir aile bağlantısı oluşturulmalı."

        AdminFamilyContactMessage.NOT_AUTHENTICATED ->
            "Bu işlem için tekrar giriş yapman gerekiyor."

        AdminFamilyContactMessage.PERMISSION_DENIED ->
            "Bu ailede kişi yönetme yetkin bulunmuyor."

        AdminFamilyContactMessage.NETWORK_UNAVAILABLE ->
            "İnternet bağlantısı kurulamadı. Bağlantı geldiğinde tekrar dene."

        AdminFamilyContactMessage.NOT_CONFIGURED ->
            "Firebase bağlantısı bu sürümde hazır değil."

        AdminFamilyContactMessage.UNKNOWN_FAILURE ->
            "Beklenmeyen bir hata oluştu. Tekrar deneyebilirsin."
    }