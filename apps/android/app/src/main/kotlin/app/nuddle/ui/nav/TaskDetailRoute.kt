package app.nuddle.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nuddle.ui.screens.home.NewDeadlineCallbacks
import app.nuddle.ui.screens.poke.DropCallbacks
import app.nuddle.ui.screens.task.DetailAction
import app.nuddle.ui.screens.task.TaskDetailCallbacks
import app.nuddle.ui.screens.task.TaskDetailScreen
import app.nuddle.ui.screens.task.TaskDetailUiState
import app.nuddle.ui.screens.task.TaskDetailViewModel

/** The `task/{taskId}` route (7c, 7d, 7f and the detail of any open task). Goes back once the task leaves my lists. */
@Composable
fun TaskDetailRoute(
    onBack: () -> Unit,
    onTakePhoto: (taskId: String) -> Unit,
    onCall: (phone: String) -> Unit,
    onMessage: (phone: String) -> Unit,
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
                onAction = { action -> viewModel.answer(action, current) { onTakePhoto(viewModel.taskId) } },
                onCall = onCall,
                onMessage = onMessage,
                drop =
                    DropCallbacks(
                        onReason = viewModel.drop::onReason,
                        onNoteChange = viewModel.drop::onNoteChange,
                        onDrop = { note ->
                            viewModel.drop.onDismiss()
                            viewModel.answers.onCantToday(note)
                        },
                        onDismiss = viewModel.drop::onDismiss,
                    ),
                newDeadline =
                    NewDeadlineCallbacks(
                        onChoice = viewModel.newDeadline::onChoice,
                        onTimePicked = viewModel.newDeadline::onTimePicked,
                        onSave = viewModel.newDeadline::onSave,
                        onDismiss = viewModel.newDeadline::onDismiss,
                    ),
            ),
    )
}

/** A button at the bottom of the detail: an answer at once, a sheet, or the camera. */
private fun TaskDetailViewModel.answer(
    action: DetailAction,
    state: TaskDetailUiState,
    takePhoto: () -> Unit,
) {
    when (action) {
        DetailAction.DoneEarly -> answers.onDone(state.title)

        DetailAction.TakePhoto, DetailAction.NewPhoto -> takePhoto()

        // A task to self has nobody to leave a note for: it closes at once, as on the poke (7f).
        DetailAction.CantToday -> state.from?.let { drop.onOpen(it.name) } ?: answers.onCantToday(null)

        DetailAction.DidItLater -> answers.onDidItLater()

        DetailAction.RemoveMissed -> answers.onRemoveMissed()

        DetailAction.CancelTask -> onCancel()

        DetailAction.NewTime, DetailAction.Extend -> newDeadline.onOpen(state.toName)

        DetailAction.SenderHandles -> answers.onSenderHandles()

        DetailAction.CancelNow -> answers.onCancelNow()
    }
}
