package app.nuddle.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nuddle.ui.screens.task.TaskDetailCallbacks
import app.nuddle.ui.screens.task.TaskDetailScreen
import app.nuddle.ui.screens.task.TaskDetailViewModel

/** The `task/{taskId}` route (7c, 7d). Goes back once the task is closed, here or by a sync. */
@Composable
fun TaskDetailRoute(
    onBack: () -> Unit,
    viewModel: TaskDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val back by rememberUpdatedState(onBack)
    LaunchedEffect(viewModel) { viewModel.close.collect { back() } }
    val current = state ?: return
    TaskDetailScreen(
        state = current,
        callbacks =
            TaskDetailCallbacks(
                onBack = onBack,
                onCancel = viewModel::onCancel,
                onReason = viewModel::onReason,
                onCancelDismiss = viewModel::onCancelDismiss,
                onCancelConfirm = viewModel::onCancelConfirm,
                onEdit = viewModel::onEdit,
                onDeadlineChoice = viewModel::onDeadlineChoice,
                onTimePicked = viewModel::onTimePicked,
                onPhotoChoice = viewModel::onPhotoChoice,
                onEditDismiss = viewModel::onEditDismiss,
                onEditSave = viewModel::onEditSave,
            ),
    )
}
