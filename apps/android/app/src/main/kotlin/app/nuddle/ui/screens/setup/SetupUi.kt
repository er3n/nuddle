package app.nuddle.ui.screens.setup

import androidx.compose.runtime.Immutable
import app.nuddle.domain.SetupStep

/** The setup route's state: one of the three screens. */
@Immutable
sealed interface SetupUiState {
    /** 2c. [starting] while the 3 s before the demo poke run. */
    data class Intro(
        val starting: Boolean = false,
    ) : SetupUiState

    /** 2d. [settingsLeft] is the "2" of "2 ayar gerekiyor"; the line is hidden at 0. */
    data class Demo(
        val settingsLeft: Int,
    ) : SetupUiState

    /** 2e. [settingsCount] is the "2" of the title, counted when the steps opened. */
    data class Steps(
        val settingsCount: Int,
        val notificationsGranted: Boolean,
        val steps: List<PermissionStepUi>,
        val active: SetupStep,
    ) : SetupUiState
}

@Immutable
data class PermissionStepUi(
    val step: SetupStep,
    val granted: Boolean,
    val skipped: Boolean,
)
