package com.berkant.yaninda.ui.grandfather

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.berkant.yaninda.R
import com.berkant.yaninda.YanindaApplication
import com.berkant.yaninda.core.time.TimeProvider
import com.berkant.yaninda.data.repository.DoseOccurrenceRepository
import com.berkant.yaninda.data.repository.MedicationRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import com.berkant.yaninda.notification.FullScreenIntentCapability
import com.berkant.yaninda.notification.NotificationCapability
import com.berkant.yaninda.reminder.ExactAlarmCapability
import com.berkant.yaninda.reminder.ReminderRefreshFailure
import com.berkant.yaninda.reminder.ReminderRuntimeStatus
import com.berkant.yaninda.schedule.AlarmScheduleSyncStatus
enum class NextMedicationAvailability {
    LOADING,
    AVAILABLE,
    NONE,
    UNAVAILABLE,
}

data class GrandfatherHomeUiState(
    val now: Instant,
    val zoneId: ZoneId,
    val nextMedicationAt: Instant? = null,
    val nextMedicationNames: List<String> = emptyList(),
    val nextMedicationAvailability: NextMedicationAvailability = NextMedicationAvailability.LOADING,
)

class GrandfatherHomeViewModel(
    private val occurrenceRepository: DoseOccurrenceRepository,
    private val medicationRepository: MedicationRepository,
    private val timeProvider: TimeProvider,
) : ViewModel() {
    private val mutableState = MutableStateFlow(currentState())
    val state: StateFlow<GrandfatherHomeUiState> = mutableState.asStateFlow()

    init {
        /*
         * Room'daki ilaç programı değiştiğinde dede ekranını
         * anında güncelle.
         *
         * Örneğin ADMIN_DEVICE yeni schedule indirdiğinde:
         *
         * Firestore
         *   -> AlarmScheduleSyncCoordinator
         *   -> Room
         *   -> configurations Flow
         *   -> refresh()
         *
         * Böylece kullanıcı bir sonraki dakikayı beklemek zorunda kalmaz.
         */
        viewModelScope.launch {
            medicationRepository.configurations.collectLatest {
                refresh()
            }
        }

        /*
         * İlaç programı değişmese bile saat ilerlediği için
         * her dakika "sıradaki ilaç" hesabını yenilemeye devam et.
         */
        viewModelScope.launch {
            while (currentCoroutineContext().isActive) {
                val millisIntoMinute = Math.floorMod(
                    timeProvider.now().toEpochMilli(),
                    MILLIS_PER_MINUTE,
                )

                delay(
                    MILLIS_PER_MINUTE - millisIntoMinute
                )

                refresh()
            }
        }
    }

    private suspend fun refresh() {
        val now = timeProvider.now()
        val zoneId = timeProvider.currentZoneId()

        mutableState.value = try {
            val result =
                occurrenceRepository
                    .calculateNextDoseGroup()

            val group =
                result.group

            GrandfatherHomeUiState(
                now = now,
                zoneId = zoneId,

                nextMedicationAt =
                    group?.scheduledAt,

                nextMedicationNames =
                    group
                        ?.items
                        ?.map {
                            it.medicationDisplayName
                        }
                        .orEmpty(),

                nextMedicationAvailability =
                    when {
                        result.issues.isNotEmpty() ->
                            NextMedicationAvailability.UNAVAILABLE

                        group != null ->
                            NextMedicationAvailability.AVAILABLE

                        else ->
                            NextMedicationAvailability.NONE
                    },
            )
        } catch (error: CancellationException) {
            throw error
        } catch (e: Exception) {
            GrandfatherHomeUiState(
                now = now,
                zoneId = zoneId,
                nextMedicationAt = null,
                nextMedicationAvailability = NextMedicationAvailability.UNAVAILABLE,
            )
        }
    }

    private fun currentState(): GrandfatherHomeUiState = GrandfatherHomeUiState(
        now = timeProvider.now(),
        zoneId = timeProvider.currentZoneId(),
    )

    class Factory(
        private val occurrenceRepository: DoseOccurrenceRepository,
        private val medicationRepository: MedicationRepository,
        private val timeProvider: TimeProvider,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(GrandfatherHomeViewModel::class.java))
            return GrandfatherHomeViewModel(occurrenceRepository, medicationRepository, timeProvider) as T
        }
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}

