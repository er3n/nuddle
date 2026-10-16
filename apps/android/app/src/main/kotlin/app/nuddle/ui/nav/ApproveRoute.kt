package app.nuddle.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nuddle.ui.screens.approve.ApproveCallbacks
import app.nuddle.ui.screens.approve.ApproveScreen
import app.nuddle.ui.screens.approve.ApproveViewModel

/** The `approve/{taskId}` route (4f). Goes back once answered, or once the task stops waiting (auto-approved). */
@Composable
fun ApproveRoute(
    onBack: () -> Unit,
    viewModel: ApproveViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackOnClose(viewModel.close, onBack)
    val current = state ?: return
    ApproveScreen(
        state = current,
        callbacks =
            ApproveCallbacks(
                onBack = onBack,
                onNoteChange = viewModel::onNoteChange,
                onApprove = viewModel::onApprove,
                onReRequest = viewModel::onReRequest,
                onRetryPhoto = viewModel::loadPhoto,
            ),
    )
}
