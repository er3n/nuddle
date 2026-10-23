package app.nuddle.ui.screens.task

// The parts of the task detail under the title: the status and its message, the rows, Ara and Mesaj.

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import app.nuddle.R
import app.nuddle.api.model.Reachability
import app.nuddle.data.Delivery
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.BackBar
import app.nuddle.ui.components.ChoiceChip
import app.nuddle.ui.components.DurtBottomSheet
import app.nuddle.ui.components.DurtBottomSheetContent
import app.nuddle.ui.components.DurtButton
import app.nuddle.ui.components.DurtButtonKind
import app.nuddle.ui.components.DurtButtonSize
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
import app.nuddle.ui.screens.home.SentIssue
import app.nuddle.ui.screens.home.SentIssueUi
import app.nuddle.ui.screens.home.WrittenText
import app.nuddle.ui.screens.home.message
import app.nuddle.ui.screens.home.text
import app.nuddle.ui.screens.home.timeLabel
import app.nuddle.ui.screens.poke.DropCallbacks
import app.nuddle.ui.screens.poke.DropSheet
import app.nuddle.ui.theme.DurtColors
import app.nuddle.ui.theme.DurtTheme
import kotlinx.datetime.LocalTime

private val DetailStatus.pill: PillStatus
    get() =
        when (this) {
            DetailStatus.Scheduled -> PillStatus.Scheduled
            DetailStatus.ReRequested -> PillStatus.Rejected
            DetailStatus.AwaitingApproval -> PillStatus.AwaitingApproval
            DetailStatus.Missed -> PillStatus.Missed
            DetailStatus.Dropped -> PillStatus.Dropped
        }

/** Kime or Kimden, the time, the detail and how it ends; the time and how it ends open a sheet while editable. */
@Composable
internal fun DetailRows(
    state: TaskDetailUiState,
    callbacks: TaskDetailCallbacks,
) {
    val from = state.from
    SettingsGroup(title = stringResource(R.string.preview_detail)) {
        LineRow(
            label = stringResource(if (from != null) R.string.task_from else R.string.preview_to),
            value =
                when {
                    from != null -> from.name
                    state.self -> stringResource(R.string.home_to_self)
                    else -> state.toName
                },
            onClick = { callbacks.onEdit(EditRow.Recipient) }.takeIf { state.recipientEditable },
            modifier = Modifier.testTag(TestTags.TASK_EDIT_RECIPIENT),
        )
        GroupDivider()
        LineRow(
            label = state.deadline.timeLabel(),
            value = state.deadline.text(),
            onClick = { callbacks.onEdit(EditRow.Deadline) }.takeIf { state.editable },
            modifier = Modifier.testTag(TestTags.TASK_EDIT_DEADLINE),
        )
        state.repeat?.let {
            GroupDivider()
            LineRow(label = stringResource(R.string.task_repeat_label), value = it.text())
        }
        state.detail?.let {
            GroupDivider()
            LineRow(label = stringResource(R.string.preview_detail), value = it)
        }
        GroupDivider()
        LineRow(
            label = stringResource(R.string.task_finish_label),
            value =
                when {
                    state.notice -> stringResource(R.string.home_finish_notice)
                    from == null -> stringResource(if (state.photo) R.string.task_finish_photo else R.string.task_finish_done)
                    state.photo -> stringResource(R.string.home_finish_photo_from, from.name)
                    else -> stringResource(R.string.home_finish_done)
                },
            onClick = { callbacks.onEdit(EditRow.Finish) }.takeIf { state.editable && !state.notice },
            modifier = Modifier.testTag(TestTags.TASK_EDIT_FINISH),
        )
    }
}

/** Ara and Mesaj (docs/product.md: no in-app chat; docs/ux/platform.md: ACTION_DIAL and smsto:). */
@Composable
internal fun ReachButtons(
    phone: String,
    callbacks: TaskDetailCallbacks,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(DurtTheme.dimens.space10)) {
        DurtButton(
            text = stringResource(R.string.home_call),
            onClick = { callbacks.onCall(phone) },
            kind = DurtButtonKind.Secondary,
            size = DurtButtonSize.Small,
            leadingIcon = DurtIcons.Phone,
            modifier = Modifier.weight(1f).testTag(TestTags.TASK_CALL),
        )
        DurtButton(
            text = stringResource(R.string.home_message),
            onClick = { callbacks.onMessage(phone) },
            kind = DurtButtonKind.Secondary,
            size = DurtButtonSize.Small,
            leadingIcon = DurtIcons.Message,
            modifier = Modifier.weight(1f).testTag(TestTags.TASK_MESSAGE),
        )
    }
}

/** The status and what it means now: how far pokes reach, the sender's note, what happened (open-questions#34, #36). */
@Composable
internal fun StatusCard(
    state: TaskDetailUiState,
    /** The start line tapped (NUD-78). */
    onStart: () -> Unit = {},
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusXl)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.surface, shape)
                .border(dimens.borderCard, colors.border, shape)
                .padding(dimens.space16),
        verticalArrangement = Arrangement.spacedBy(dimens.space12),
    ) {
        StatusPill(state.status.pill)
        state.reRequestNote?.takeIf { state.status == DetailStatus.ReRequested && state.side == DetailSide.ToMe }?.let {
            Text(
                it,
                style = DurtTheme.type.body,
                color = colors.ink,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(colors.background, RoundedCornerShape(dimens.radiusM))
                        .padding(horizontal = dimens.space14, vertical = dimens.space10),
            )
        }
        Text(statusSentence(state), style = DurtTheme.type.body, color = colors.ink)
        state.pokeStartsAt?.let {
            // The recipient can move the start (NUD-78): the line is a row that opens the sheet.
            val change = stringResource(R.string.cd_task_start)
            Text(
                stringResource(R.string.task_poke_starts, it),
                style = DurtTheme.type.bodySmall,
                color = colors.link,
                modifier =
                    Modifier
                        .clickable(onClickLabel = change, role = Role.Button, onClick = onStart)
                        .testTag(TestTags.TASK_START),
            )
        }
        StatusNotes(state)
    }
}

