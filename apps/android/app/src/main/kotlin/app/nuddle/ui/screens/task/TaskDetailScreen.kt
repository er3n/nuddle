package app.nuddle.ui.screens.task

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import app.nuddle.R
import app.nuddle.api.model.Reachability
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.BackBar
import app.nuddle.ui.components.ChoiceChip
import app.nuddle.ui.components.DurtBottomSheet
import app.nuddle.ui.components.DurtBottomSheetContent
import app.nuddle.ui.components.DurtButton
import app.nuddle.ui.components.DurtButtonKind
import app.nuddle.ui.components.DurtButtonSize
import app.nuddle.ui.components.ExamplePhotoRow
import app.nuddle.ui.components.GroupDivider
import app.nuddle.ui.components.LineRow
import app.nuddle.ui.components.PillStatus
import app.nuddle.ui.components.SettingsGroup
import app.nuddle.ui.components.SheetAction
import app.nuddle.ui.components.StatusPill
import app.nuddle.ui.icons.DurtIcons
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.screens.ScreenLayout
import app.nuddle.ui.screens.home.DeadlineChoice
import app.nuddle.ui.screens.home.DeadlineText
import app.nuddle.ui.screens.home.FromLine
import app.nuddle.ui.screens.home.FromUi
import app.nuddle.ui.screens.home.MissedUi
import app.nuddle.ui.screens.home.NewDeadlineCallbacks
import app.nuddle.ui.screens.home.NewDeadlineSheet
import app.nuddle.ui.screens.home.RecipientChoice
import app.nuddle.ui.screens.home.SentIssue
import app.nuddle.ui.screens.home.SentIssueUi
import app.nuddle.ui.screens.home.WrittenText
import app.nuddle.ui.screens.home.message
import app.nuddle.ui.screens.home.text
import app.nuddle.ui.screens.home.timeLabel
import app.nuddle.ui.screens.poke.DropCallbacks
import app.nuddle.ui.screens.poke.DropSheet
import app.nuddle.ui.theme.DurtTheme
import kotlinx.datetime.LocalTime

@Immutable
data class TaskDetailCallbacks(
    val onBack: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onReason: (CancelReason) -> Unit = {},
    val onCancelDismiss: () -> Unit = {},
    /** The chosen reason's text as the note, or null. */
    val onCancelConfirm: (note: String?) -> Unit = {},
    val onEdit: (EditRow) -> Unit = {},
    val onDeadlineChoice: (DeadlineChoice) -> Unit = {},
    val onTimePicked: (LocalTime) -> Unit = {},
    val onPhotoChoice: (Boolean) -> Unit = {},
    /** Kime's sheet (NUD-114). */
    val onRecipientChoice: (RecipientChoice) -> Unit = {},
    val onEditDismiss: () -> Unit = {},
    val onEditSave: () -> Unit = {},
    /** A button at the bottom (docs/ux/screens.md, Task detail). */
    val onAction: (DetailAction) -> Unit = {},
    /** The sender's number. */
    val onCall: (String) -> Unit = {},
    val onMessage: (String) -> Unit = {},
    val drop: DropCallbacks = DropCallbacks(),
    val newDeadline: NewDeadlineCallbacks = NewDeadlineCallbacks(),
    /** The start line tapped, and its sheet (NUD-78). */
    val onStart: () -> Unit = {},
    val start: NewDeadlineCallbacks = NewDeadlineCallbacks(),
)

private val CancelReason.label: Int
    get() =
        when (this) {
            CancelReason.HandledIt -> R.string.cancel_reason_handled
            CancelReason.NotNeeded -> R.string.cancel_reason_not_needed
        }

/** "tam ekran", "sesli bildirim": the strongest poke that reaches them now, never a setting name (product.md). */
internal val Reachability.level: Int?
    get() =
        when (this) {
            Reachability.FULL_SCREEN -> R.string.reach_full_screen
            Reachability.SOUND -> R.string.reach_sound
            Reachability.SILENT -> R.string.reach_silent
            Reachability.NONE -> null
        }

