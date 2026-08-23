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
    val nextMedicationName: String? = null,
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
        viewModelScope.launch {
            while (currentCoroutineContext().isActive) {
                refresh()
                val millisIntoMinute = Math.floorMod(
                    timeProvider.now().toEpochMilli(),
                    MILLIS_PER_MINUTE,
                )
                delay(MILLIS_PER_MINUTE - millisIntoMinute)
            }
        }
    }

    private suspend fun refresh() {
        val now = timeProvider.now()
        val zoneId = timeProvider.currentZoneId()

        mutableState.value = try {
            val result = occurrenceRepository.calculateNextOccurrence()
            val medName = result.occurrence?.let {
                medicationRepository.get(it.medicationId)?.medication?.displayName
            }
            GrandfatherHomeUiState(
                now = now,
                zoneId = zoneId,
                nextMedicationAt = result.occurrence?.scheduledAt,
                nextMedicationName = medName,
                nextMedicationAvailability = when {
                    result.occurrence != null -> NextMedicationAvailability.AVAILABLE
                    else -> NextMedicationAvailability.NONE
                }
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
    var showCallUnavailable by remember { mutableStateOf(false) }
    val zonedNow = state.now.atZone(state.zoneId)
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
        nextMedicationName = state.nextMedicationName,
        onCallFamily = onCallFamily?.let { callFamily ->
            {
                if (!callFamily()) showCallUnavailable = true
            }
        },
        statusTone = statusTone,
        statusSymbol = statusSymbol,
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
