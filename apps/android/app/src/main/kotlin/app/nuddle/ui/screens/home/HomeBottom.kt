package app.nuddle.ui.screens.home

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.nuddle.R
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.ComposeBar
import app.nuddle.ui.components.ComposeState
import app.nuddle.ui.components.InlineAlert
import app.nuddle.ui.theme.DurtTheme

/** The undo toast or the done note, 6b's alert while a send failed, and the compose bar. */
@Composable
internal fun ColumnScope.HomeBottom(
    state: HomeUiState,
    undo: UndoCallbacks,
    onComposeTextChange: (String) -> Unit,
    onComposeSend: () -> Unit,
    onVoiceStart: () -> Unit,
    onVoiceEnd: (cancelled: Boolean) -> Unit,
) {
    val dimens = DurtTheme.dimens
    state.undo?.let { TaskUndoToast(it, undo, Modifier.padding(bottom = dimens.space10)) }
    state.doneNote?.let { TaskDoneNote(it, Modifier.padding(bottom = dimens.space10)) }
    if (state.compose is ComposeState.Failed) {
        InlineAlert(
            stringResource(R.string.compose_failed_title),
            stringResource(R.string.compose_failed_body),
            Modifier.padding(bottom = dimens.space10).testTag(TestTags.HOME_SEND_FAILED),
        )
    }
    ComposeBar(state.compose, onComposeTextChange, onComposeSend, onVoiceStart, onVoiceEnd)
}
