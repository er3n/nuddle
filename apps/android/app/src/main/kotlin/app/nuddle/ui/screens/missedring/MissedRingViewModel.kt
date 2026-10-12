package app.nuddle.ui.screens.missedring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nuddle.api.model.BrandFix
import app.nuddle.api.model.BrandFixStep
import app.nuddle.api.model.BrandFixTarget
import app.nuddle.data.BatteryOptimization
import app.nuddle.data.BrandFixRepository
import app.nuddle.data.MissedRings
import app.nuddle.data.session.MissedRingState
import app.nuddle.data.session.MissedRingStore
import app.nuddle.domain.MissedRingRules
import app.nuddle.ui.screens.home.writtenText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Instant

private const val STOP_TIMEOUT_MS = 5_000L

/** The brand steps from the server, while loading and when they could not be loaded. */
private sealed interface Load {
    data object Loading : Load

    data object Failed : Load

    data class Loaded(
        val fix: BrandFix,
    ) : Load
}

/** 2h. Opening a step's page is remembered; coming back re-checks what the phone can tell by itself. */
@HiltViewModel
class MissedRingViewModel
    @Inject
    constructor(
        private val missedRings: MissedRings,
        private val store: MissedRingStore,
        private val fixes: BrandFixRepository,
        private val battery: BatteryOptimization,
        private val clock: Clock,
        private val timeZone: TimeZone,
    ) : ViewModel() {
        private val load = MutableStateFlow<Load>(Load.Loading)
        private val batteryIgnored = MutableStateFlow(battery.ignored())

        val state: StateFlow<MissedRingUiState> =
            combine(missedRings.latest, store.state, load, batteryIgnored) { lost, local, load, ignored ->
                val loaded = (load as? Load.Loaded)?.fix
                MissedRingUiState(
                    lostAt = lost?.let { writtenText(it, clock.now(), timeZone) },
                    reason = loaded?.reason,
                    steps =
                        loaded?.steps.orEmpty().map {
                            BrandStepUi(it.id, it.title, it.hint, done = it.done(local, lost, ignored))
                        },
                    loading = load == Load.Loading,
                    failed = load == Load.Failed,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MissedRingUiState())

        init {
            fetch()
        }

        fun onRetry() = fetch()

        private fun fetch() {
            load.value = Load.Loading
            viewModelScope.launch {
                load.value = fixes.forThisPhone()?.let { Load.Loaded(it) } ?: Load.Failed
                markFixedWhenDone()
            }
        }

        /** Where "Ayarı aç" of [stepId] may go, in order. */
        fun targets(stepId: String): List<BrandFixTarget> =
            (load.value as? Load.Loaded)
                ?.fix
                ?.steps
                ?.firstOrNull { it.id == stepId }
                ?.targets
                .orEmpty()

        /** The step's page opened: it counts as done (open-questions#38). */
        fun onOpened(stepId: String) {
            viewModelScope.launch { store.opened(stepId, clock.now()) }
        }

        /** Back from a settings page. */
        fun onResume() {
            batteryIgnored.value = battery.ignored()
            viewModelScope.launch { markFixedWhenDone() }
        }

        /** Every step done after the loss: the bell stops warning about it (2g). */
        private suspend fun markFixedWhenDone() {
            val fix = (load.value as? Load.Loaded)?.fix ?: return
            val lost = missedRings.latest.first() ?: return
            val local = store.state.first()
            if (local.fixedAt?.let { it > lost } == true) return
            if (fix.steps.all { it.done(local, lost, batteryIgnored.value) }) store.fixed(clock.now())
        }

        private fun BrandFixStep.done(
            local: MissedRingState,
            lost: Instant?,
            batteryIgnored: Boolean,
        ): Boolean =
            MissedRingRules.stepDone(
                checkPasses = batteryIgnored.takeIf { check == BrandFixStep.Check.BATTERY_OPTIMIZATION },
                openedAt = local.opened[id],
                lostAt = lost,
            )
    }
