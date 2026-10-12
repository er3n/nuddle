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
import app.nuddle.ui.screens.home.DeadlineChoice
import app.nuddle.ui.screens.home.DeadlineText
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
        description = stringResource(R.string.task_edit_note, state.toName),
        onDismiss = callbacks.onEditDismiss,
        primary = saveAction(state, callbacks),
    ) { EditBody(state, callbacks) }
}

@OptIn(ExperimentalLayoutApi::class) // Reason: FlowRow is the layout for wrapping chips; no stable alternative.
@Composable
private fun ColumnScope.EditBody(
    state: TaskDetailUiState,
    callbacks: TaskDetailCallbacks,
) {
    val dimens = DurtTheme.dimens
    var pickingTime by remember { mutableStateOf(false) }
    when (state.editing) {
        EditRow.Deadline -> {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(dimens.space8),
                verticalArrangement = Arrangement.spacedBy(dimens.space8),
            ) {
                state.deadlineChoices.forEach { choice ->
                    val (text, tag) =
                        when (choice) {
                            is DeadlineChoice.TonightEvening -> {
                                stringResource(R.string.preview_tonight) to TestTags.PREVIEW_EVENING
                            }

                            is DeadlineChoice.TomorrowMorning -> {
                                stringResource(R.string.preview_tomorrow_morning) to
                                    TestTags.PREVIEW_MORNING
                            }

                            is DeadlineChoice.Picked -> {
                                choice.time to TestTags.PREVIEW_PICK_TIME
                            }
                        }
                    ChoiceChip(
                        text,
                        selected = state.pickedDeadline == choice,
                        onClick = { callbacks.onDeadlineChoice(choice) },
                        modifier = Modifier.testTag(tag),
                    )
                }
                val picked = state.pickedDeadline as? DeadlineChoice.Picked
                ChoiceChip(
                    text = picked?.time ?: stringResource(R.string.preview_pick_time),
                    selected = picked != null,
                    onClick = { pickingTime = true },
                    modifier = Modifier.testTag(TestTags.PREVIEW_PICK_TIME),
                )
            }
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

        null -> {}
    }
    if (state.saveFailed) InlineAlert(stringResource(R.string.task_edit_failed_title), stringResource(R.string.invite_failed_body))
    if (pickingTime) {
        TimePickerDialog(
            onPick = {
                pickingTime = false
                callbacks.onTimePicked(it)
            },
            onDismiss = { pickingTime = false },
        )
    }
}

@PreviewScreens
@Composable
private fun EditDeadlineSheetPreview() {
    val morning = DeadlineChoice.TomorrowMorning(Instant.parse("2026-10-12T09:00:00+03:00"))
    val state =
        TaskDetailUiState(
            myName = "Selin",
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