private val DetailAction.label: Int
    get() =
        when (this) {
            DetailAction.DoneEarly -> R.string.home_done_early
            DetailAction.TakePhoto -> R.string.home_done_photo
            DetailAction.Ok -> R.string.poke_ok
            DetailAction.NewPhoto -> R.string.rerequest_new_photo
            DetailAction.CantToday -> R.string.poke_cant_today
            DetailAction.DidItLater -> R.string.issue_did_it_later
            DetailAction.RemoveMissed -> R.string.task_remove_missed
            DetailAction.CancelTask -> R.string.task_cancel
            DetailAction.NewTime -> R.string.issue_new_time
            DetailAction.SenderHandles -> R.string.issue_i_handle
            DetailAction.Extend -> R.string.issue_extend
            DetailAction.CancelNow -> R.string.cancel_confirm
        }

/** The first action is the main one; finishing is green, cancelling red, the rest quiet. */
private fun DetailAction.kind(first: Boolean): DurtButtonKind =
    when {
        this == DetailAction.CancelTask -> DurtButtonKind.DangerOutline

        this in
            setOf(
                DetailAction.DoneEarly,
                DetailAction.TakePhoto,
                DetailAction.Ok,
                DetailAction.NewPhoto,
                DetailAction.DidItLater,
            )
        -> DurtButtonKind.Success

        first -> DurtButtonKind.Primary

        else -> DurtButtonKind.Secondary
    }

/**
 * Any open task from either side (docs/ux/screens.md, Task detail): who wrote it or what I wrote, the whole title, the
 * state and its message, the rows, Ara and Mesaj for a task to me, and the actions for the state pinned to the bottom.
 * For my task to someone else this is 7c, whose rows can be changed and whose "Görevi iptal et" opens 7d.
 */
@Composable
fun TaskDetailScreen(
    state: TaskDetailUiState,
    callbacks: TaskDetailCallbacks,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    ScreenLayout(
        background = colors.background,
        modifier = modifier.testTag(TestTags.TASK_DETAIL),
        bottom = {
            state.actions.forEachIndexed { index, action ->
                DurtButton(
                    text = stringResource(action.label),
                    // Görevi iptal et opens 7d, as it always did; the rest go to the route.
                    onClick = { if (action == DetailAction.CancelTask) callbacks.onCancel() else callbacks.onAction(action) },
                    kind = action.kind(index == 0),
                    size = if (index == 0) DurtButtonSize.Large else DurtButtonSize.Medium,
                    modifier =
                        Modifier.fillMaxWidth().testTag(
                            if (action == DetailAction.CancelTask) TestTags.TASK_CANCEL else TestTags.taskAction(action.name),
                        ),
                )
            }
        },
    ) {
        BackBar(title = stringResource(R.string.task_detail_title), onBack = callbacks.onBack)
        val from = state.from
        if (from != null) {
            FromLine(from)
        } else {
            state.rawText?.takeIf { state.side == DetailSide.Mine || state.self }?.let {
                Text(stringResource(R.string.preview_you_wrote, it), style = DurtTheme.type.caption, color = colors.textSecondary)
            }
        }
        Text(state.title, style = DurtTheme.type.h1, color = colors.ink, modifier = Modifier.semantics { heading() })
        state.examplePhoto?.let { ExamplePhotoRow(it, from = from?.name) }
        StatusCard(state, callbacks.onStart)
        DetailRows(state, callbacks)
        if (state.editable) {
            Text(
                if (state.self) stringResource(R.string.task_edit_hint_self) else stringResource(R.string.task_edit_hint, state.toName),
                style = DurtTheme.type.caption,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = dimens.space8),
            )
        }
        from?.phone?.let { ReachButtons(it, callbacks) }
    }
    if (state.cancelling) CancelSheet(state, callbacks)
    if (state.editing != null) EditSheet(state, callbacks)
    state.drop?.let { DropSheet(it, callbacks.drop) }
    TimeSheets(state, callbacks)
}

@Composable
private fun cancelAction(
    state: TaskDetailUiState,
    callbacks: TaskDetailCallbacks,
): SheetAction {
    val note = state.reason?.let { stringResource(it.label) }
    return SheetAction(stringResource(R.string.cancel_confirm), { callbacks.onCancelConfirm(note) }, destructive = true)
}

