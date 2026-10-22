package app.nuddle.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nuddle.ui.screens.devices.DevicesCallbacks
import app.nuddle.ui.screens.devices.DevicesScreen
import app.nuddle.ui.screens.devices.DevicesViewModel

/** The `devices` route (5e Cihazlarım, NUD-142), from profile. */
@Composable
fun DevicesRoute(
    onBack: () -> Unit,
    viewModel: DevicesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DevicesScreen(
        state = state,
        callbacks =
            DevicesCallbacks(
                onBack = onBack,
                onRetry = viewModel::onRetry,
                onSignOutOthers = viewModel::onSignOutOthers,
                onConfirm = viewModel::onConfirm,
                onDismiss = viewModel::onDismiss,
            ),
    )
}
