package app.nuddle.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import app.nuddle.R
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.DoneNote
import app.nuddle.ui.components.PillStatus
import app.nuddle.ui.components.StatusPill
import app.nuddle.ui.components.UndoToast
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.theme.DurtTheme

// Parts of home once there is a task (2f). Kept next to HomeScreen.kt, used only there.

private val NextUpStatus.pill: PillStatus
    get() =
        when (this) {
            NextUpStatus.Scheduled -> PillStatus.Scheduled
            NextUpStatus.AwaitingApproval -> PillStatus.AwaitingApproval
            NextUpStatus.ReRequested -> PillStatus.Rejected
        }

/**
 * 2f: title, detail and who it is for, the deadline and how it ends. 4c, a task from someone else: who wrote it and when
 * on top. 4e: the photo waits for the sender. 4g: the sender asked for another photo. No buttons since NUD-67: a tap
 * opens the task, whose detail has the actions.
 */
@Composable
internal fun NextUpCard(
    task: NextUpUi,
    onOpen: () -> Unit,
    photo: ImageBitmap? = null,
    /** The photo whose upload failed: the kept photo and Şimdi tekrar dene (6e). */
    onOpenUnsentPhoto: (taskId: String) -> Unit = {},
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusXl)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .clickable(onClick = onOpen)
                .background(colors.surface, shape)
                .border(dimens.borderCard, colors.border, shape)
                .padding(dimens.space16)
                .testTag(TestTags.HOME_NEXT_UP),
        verticalArrangement = Arrangement.spacedBy(dimens.space12),
    ) {
        Heading(task)
        when (task.status) {
            NextUpStatus.Scheduled -> ToDo(task)
            NextUpStatus.AwaitingApproval -> PhotoWaiting(task, photo, onOpenUnsentPhoto)
            NextUpStatus.ReRequested -> ReRequested(task)
        }
    }
}

/** Who it is from (or "Kendine"), the status, the title and the line under it. */
@Composable
private fun ColumnScope.Heading(task: NextUpUi) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val from = task.from
    if (from == null) {
        val toSelf = stringResource(R.string.home_to_self)
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.space12)) {
            Column(Modifier.weight(1f)) {
                Text(task.title, style = DurtTheme.type.brand, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    text = task.detail?.let { stringResource(R.string.separator_dot, it, toSelf) } ?: toSelf,
                    style = DurtTheme.type.bodySmall,
                    color = colors.textSecondary,
                )
            }
            StatusPill(task.status.pill)
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FromLine(from, Modifier.weight(1f))
            StatusPill(task.status.pill)
        }
        Column {
            Text(task.title, style = DurtTheme.type.brand, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            // Once the photo is involved the tiles go and the deadline joins the line (4e, 4g).
            val deadline = task.deadline.phrase()
            val line =
                when {
                    task.status == NextUpStatus.Scheduled -> task.detail
                    task.detail == null -> deadline
                    else -> stringResource(R.string.separator_dot, task.detail, deadline)
                }
            line?.let { Text(it, style = DurtTheme.type.bodySmall, color = colors.textSecondary) }
        }
    }
    // A snooze the person chose, not the plan: it says when the poke comes back (NUD-63).
    task.snoozedUntil?.let {
        Text(
            stringResource(R.string.next_up_snoozed, it),
            style = DurtTheme.type.bodySmallStrong,
            color = colors.warning,
            modifier = Modifier.testTag(TestTags.NEXT_UP_SNOOZED),
        )
    }
}

