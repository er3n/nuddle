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
import app.nuddle.data.sync.AlarmRearmer
import app.nuddle.domain.MissedRingRules
import app.nuddle.ui.screens.home.writtenText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
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

/**
 * 2h. Opening a step's page is remembered; coming back re-checks what the phone can tell by itself. Opened while the bell
 * warns about no loss, the person reports it themselves (NUD-126): the steps start again from that moment.
 */
@HiltViewModel
class MissedRingViewModel
    @Inject
    constructor(
        private val missedRings: MissedRings,
        private val store: MissedRingStore,
        private val fixes: BrandFixRepository,
        private val battery: BatteryOptimization,
        private val alarms: AlarmRearmer,
        private val clock: Clock,
        private val timeZone: TimeZone,
    ) : ViewModel() {
        private val load = MutableStateFlow<Load>(Load.Loading)
        private val batteryIgnored = MutableStateFlow(battery.ignored())

        /** When the person opened 2h without a warned loss; null while unknown or for a detected loss. */
        private val reportedAt = MutableStateFlow<Instant?>(null)
        private val rearm = MutableStateFlow(RearmUi.Idle)

        private val withoutRearm: Flow<MissedRingUiState> =
            combine(missedRings.latest, store.state, load, batteryIgnored, reportedAt) { lost, local, load, ignored, reported ->
                val loaded = (load as? Load.Loaded)?.fix
                // A self report starts the steps again: only what was opened after it counts.
                val since = reported ?: lost
                MissedRingUiState(
                    lostAt = lost?.takeIf { reported == null }?.let { writtenText(it, clock.now(), timeZone) },
                    reason = loaded?.reason,
                    steps =
                        loaded?.steps.orEmpty().map {
                            BrandStepUi(it.id, it.title, it.hint, done = it.done(local, since, ignored))
                        },
                    loading = load == Load.Loading,
                    failed = load == Load.Failed,
                    reported = reported != null,
                )
            }

        val state: StateFlow<MissedRingUiState> =
            combine(withoutRearm, rearm) { ui, rearm -> ui.copy(rearm = rearm) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MissedRingUiState())

        init {
            viewModelScope.launch { if (missedRings.warning.first() == null) reportedAt.value = clock.now() }
            fetch()
        }

        /** "Dürtmelerimi yeniden kur": every alarm of this phone set again from the stored plans (NUD-126). */
        fun onRearm() {
            if (rearm.value == RearmUi.Busy) return
            rearm.value = RearmUi.Busy
            viewModelScope.launch {
                alarms.rearm()
                rearm.value = RearmUi.Done
            }
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
