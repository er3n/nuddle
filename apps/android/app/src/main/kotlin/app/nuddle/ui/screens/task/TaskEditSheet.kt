package app.nuddle.ui.screens.task

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.nuddle.R
import app.nuddle.api.model.Reachability
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.ChoiceChip
import app.nuddle.ui.components.DurtBottomSheet
import app.nuddle.ui.components.DurtBottomSheetContent
import app.nuddle.ui.components.InlineAlert
import app.nuddle.ui.components.SheetAction
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.screens.home.DeadlineChips
import app.nuddle.ui.screens.home.DeadlineChoice
import app.nuddle.ui.screens.home.DeadlineText
import app.nuddle.ui.screens.home.RecipientChoice
import app.nuddle.ui.screens.home.TimePickerDialog
import app.nuddle.ui.theme.DurtTheme
import kotlin.time.Instant

// The sheet behind a tapped row of 7c: a new deadline, or how the task ends. Used only by TaskDetailScreen.

@Composable
private fun EditRow.title(): String =
    stringResource(
        when (this) {
            EditRow.Deadline -> R.string.preview_question_deadline
            EditRow.Finish -> R.string.preview_finish_question
            EditRow.Recipient -> R.string.preview_question_recipient
        },
    )

@Composable
private fun saveAction(
    state: TaskDetailUiState,
    callbacks: TaskDetailCallbacks,
) = SheetAction(
    text = stringResource(R.string.action_save),
    onClick = callbacks.onEditSave,
    enabled = state.editing != EditRow.Deadline || state.pickedDeadline != null,
    loading = state.saving,
)

@Composable
internal fun EditSheet(
    state: TaskDetailUiState,
    callbacks: TaskDetailCallbacks,
) {
    val row = state.editing ?: return
    DurtBottomSheet(
        title = row.title(),
        description = row.note(state),
        onDismiss = callbacks.onEditDismiss,
        primary = saveAction(state, callbacks),
    ) { EditBody(state, callbacks) }
}

/** What the sheet says happens: a note to the recipient, or for Kime that the task moves (NUD-114). */
@Composable
private fun EditRow.note(state: TaskDetailUiState): String =
    when {
        this == EditRow.Recipient -> stringResource(R.string.task_reassign_note)
        else -> stringResource(R.string.task_edit_note, state.toName)
    }

@OptIn(ExperimentalLayoutApi::class) // Reason: FlowRow is the layout for wrapping chips; no stable alternative.
@Composable
private fun ColumnScope.EditBody(
    state: TaskDetailUiState,
    callbacks: TaskDetailCallbacks,
) {
    val dimens = DurtTheme.dimens
    when (state.editing) {
        EditRow.Deadline -> {
            DeadlineChips(state.deadlineChoices, state.pickedDeadline, callbacks.onDeadlineChoice, callbacks.onTimePicked)
        }

        EditRow.Finish -> {
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.space8)) {
                ChoiceChip(
                    stringResource(R.string.task_finish_done),
                    selected = !state.pickedPhoto,
                    onClick = { callbacks.onPhotoChoice(false) },
                    modifier = Modifier.weight(1f).testTag(TestTags.PREVIEW_FINISH_DONE),
                )
                ChoiceChip(
                    stringResource(R.string.task_finish_photo),
                    selected = state.pickedPhoto,
                    onClick = { callbacks.onPhotoChoice(true) },
                    modifier = Modifier.weight(1f).testTag(TestTags.PREVIEW_FINISH_PHOTO),
                )
            }
        }

        EditRow.Recipient -> {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(dimens.space8),
                verticalArrangement = Arrangement.spacedBy(dimens.space8),
            ) {
                state.recipientOptions.forEach { choice ->
                    val (text, tag) =
                        when (choice) {
                            RecipientChoice.Self -> stringResource(R.string.home_to_self) to TestTags.previewRecipient(null)
                            is RecipientChoice.Person -> choice.name to TestTags.previewRecipient(choice.personId)
                        }
                    ChoiceChip(
                        text,
                        selected = state.pickedRecipient == choice,
                        onClick = { callbacks.onRecipientChoice(choice) },
                        modifier = Modifier.testTag(tag),
                    )
                }
            }
        }

        null -> {}
    }
    if (state.saveFailed) InlineAlert(stringResource(R.string.task_edit_failed_title), stringResource(R.string.invite_failed_body))
}

@PreviewScreens
@Composable
private fun EditDeadlineSheetPreview() {
    val morning = DeadlineChoice.TomorrowMorning(Instant.parse("2026-10-12T09:00:00+03:00"))
    val state =
        TaskDetailUiState(
            myName = "Sibel",
            title = "Temizlikçi için para çek",
            rawText = null,
            toName = "Eren",
            reachability = Reachability.FULL_SCREEN,
            deadline = DeadlineText.Tomorrow("09:00"),
            detail = "1.000 TL",
            photo = true,
            editing = EditRow.Deadline,
            deadlineChoices = listOf(morning),
            pickedDeadline = morning,
        )
    DurtTheme {
        // The sheet itself is a ModalBottomSheet window; screenshots show its content over the scrim.
        Box(Modifier.fillMaxSize().background(DurtTheme.colors.scrim), contentAlignment = Alignment.BottomCenter) {
            DurtBottomSheetContent(
                title = EditRow.Deadline.title(),
                description = stringResource(R.string.task_edit_note, state.toName),
                onDismiss = {},
                primary = saveAction(state, TaskDetailCallbacks()),
            ) { EditBody(state, TaskDetailCallbacks()) }
        }
    }
}

/** Kime on my task to Eren (NUD-114): Kendine and each connection, Eren selected. */
@PreviewScreens
@Composable
private fun EditRecipientSheetPreview() {
    val eren = RecipientChoice.Person("eren", "Eren")
    val state =
        TaskDetailUiState(
            myName = "Sibel",
            title = "Temizlikçi için para çek",
            rawText = null,
            toName = "Eren",
            reachability = Reachability.FULL_SCREEN,
            deadline = DeadlineText.Tomorrow("09:00"),
            detail = "1.000 TL",
            photo = true,
            editing = EditRow.Recipient,
            recipient = eren,
            recipientOptions = listOf(RecipientChoice.Self, eren, RecipientChoice.Person("defne", "Defne")),
        )
    DurtTheme {
        Box(Modifier.fillMaxSize().background(DurtTheme.colors.scrim), contentAlignment = Alignment.BottomCenter) {
            DurtBottomSheetContent(
                title = EditRow.Recipient.title(),
                description = EditRow.Recipient.note(state),
                onDismiss = {},
                primary = saveAction(state, TaskDetailCallbacks()),
            ) { EditBody(state, TaskDetailCallbacks()) }
        }
    }
}
