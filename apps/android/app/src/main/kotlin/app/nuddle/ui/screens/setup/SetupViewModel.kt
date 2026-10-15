package app.nuddle.ui.screens.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nuddle.data.SessionStarter
import app.nuddle.data.session.SetupStore
import app.nuddle.di.ApplicationScope
import app.nuddle.domain.PermissionReader
import app.nuddle.domain.SetupRules
import app.nuddle.domain.SetupStep
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** "3 saniye sonra çalar" (2c). */
internal const val DEMO_DELAY_MS = 3_000L

/** What the setup route has to do outside Compose: system dialogs and settings pages. */
sealed interface SetupEffect {
    /** The notification permission: the system dialog, or the app's notification settings once it is denied for good. */
    data object RequestNotifications : SetupEffect

    data class OpenSettings(
        val step: SetupStep,
    ) : SetupEffect

    data object Finished : SetupEffect
}

/**
 * Setup (docs/product.md, Permissions and setup): 2c, the demo poke (2d), then the steps (2e). Permissions are read
 * again whenever the screen comes back from a dialog or a settings page, so a granted step moves on by itself.
 */
@HiltViewModel
class SetupViewModel
    @Inject
    constructor(
        private val setup: SetupStore,
        private val permissions: PermissionReader,
        private val starter: SessionStarter,
        @param:ApplicationScope private val appScope: CoroutineScope,
    ) : ViewModel() {
        private val ui = MutableStateFlow<SetupUiState>(SetupUiState.Intro())
        val state: StateFlow<SetupUiState> = ui.asStateFlow()

        private val effectChannel = Channel<SetupEffect>(Channel.BUFFERED)
        val effects: Flow<SetupEffect> = effectChannel.receiveAsFlow()

        private val skipped = mutableSetOf<SetupStep>()

        /** Fixed when the steps open, so the title does not count down while the person works through them. */
        private var settingsCount = 0
        private var finished = false

        /** Bir dene: the demo poke rings after 3 s. */
        fun onTry() {
            val intro = ui.value as? SetupUiState.Intro ?: return
            if (intro.starting) return
            ui.value = intro.copy(starting = true)
            viewModelScope.launch {
                delay(DEMO_DELAY_MS)
                ui.value = SetupUiState.Demo(settingsLeft = SetupRules.settingsLeft(permissions.read()))
            }
        }

        /** Denemeden kur on 2c, or Anladım, kuralım on 2d. */
        fun onStartSteps() {
            if (ui.value is SetupUiState.Steps) return
            settingsCount = SetupRules.settingsLeft(permissions.read())
            val active = refresh()
            if (active == SetupStep.NOTIFICATIONS) effectChannel.trySend(SetupEffect.RequestNotifications)
        }

        /** Ayarları aç (or İzin ver) on the active step. */
        fun onOpenActive() {
            val active = (ui.value as? SetupUiState.Steps)?.active ?: return
            effectChannel.trySend(
                if (active == SetupStep.NOTIFICATIONS) SetupEffect.RequestNotifications else SetupEffect.OpenSettings(active),
            )
        }

        /** "Alarmsız devam et" and the same for full screen; notifications cannot be skipped. */
        fun onSkipActive() {
            val active = (ui.value as? SetupUiState.Steps)?.active ?: return
            if (!active.skippable) return
            skipped += active
            refresh()
        }

        /** Back from a system dialog or a settings page. */
        fun onResume() {
            if (ui.value is SetupUiState.Steps) refresh()
        }

        private fun refresh(): SetupStep? {
            val granted = permissions.read()
            val active = SetupRules.active(granted, skipped)
            if (active == null) {
                finish()
                return null
            }
            ui.value =
                SetupUiState.Steps(
                    settingsCount = settingsCount,
                    notificationsGranted = granted.notifications,
                    steps =
                        granted.steps.map { step ->
                            PermissionStepUi(step, granted = granted.has(step), skipped = step in skipped)
                        },
                    active = active,
                )
            return active
        }

        /**
         * The new permissions go up with the device registration, so reachability is right from the first poke. That
         * call outlives this screen: Finished closes it, which would cancel it in `viewModelScope`.
         */
        private fun finish() {
            if (finished) return
            finished = true
            viewModelScope.launch {
                setup.markDone()
                effectChannel.send(SetupEffect.Finished)
            }
            appScope.launch { starter.start() }
        }
    }