/** The sender's tick in words (NUD-68); only sent has no line. */
internal fun Delivery.sentence(): Int =
    when (this) {
        Delivery.Sent, Delivery.Reached -> R.string.task_delivery_reached
        Delivery.Seen -> R.string.task_delivery_seen
    }

@Composable
private fun statusSentence(state: TaskDetailUiState): AnnotatedString {
    val fromName = state.from?.name.orEmpty()
    val plain =
        when {
            state.status == DetailStatus.Missed -> state.missed?.message() ?: state.sentIssue?.message()
            state.status == DetailStatus.Dropped -> state.sentIssue?.message()
            state.side == DetailSide.ToSelf -> stringResource(R.string.task_reach_self)
            state.side == DetailSide.Mine -> null
            state.status == DetailStatus.ReRequested -> stringResource(R.string.rerequest_resumes)
            state.status == DetailStatus.AwaitingApproval -> photoSentence(state, fromName)
            else -> stringResource(R.string.task_arrived_calm)
        }
    return plain?.let { AnnotatedString(it) } ?: reachSentence(state)
}

@Composable
private fun photoSentence(
    state: TaskDetailUiState,
    fromName: String,
): String {
    val waiting = stringResource(R.string.photo_waiting, fromName)
    val sent = state.photoSentAt?.let { stringResource(R.string.photo_waiting_sent, it) } ?: stringResource(R.string.photo_waiting_sending)
    return listOf(waiting, sent, stringResource(R.string.photo_auto_approve, fromName)).joinToString(" ")
}

/**
 * "Şu an Eren için en güçlü dürtme: **tam ekran**. Bitince sana yazarım." (open-questions#34). A one-time message rings once
 * with sound, so it says that instead (NUD-140).
 */
@Composable
private fun reachSentence(state: TaskDetailUiState): AnnotatedString {
    val level = state.reachability.level?.let { stringResource(it) }
    if (level == null) return AnnotatedString(stringResource(R.string.task_reach_none, state.toName))
    if (state.notice) return AnnotatedString(stringResource(R.string.task_reach_notice))
    val full = stringResource(R.string.task_reach, state.toName, level)
    val start = full.indexOf(level)
    return buildAnnotatedString {
        append(full)
        if (start >= 0) addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, start + level.length)
    }
}

/** Eren opens Sibel's task from Bana: Şimdiden bitti or Bitti, çek, Bugün yapamayacağım, Ara and Mesaj (NUD-67). */
@PreviewScreens
@Composable
private fun TaskDetailScreenToMePreview() {
    DurtTheme {
        TaskDetailScreen(
            previewState.copy(
                myName = "Eren",
                title = "Kirli sepetlerini birleştirip makinelerdeki çamaşırları taşı",
                toName = "",
                side = DetailSide.ToMe,
                from = FromUi("Sibel", WrittenText.Today("13:40"), "+905320000001"),
                deadline = DeadlineText.Today("16:00"),
                detail = null,
                photo = false,
                snoozedUntil = "15:20",
            ),
            TaskDetailCallbacks(),
        )
    }
}

/** Sibel opens the message she sent Eren: one ring with sound, never the strongest poke (NUD-140). */
@PreviewScreens
@Composable
private fun TaskDetailScreenNoticePreview() {
    DurtTheme {
        TaskDetailScreen(
            previewState.copy(
                title = "Yemek hazır",
                rawText = "erene yemek hazır de",
                deadline = DeadlineText.Today("20:00"),
                detail = null,
                photo = false,
                notice = true,
            ),
            TaskDetailCallbacks(),
        )
    }
}

/** Sibel opens the task Eren dropped from Gönderdiklerim: the 5d message, Yeni zaman ver and Ben hallederim. */
@PreviewScreens
@Composable
private fun TaskDetailScreenDroppedPreview() {
    DurtTheme {
        TaskDetailScreen(
            previewState.copy(
                status = DetailStatus.Dropped,
                sentIssue =
                    SentIssueUi(
                        "t1",
                        "Temizlikçi için para çek",
                        "eren",
                        "Eren",
                        DeadlineText.Today("19:00"),
                        SentIssue.Dropped,
                        note = "Yarın hallederim",
                    ),
            ),
            TaskDetailCallbacks(),
        )
    }
}

/** Eren opens the task he missed: the 7e message, Sonradan yaptım and Listeden kaldır. */
@PreviewScreens
@Composable
private fun TaskDetailScreenMissedPreview() {
    DurtTheme {
        TaskDetailScreen(
            previewState.copy(
                myName = "Eren",
                toName = "",
                side = DetailSide.ToMe,
                status = DetailStatus.Missed,
                from = FromUi("Sibel", WrittenText.Yesterday("21:10"), "+905320000001"),
                deadline = DeadlineText.Today("09:00"),
                missed = MissedUi("t1", "Temizlikçi için para çek", DeadlineText.Today("09:00"), "sibel", "Sibel", "+905320000001"),
            ),
            TaskDetailCallbacks(),
        )
    }
}