/** Yeni zaman ver (5d) for the sender, and the moved start (NUD-78) for the recipient: the same sheet, other words. */
@Composable
private fun TimeSheets(
    state: TaskDetailUiState,
    callbacks: TaskDetailCallbacks,
) {
    state.newDeadline?.let { NewDeadlineSheet(it, callbacks.newDeadline) }
    state.start?.let {
        NewDeadlineSheet(
            it,
            callbacks.start,
            title = stringResource(R.string.start_title),
            description = stringResource(R.string.start_body),
        )
    }
}

/** 7d: Danger primary, two optional reasons, Vazgeç. */
@Composable
private fun CancelSheet(
    state: TaskDetailUiState,
    callbacks: TaskDetailCallbacks,
) {
    DurtBottomSheet(
        title = stringResource(R.string.cancel_title),
        description =
            listOfNotNull(
                if (state.self) {
                    stringResource(
                        R.string.cancel_body_self,
                    )
                } else {
                    stringResource(R.string.cancel_body, state.toName, state.myName)
                },
                // A repeating task: the cancel ends the series (ADR 0021).
                if (state.repeat != null) stringResource(R.string.cancel_body_repeat) else null,
            ).joinToString(" "),
        onDismiss = callbacks.onCancelDismiss,
        primary = cancelAction(state, callbacks),
    ) {
        // The reasons are a note for the recipient; nobody else reads one on a task to self.
        if (!state.self) CancelReasons(state, callbacks)
    }
}

@OptIn(ExperimentalLayoutApi::class) // Reason: FlowRow is the layout for wrapping chips; no stable alternative.
@Composable
private fun ColumnScope.CancelReasons(
    state: TaskDetailUiState,
    callbacks: TaskDetailCallbacks,
) {
    val dimens = DurtTheme.dimens
    FlowRow(horizontalArrangement = Arrangement.spacedBy(dimens.space10), verticalArrangement = Arrangement.spacedBy(dimens.space10)) {
        CancelReason.entries.forEach { reason ->
            ChoiceChip(
                stringResource(reason.label),
                selected = reason == state.reason,
                onClick = { callbacks.onReason(reason) },
                modifier = Modifier.testTag(TestTags.cancelReason(reason.name)),
            )
        }
    }
    Text(stringResource(R.string.cancel_reason_hint), style = DurtTheme.type.caption, color = DurtTheme.colors.textSecondary)
}

internal val previewState =
    TaskDetailUiState(
        myName = "Sibel",
        title = "Temizlikçi için para çek",
        rawText = "eren yarın 9dan önce temizlikçi için 1000 tl çeksin",
        toName = "Eren",
        reachability = Reachability.FULL_SCREEN,
        deadline = DeadlineText.Tomorrow("09:00"),
        detail = "1.000 TL",
        photo = true,
    )

@PreviewScreens
@Composable
private fun TaskDetailScreenPreview() {
    DurtTheme { TaskDetailScreen(previewState, TaskDetailCallbacks()) }
}

/** A task to self at a moment, opened from the next-up card (NUD-47, open-questions#43). */
@PreviewScreens
@Composable
private fun TaskDetailScreenSelfPreview() {
    DurtTheme {
        TaskDetailScreen(
            previewState.copy(
                title = "Su iç",
                rawText = "2 dakika sonra su iç",
                toName = "",
                self = true,
                side = DetailSide.ToSelf,
                deadline = DeadlineText.Today("19:48", moment = true),
                detail = null,
                photo = false,
            ),
            TaskDetailCallbacks(),
        )
    }
}

@PreviewScreens
@Composable
private fun TaskDetailScreenUnreachablePreview() {
    DurtTheme { TaskDetailScreen(previewState.copy(reachability = Reachability.NONE, detail = null, photo = false), TaskDetailCallbacks()) }
}

@PreviewScreens
@Composable
private fun CancelSheetPreview() {
    val state = previewState.copy(cancelling = true, reason = CancelReason.HandledIt)
    DurtTheme {
        // The sheet itself is a ModalBottomSheet window; screenshots show its content over the scrim.
        Box(Modifier.fillMaxSize().background(DurtTheme.colors.scrim), contentAlignment = Alignment.BottomCenter) {
            DurtBottomSheetContent(
                title = stringResource(R.string.cancel_title),
                description = stringResource(R.string.cancel_body, state.toName, state.myName),
                onDismiss = {},
                primary = cancelAction(state, TaskDetailCallbacks()),
            ) { CancelReasons(state, TaskDetailCallbacks()) }
        }
    }
}