@Composable
fun GrandfatherHomeRoute(
    onCallFamily: (() -> Boolean)?,
) {
    val application = LocalContext.current.applicationContext as YanindaApplication
    val factory = remember(application) {
        GrandfatherHomeViewModel.Factory(
            occurrenceRepository = application.doseOccurrenceRepository,
            medicationRepository = application.medicationRepository,
            timeProvider = application.timeProvider,
        )
    }
    val viewModel: GrandfatherHomeViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reminderStatus by
    application.reminderCoordinator.status
        .collectAsStateWithLifecycle()
    val scheduleSyncState =
        application
            .alarmScheduleSyncCoordinator
            ?.status
            ?.collectAsStateWithLifecycle()

    val scheduleSyncStatus =
        scheduleSyncState?.value
    var showCallUnavailable by remember { mutableStateOf(false) }
    val zonedNow = state.now.atZone(state.zoneId)
    val nextMedicationDayLabel =
        state.nextMedicationAt
            ?.let { nextMedicationAt ->
                formatNextMedicationDayLabel(
                    now = state.now,
                    nextMedicationAt = nextMedicationAt,
                    zoneId = state.zoneId,
                )
            }
    val nextMedicationText = when (state.nextMedicationAvailability) {
        NextMedicationAvailability.LOADING -> stringResource(R.string.home_next_loading)
        NextMedicationAvailability.NONE -> stringResource(R.string.home_next_none)
        NextMedicationAvailability.UNAVAILABLE -> stringResource(R.string.home_next_unavailable)
        NextMedicationAvailability.AVAILABLE -> checkNotNull(state.nextMedicationAt)
            .atZone(state.zoneId)
            .format(TIME_FORMATTER)
    }
    val statusText = stringResource(
        when (state.nextMedicationAvailability) {
            NextMedicationAvailability.LOADING -> R.string.home_status_loading
            NextMedicationAvailability.NONE -> R.string.home_status_no_schedule
            NextMedicationAvailability.UNAVAILABLE -> R.string.home_status_schedule_unavailable
            NextMedicationAvailability.AVAILABLE -> R.string.home_status_idle
        }
    )
    val statusTone = when (state.nextMedicationAvailability) {
        NextMedicationAvailability.AVAILABLE -> GrandfatherHomeStatusTone.POSITIVE
        NextMedicationAvailability.LOADING,
        NextMedicationAvailability.NONE,
        -> GrandfatherHomeStatusTone.NEUTRAL

        NextMedicationAvailability.UNAVAILABLE -> GrandfatherHomeStatusTone.ATTENTION
    }
    val statusSymbol = stringResource(
        when (state.nextMedicationAvailability) {
            NextMedicationAvailability.AVAILABLE -> R.string.home_status_symbol
            NextMedicationAvailability.LOADING -> R.string.home_status_loading_symbol
            NextMedicationAvailability.NONE -> R.string.home_status_info_symbol
            NextMedicationAvailability.UNAVAILABLE -> R.string.home_status_attention_symbol
        }
    )

    GrandfatherHomeScreen(
        dateText = zonedNow.format(DATE_FORMATTER),
        timeText = zonedNow.format(TIME_FORMATTER),
        statusText = statusText,
        nextMedicationTime = nextMedicationText,
        nextMedicationNames = state.nextMedicationNames,

        reminderHealthText =
            buildString {

                append(
                    scheduleSyncStatus
                        .toHealthText()
                )

                append("\n")

                append(
                    reminderStatus.toHealthText(
                        zoneId = state.zoneId,
                    )
                )
            },

        reminderHealthy =
            reminderStatus.isHealthy() &&
                    scheduleSyncStatus?.let {
                        it.isCurrent && !it.hasError
                    } == true,

        onCallFamily = onCallFamily?.let { callFamily ->
            {
                if (!callFamily()) {
                    showCallUnavailable = true
                }
            }
        },

        statusTone = statusTone,
        statusSymbol = statusSymbol,
        nextMedicationDayLabel = nextMedicationDayLabel,
    )

    if (showCallUnavailable) {
        AlertDialog(
            onDismissRequest = { showCallUnavailable = false },
            title = { Text(stringResource(R.string.call_unavailable_title)) },
            text = { Text(stringResource(R.string.prototype_call_notice)) },
            confirmButton = {
                Button(
                    onClick = { showCallUnavailable = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                ) {
                    Text(stringResource(R.string.prototype_notice_dismiss))
                }
            },
        )
    }
}

private val TURKISH_LOCALE: Locale = Locale.forLanguageTag("tr-TR")
private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern(
    "d MMMM EEEE",
    TURKISH_LOCALE,
)
private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern(
    "HH:mm",
    TURKISH_LOCALE,
)

internal fun formatNextMedicationDayLabel(
    now: Instant,
    nextMedicationAt: Instant,
    zoneId: ZoneId,
): String? {
    val today = now.atZone(zoneId).toLocalDate()
    val medicationDate = nextMedicationAt.atZone(zoneId).toLocalDate()

    return when (medicationDate) {
        today -> null
        today.plusDays(1) -> "Yarın"
        else -> medicationDate.format(DATE_FORMATTER)
    }
}

private fun ReminderRuntimeStatus.isHealthy(): Boolean {
    return exactAlarmCapability ==
            ExactAlarmCapability.AVAILABLE &&
            notificationCapability ==
            NotificationCapability.AVAILABLE &&
            fullScreenIntentCapability ==
            FullScreenIntentCapability.AVAILABLE &&
            refreshFailure == null &&
            failedOperationCount == 0
}
private fun ReminderRuntimeStatus.toHealthText(
    zoneId: ZoneId,
): String {

    val exactAlarmText =
        when (exactAlarmCapability) {
            ExactAlarmCapability.AVAILABLE ->
                "Kesin alarm hazır"

            ExactAlarmCapability.USER_ACTION_REQUIRED ->
                "Kesin alarm izni gerekli"

            ExactAlarmCapability.CHECK_FAILED ->
                "Kesin alarm kontrol edilemedi"

            ExactAlarmCapability.NOT_CHECKED ->
                "Kesin alarm henüz kontrol edilmedi"
        }

    val notificationText =
        when (notificationCapability) {
            NotificationCapability.AVAILABLE ->
                "Bildirimler hazır"

            NotificationCapability.RUNTIME_PERMISSION_REQUIRED ->
                "Bildirim izni gerekli"

            NotificationCapability.APP_NOTIFICATIONS_DISABLED ->
                "Bildirimler kapalı"

            NotificationCapability.CHANNEL_DISABLED ->
                "İlaç bildirim kanalı kapalı"

            NotificationCapability.CHANNEL_ATTENTION_REQUIRED ->
                "Bildirim ayarı kontrol edilmeli"

            NotificationCapability.CHECK_FAILED ->
                "Bildirim durumu kontrol edilemedi"

            NotificationCapability.NOT_CHECKED ->
                "Bildirimler henüz kontrol edilmedi"
        }

    val fullScreenText =
        when (fullScreenIntentCapability) {
            FullScreenIntentCapability.AVAILABLE ->
                "Tam ekran alarm hazır"

            FullScreenIntentCapability.USER_ACTION_REQUIRED ->
                "Tam ekran alarm izni gerekli"

            FullScreenIntentCapability.CHECK_FAILED ->
                "Tam ekran alarm kontrol edilemedi"

            FullScreenIntentCapability.NOT_CHECKED ->
                "Tam ekran alarm henüz kontrol edilmedi"
        }

    val nextAlarmText =
        nextAlarmAt?.let {
            "Sıradaki gerçek alarm: ${
                it.atZone(zoneId).format(TIME_FORMATTER)
            }"
        } ?: "Sıradaki gerçek alarm yok"

    val schedulingText =
        if (plannedOccurrenceCount > 0) {
            "Planlanan: $plannedOccurrenceCount • Kurulan: $scheduledAlarmCount"
        } else {
            "Planlanmış alarm yok"
        }

    val failureText =
        when (refreshFailure) {
            ReminderRefreshFailure.OCCURRENCE_PERSISTENCE ->
                "İlaç planı oluşturulamadı"

            ReminderRefreshFailure.ALARM_CANCELLATION ->
                "Eski alarm iptal edilirken hata oluştu"

            ReminderRefreshFailure.ALARM_SCHEDULING ->
                "Android alarmı kurulamadı"

            ReminderRefreshFailure.RESPONSE_WINDOW_SCHEDULING ->
                "Takip alarmı kurulamadı"

            null -> null
        }

    return buildList {
        add(exactAlarmText)
        add(notificationText)
        add(fullScreenText)
        add(schedulingText)
        add(nextAlarmText)

        failureText?.let(::add)
    }.joinToString("\n")
}
private fun AlarmScheduleSyncStatus?.toHealthText(): String {
    if (this == null) {
        return "Program senkronizasyonu kullanılamıyor"
    }

    if (hasError) {
        return """
            Program senkronizasyonu kontrol edilmeli
            Buluttaki sürüm: $desiredVersion
            Telefondaki sürüm: $appliedVersion
        """.trimIndent()
    }

    if (desiredVersion <= 0L) {
        return "Henüz yayınlanmış ilaç programı yok"
    }

    return """
        Program güncel: ${if (isCurrent) "Evet" else "Hayır"}
        Buluttaki sürüm: $desiredVersion
        Telefondaki sürüm: $appliedVersion
    """.trimIndent()
}