/** The deadline and how it ends. */
@Composable
private fun ToDo(task: NextUpUi) {
    val dimens = DurtTheme.dimens
    val from = task.from
    // Both tiles as tall as the taller one when a value wraps at large font scales.
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(dimens.space10)) {
        InfoTile(task.deadline.timeLabel(), task.deadline.text(), Modifier.weight(1f))
        InfoTile(
            label = stringResource(R.string.home_finish_label),
            value =
                when {
                    !task.photo -> stringResource(R.string.home_finish_done)
                    from == null -> stringResource(R.string.home_finish_photo)
                    else -> stringResource(R.string.home_finish_photo_from, from.name)
                },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun InfoTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .background(colors.background, RoundedCornerShape(dimens.radiusS))
                .padding(horizontal = dimens.space12, vertical = dimens.space10),
    ) {
        Text(label, style = DurtTheme.type.caption, color = colors.textTertiary)
        Text(value, style = DurtTheme.type.bodySmallStrong, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** The invite card once there is a task: one dashed row (2f). */
@Composable
internal fun CompactInviteCard(onInvite: () -> Unit) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .dashedBorder(colors.textMuted, dimens.borderStrong, dimens.radiusM)
                .clickable(onClick = onInvite)
                .padding(horizontal = dimens.space14, vertical = dimens.space12)
                .testTag(TestTags.HOME_INVITE),
        horizontalArrangement = Arrangement.spacedBy(dimens.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DashedPlus(dimens.avatarList)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.home_invite_title), style = DurtTheme.type.bodySmallStrong, color = colors.ink)
            Text(stringResource(R.string.home_invite_compact_body), style = DurtTheme.type.caption, color = colors.textSecondary)
        }
    }
}

private fun Modifier.dashedBorder(
    color: Color,
    width: Dp,
    radius: Dp,
): Modifier =
    drawBehind {
        val stroke = width.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius(radius.toPx()),
            style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH, DASH))),
        )
    }

/**
 * 2a: "Kuruldu · Para çek · yarın 09:00"; 4i: "Gönderildi · Eren · Para çek · yarın 09:00" (open-questions#34). With
 * Geri al and Düzenle (UndoToast, components.md 6).
 */
@Composable
internal fun TaskUndoToast(
    task: UndoUi,
    callbacks: UndoCallbacks,
    modifier: Modifier = Modifier,
) {
    val parts =
        listOfNotNull(task.to, task.title, task.deadline.inlineText(), if (task.photo) stringResource(R.string.undo_finish_photo) else null)
    UndoToast(
        title = stringResource(if (task.to == null) R.string.undo_self_title else R.string.undo_sent_title),
        subtitle = parts.reduce { line, part -> stringResource(R.string.separator_dot, line, part) },
        onUndo = callbacks.onUndo,
        onEdit = callbacks.onEdit,
        onTimeout = callbacks.onTimeout,
        modifier = modifier.testTag(TestTags.HOME_UNDO),
    )
}

/** 5c: "\"Para çek\" tamamlandı" and, when a poke had rung, which one caught the person. */
@Composable
internal fun TaskDoneNote(
    note: DoneNoteUi,
    modifier: Modifier = Modifier,
) {
    DoneNote(
        title = stringResource(R.string.done_note_title, note.title),
        learning = note.caughtAt?.let { stringResource(R.string.done_note_caught, it) },
        modifier = modifier.testTag(TestTags.HOME_DONE_NOTE),
    )
}

/** 4c after Ertele: the card says when the poke comes back (NUD-63). */
@PreviewScreens
@Composable
private fun HomeScreenSnoozedPreview() {
    DurtTheme {
        HomeScreen(
            state =
                HomeUiState(
                    myName = "Eren",
                    connected = true,
                    nextUp =
                        NextUpUi(
                            taskId = "task-1",
                            title = "Çay tazele",
                            detail = null,
                            deadline = DeadlineText.Today("22:41", moment = true),
                            photo = false,
                            from = FromUi("Sibel", WrittenText.Today("22:19"), null),
                            snoozedUntil = "22:37",
                        ),
                ),
            onBell = {},
            onProfile = {},
            onInvite = {},
            onComposeTextChange = {},
            onComposeSend = {},
            onVoiceStart = {},
            onVoiceEnd = {},
        )
    }
}
