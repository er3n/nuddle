package app.nuddle.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.nuddle.R
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.MY_AVATAR
import app.nuddle.ui.components.PillStatus
import app.nuddle.ui.components.RemoveButton
import app.nuddle.ui.components.TaskRow
import app.nuddle.ui.components.TaskUi
import app.nuddle.ui.icons.DurtIcons
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.theme.DurtTheme
import app.nuddle.ui.theme.avatarFor

// The rows of home that wait for an answer: 7e in Bana, 5d in Gönderdiklerim. Their messages and buttons are in the
// task detail since NUD-67.

/** 7e: a missed task to me or to self, "Kaçırıldı · Sibel · bugün 09:00'a kadardı". */
@Composable
internal fun MissedRow(
    task: MissedUi,
    myName: String,
    onOpen: () -> Unit,
) {
    val colors = DurtTheme.colors
    val deadline = task.deadline.phrase()
    val from = task.fromName
    TaskRow(
        task =
            TaskUi(
                title = task.title,
                subtitle =
                    if (from == null) {
                        stringResource(R.string.issue_self_deadline, deadline)
                    } else {
                        stringResource(R.string.issue_from_deadline, from, deadline)
                    },
                status = PillStatus.Missed,
                personName = from ?: myName,
                personColor = task.fromId?.let { colors.avatarFor(it) } ?: colors.avatarSelf,
                personId = task.fromId ?: MY_AVATAR,
            ),
        onClick = onOpen,
        modifier = Modifier.testTag(TestTags.HOME_MISSED),
    )
}

/** "Sibel iptal etti: "Kuru temizlemeyi al"" with the note and "Dürtmeler durdu", and a ✕ that removes it (NUD-67). */
@Composable
internal fun CancelledRow(
    task: CancelledUi,
    onRemove: () -> Unit,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusM)
    val stopped = stringResource(R.string.issue_pokes_stopped)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.surface, shape)
                .border(dimens.borderCard, colors.border, shape)
                .padding(start = dimens.space14, top = dimens.space10, bottom = dimens.space10, end = dimens.space4)
                .testTag(TestTags.ISSUE_CANCELLED),
        horizontalArrangement = Arrangement.spacedBy(dimens.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(dimens.avatarList).background(colors.neutralPill, CircleShape), contentAlignment = Alignment.Center) {
            Icon(DurtIcons.Close, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(dimens.icon))
        }
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            Text(
                stringResource(R.string.issue_cancelled_title, task.byName, task.title),
                style = DurtTheme.type.body,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    task.note?.let { stringResource(R.string.separator_dot, stringResource(R.string.issue_note, it), stopped) } ?: stopped,
                style = DurtTheme.type.caption,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        RemoveButton(onRemove, Modifier.testTag(TestTags.ISSUE_REMOVE))
    }
}

/** 5d: my task that was dropped ("Bırakıldı · Not: yarın hallederim") or missed ("Kaçırıldı · ..."). */
@Composable
internal fun SentIssueRow(
    task: SentIssueUi,
    onOpen: () -> Unit,
) {
    val colors = DurtTheme.colors
    val dropped = task.issue == SentIssue.Dropped
    val note = task.note
    TaskRow(
        task =
            TaskUi(
                title = task.title,
                subtitle =
                    if (dropped && note != null) {
                        stringResource(R.string.separator_dot, task.toName, stringResource(R.string.issue_note, note))
                    } else {
                        stringResource(R.string.separator_dot, task.toName, task.deadline.startPhrase())
                    },
                status = if (dropped) PillStatus.Dropped else PillStatus.Missed,
                personName = task.toName,
                personColor = colors.avatarFor(task.toId),
                personId = task.toId,
            ),
        onClick = onOpen,
        modifier = Modifier.testTag(TestTags.HOME_SENT_ISSUE),
    )
}

/** The 5d message: what happened, then the note or how many pokes rang. */
@Composable
fun SentIssueUi.message(): String {
    val first =
        when {
            issue == SentIssue.Dropped -> stringResource(R.string.issue_dropped, toName)
            photo -> stringResource(if (deadline.moment) R.string.issue_missed_photo_moment else R.string.issue_missed_photo)
            deadline.moment -> stringResource(R.string.issue_missed_done_moment, toName)
            else -> stringResource(R.string.issue_missed_done, toName)
        }
    val second =
        when {
            issue == SentIssue.Dropped -> note?.let { stringResource(R.string.issue_note, it) }
            pokes == 0 -> null
            toldMe -> pluralStringResource(R.plurals.issue_pokes_told_me, pokes, pokes)
            else -> pluralStringResource(R.plurals.issue_pokes, pokes, pokes)
        }
    return second?.let { stringResource(R.string.two_sentences, first, it) } ?: first
}

/** The 7e message of a missed task to me or to self. */
@Composable
fun MissedUi.message(): String {
    val from = fromName
    return when {
        from == null && deadline.moment -> stringResource(R.string.issue_missed_self_moment)
        from == null -> stringResource(R.string.issue_missed_self)
        deadline.moment -> stringResource(R.string.issue_missed_to_me_moment, from)
        else -> stringResource(R.string.issue_missed_to_me, from)
    }
}

@Composable
private fun IssuesPreviewOf(state: HomeUiState) {
    DurtTheme {
        HomeScreen(
            state = state,
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

/** 7e in Bana: Eren missed a task from Sibel, and Sibel cancelled another. */
@PreviewScreens
@Composable
private fun HomeScreenMissedPreview() {
    IssuesPreviewOf(
        HomeUiState(
            myName = "Eren",
            connected = true,
            issues =
                IssuesUi(
                    missed =
                        listOf(
                            MissedUi("t1", "Temizlikçi için para çek", DeadlineText.Today("09:00"), "sibel", "Sibel", "+905321234567"),
                        ),
                    cancelled = listOf(CancelledUi("t2", "Kuru temizlemeyi al", "Sibel", "Ben hallettim")),
                ),
        ),
    )
}

/** 5d in Gönderdiklerim: Sibel's tasks to Eren, one dropped with a note and one missed. */
@PreviewScreens
@Composable
private fun HomeScreenSentIssuesPreview() {
    IssuesPreviewOf(
        HomeUiState(
            myName = "Sibel",
            connected = true,
            tab = HomeTab.Sent,
            issues =
                IssuesUi(
                    sent =
                        listOf(
                            SentIssueUi(
                                "t1",
                                "Gelirken ekmek al",
                                "eren",
                                "Eren",
                                DeadlineText.Today("19:00"),
                                SentIssue.Dropped,
                                note = "Vaktim olmadı",
                            ),
                            SentIssueUi(
                                "t2",
                                "Temizlikçi için para çek",
                                "eren",
                                "Eren",
                                DeadlineText.Today("09:00"),
                                SentIssue.Missed,
                                photo = true,
                                pokes = 4,
                                toldMe = true,
                            ),
                        ),
                ),
        ),
    )
}
