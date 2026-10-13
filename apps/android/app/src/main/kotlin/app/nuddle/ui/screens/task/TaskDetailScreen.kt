package app.nuddle.ui.screens.task

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import app.nuddle.ui.components.GroupDivider
import app.nuddle.ui.components.LineRow
import app.nuddle.ui.components.PillStatus
import app.nuddle.ui.components.SettingsGroup
import app.nuddle.ui.components.SheetAction
import app.nuddle.ui.components.StatusPill
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.screens.ScreenLayout
import app.nuddle.ui.screens.home.DeadlineChoice
import app.nuddle.ui.screens.home.DeadlineText
import app.nuddle.ui.screens.home.text
import app.nuddle.ui.screens.home.timeLabel
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
    val onEditDismiss: () -> Unit = {},
    val onEditSave: () -> Unit = {},
)

private val CancelReason.label: Int
    get() =
        when (this) {
            CancelReason.HandledIt -> R.string.cancel_reason_handled
            CancelReason.NotNeeded -> R.string.cancel_reason_not_needed
        }

/** "tam ekran", "sesli bildirim": the strongest poke that reaches them now, never a setting name (product.md). */
private val Reachability.level: Int?
    get() =
        when (this) {
            Reachability.FULL_SCREEN -> R.string.reach_full_screen
            Reachability.SOUND -> R.string.reach_sound
            Reachability.SILENT -> R.string.reach_silent
            Reachability.NONE -> null
        }

/**
 * 7c: what I wrote, the status and how far pokes reach the recipient, the details, and "Görevi iptal et", which opens
 * 7d. Editing a row comes in its own step.
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
            DurtButton(
                text = stringResource(R.string.task_cancel),
                onClick = callbacks.onCancel,
                kind = DurtButtonKind.DangerOutline,
                modifier = Modifier.testTag(TestTags.TASK_CANCEL),
            )
        },
    ) {
        BackBar(title = stringResource(R.string.task_detail_title), onBack = callbacks.onBack)
        state.rawText?.let {
            Text(stringResource(R.string.preview_you_wrote, it), style = DurtTheme.type.caption, color = colors.textSecondary)
        }
        Text(state.title, style = DurtTheme.type.h1, color = colors.ink, modifier = Modifier.semantics { heading() })
        StatusCard(state)
        SettingsGroup(title = stringResource(R.string.preview_detail)) {
            LineRow(
                label = stringResource(R.string.preview_to),
                value = if (state.self) stringResource(R.string.home_to_self) else state.toName,
            )
            GroupDivider()
            LineRow(
                label = state.deadline.timeLabel(),
                value = state.deadline.text(),
                onClick = { callbacks.onEdit(EditRow.Deadline) },
                modifier = Modifier.testTag(TestTags.TASK_EDIT_DEADLINE),
            )
            state.detail?.let {
                GroupDivider()
                LineRow(label = stringResource(R.string.preview_detail), value = it)
            }
            GroupDivider()
            LineRow(
                label = stringResource(R.string.task_finish_label),
                value = stringResource(if (state.photo) R.string.task_finish_photo else R.string.task_finish_done),
                onClick = { callbacks.onEdit(EditRow.Finish) },
                modifier = Modifier.testTag(TestTags.TASK_EDIT_FINISH),
            )
        }
        Text(
            if (state.self) stringResource(R.string.task_edit_hint_self) else stringResource(R.string.task_edit_hint, state.toName),
            style = DurtTheme.type.caption,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = dimens.space8),
        )
    }
    if (state.cancelling) CancelSheet(state, callbacks)
    if (state.editing != null) EditSheet(state, callbacks)
}

/** Kurulu and "Şu an Eren için en güçlü dürtme: **tam ekran**. Bitince sana yazarım." (open-questions#34). */
@Composable
private fun StatusCard(state: TaskDetailUiState) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusXl)
    val level = state.reachability.level?.let { stringResource(it) }
    val sentence =
        if (state.self) {
            buildAnnotatedString { append(stringResource(R.string.task_reach_self)) }
        } else if (level == null) {
            buildAnnotatedString { append(stringResource(R.string.task_reach_none, state.toName)) }
        } else {
            val full = stringResource(R.string.task_reach, state.toName, level)
            val start = full.indexOf(level)
            buildAnnotatedString {
                append(full)
                if (start >= 0) addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, start + level.length)
            }
        }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.surface, shape)
                .border(dimens.borderCard, colors.border, shape)
                .padding(dimens.space16),
        verticalArrangement = Arrangement.spacedBy(dimens.space12),
    ) {
        StatusPill(PillStatus.Scheduled)
        Text(sentence, style = DurtTheme.type.body, color = colors.ink)
    }
}

@Composable
private fun cancelAction(
    state: TaskDetailUiState,
    callbacks: TaskDetailCallbacks,
): SheetAction {
    val note = state.reason?.let { stringResource(it.label) }
    return SheetAction(stringResource(R.string.cancel_confirm), { callbacks.onCancelConfirm(note) }, destructive = true)
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
            if (state.self) stringResource(R.string.cancel_body_self) else stringResource(R.string.cancel_body, state.toName, state.myName),
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

private val previewState =
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
