package app.nuddle.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.nuddle.R
import app.nuddle.ui.components.DurtBottomSheet
import app.nuddle.ui.components.DurtBottomSheetContent
import app.nuddle.ui.components.InlineAlert
import app.nuddle.ui.components.SheetAction
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.theme.DurtTheme
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

/** "Yeni zaman ver" or "Süreyi uzat" (5d): the 2b deadline picks for a dropped or missed task. */
@Immutable
data class NewDeadlineUi(
    val taskId: String,
    val toName: String,
    val choices: List<DeadlineChoice>,
    val picked: DeadlineChoice? = null,
    val saving: Boolean = false,
    val failed: Boolean = false,
    /** The latest time allowed (the task's own time) when the sheet moves the start (NUD-78); null for a deadline. */
    val limit: Instant? = null,
) {
    /** A picked time past the limit cannot be saved. */
    val pickedTooLate: Boolean get() = limit != null && picked != null && picked.at > limit
}

data class NewDeadlineCallbacks(
    val onChoice: (DeadlineChoice) -> Unit = {},
    val onTimePicked: (LocalTime) -> Unit = {},
    val onSave: () -> Unit = {},
    val onDismiss: () -> Unit = {},
)

@Composable
private fun saveAction(
    state: NewDeadlineUi,
    callbacks: NewDeadlineCallbacks,
) = SheetAction(
    text = stringResource(R.string.action_save),
    onClick = callbacks.onSave,
    enabled = state.picked != null && !state.pickedTooLate,
    loading = state.saving,
)

@Composable
fun NewDeadlineSheet(
    state: NewDeadlineUi,
    callbacks: NewDeadlineCallbacks,
    title: String = stringResource(R.string.preview_question_deadline),
    description: String = stringResource(R.string.issue_new_time_body, state.toName),
) {
    DurtBottomSheet(
        title = title,
        description = description,
        onDismiss = callbacks.onDismiss,
        primary = saveAction(state, callbacks),
    ) { NewDeadlineBody(state, callbacks) }
}

@Composable
private fun ColumnScope.NewDeadlineBody(
    state: NewDeadlineUi,
    callbacks: NewDeadlineCallbacks,
) {
    DeadlineChips(state.choices, state.picked, callbacks.onChoice, callbacks.onTimePicked)
    if (state.pickedTooLate) InlineAlert(stringResource(R.string.start_too_late_title), stringResource(R.string.start_too_late_body))
    if (state.failed) InlineAlert(stringResource(R.string.task_edit_failed_title), stringResource(R.string.invite_failed_body))
}

@PreviewScreens
@Composable
private fun NewDeadlineSheetPreview() {
    val evening = DeadlineChoice.TonightEvening(Instant.parse("2026-10-12T19:00:00+03:00"))
    val morning = DeadlineChoice.TomorrowMorning(Instant.parse("2026-10-13T09:00:00+03:00"))
    val state = NewDeadlineUi("t1", "Eren", listOf(evening, morning), picked = morning)
    DurtTheme {
        // The sheet is a ModalBottomSheet window; screenshots show its content over the scrim.
        Box(Modifier.fillMaxSize().background(DurtTheme.colors.scrim), contentAlignment = Alignment.BottomCenter) {
            DurtBottomSheetContent(
                title = stringResource(R.string.preview_question_deadline),
                description = stringResource(R.string.issue_new_time_body, state.toName),
                onDismiss = {},
                primary = saveAction(state, NewDeadlineCallbacks()),
            ) { NewDeadlineBody(state, NewDeadlineCallbacks()) }
        }
    }
}
