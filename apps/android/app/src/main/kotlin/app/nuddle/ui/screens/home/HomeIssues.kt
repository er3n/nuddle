package app.nuddle.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.nuddle.R
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.CardAction
import app.nuddle.ui.components.PillStatus
import app.nuddle.ui.components.TaskActionCard
import app.nuddle.ui.components.TaskUi
import app.nuddle.ui.icons.DurtIcons
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.theme.DurtTheme
import app.nuddle.ui.theme.avatarFor
import app.nuddle.ui.uppercaseForUi

// The cards of home that wait for an answer: 7e for tasks to me, 5d for my tasks to others.

data class IssueCallbacks(
    val onDidItLater: (taskId: String) -> Unit = {},
    val onCall: (phone: String) -> Unit = {},
    val onNewDeadline: (taskId: String) -> Unit = {},
    val onSenderHandles: (taskId: String) -> Unit = {},
    val onCancel: (taskId: String) -> Unit = {},
)

/** 7e under "Sana yazılanlar": missed cards, cancelled rows, and the footnote while a missed card is there. */
@Composable
internal fun ColumnScope.ToMeIssues(
    issues: IssuesUi,
    callbacks: IssueCallbacks,
) {
    val colors = DurtTheme.colors
    Text(
        text = stringResource(R.string.home_to_me).uppercaseForUi(),
        style = DurtTheme.type.overline,
        color = colors.textTertiary,
        modifier = Modifier.semantics { heading() },
    )
    issues.missed.forEach { MissedCard(it, callbacks) }
    issues.cancelled.forEach { CancelledRow(it) }
    if (issues.missed.isNotEmpty()) {
        Text(
            stringResource(R.string.issues_footer),
            style = DurtTheme.type.caption,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = DurtTheme.dimens.space4),
        )
    }
}

@Composable
private fun MissedCard(
    task: MissedUi,
    callbacks: IssueCallbacks,
) {
    val colors = DurtTheme.colors
    val deadline = task.deadline.phrase()
    val from = task.fromName
    val phone = task.phone
    TaskActionCard(
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
                personName = from ?: "",
                personColor = task.fromId?.let { colors.avatarFor(it) } ?: colors.avatarSelf,
            ),
        message =
            when {
                from == null && task.deadline.moment -> stringResource(R.string.issue_missed_self_moment)
                from == null -> stringResource(R.string.issue_missed_self)
                task.deadline.moment -> stringResource(R.string.issue_missed_to_me_moment, from)
                else -> stringResource(R.string.issue_missed_to_me, from)
            },
        primary =
            CardAction(stringResource(R.string.issue_did_it_later), {
                callbacks.onDidItLater(task.taskId)
            }, testTag = TestTags.ISSUE_DID_IT_LATER),
        secondary =
            phone?.let {
                CardAction(stringResource(R.string.home_call), { callbacks.onCall(it) }, DurtIcons.Phone, TestTags.ISSUE_CALL)
            },
    )
}

/** "Sibel iptal etti: "Kuru temizlemeyi al"" with the note and "Dürtmeler durdu". */
@Composable
private fun CancelledRow(task: CancelledUi) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusL)
    val stopped = stringResource(R.string.issue_pokes_stopped)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.surface, shape)
                .border(dimens.borderCard, colors.border, shape)
                .padding(dimens.space14)
                .testTag(TestTags.ISSUE_CANCELLED),
        horizontalArrangement = Arrangement.spacedBy(dimens.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(dimens.avatarList).background(colors.neutralPill, CircleShape), contentAlignment = Alignment.Center) {
            Icon(DurtIcons.Close, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(dimens.icon))
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.issue_cancelled_title, task.byName, task.title), style = DurtTheme.type.body, color = colors.ink)
            Text(
                text =
                    task.note?.let { stringResource(R.string.separator_dot, stringResource(R.string.issue_note, it), stopped) } ?: stopped,
                style = DurtTheme.type.caption,
                color = colors.textSecondary,
            )
        }
    }
}

/** 5d: dropped (Yeni zaman ver, Ben hallederim) or missed (Süreyi uzat, İptal et), on top of "Senin yazdıkların". */
@Composable
internal fun SentIssueCard(
    task: SentIssueUi,
    callbacks: IssueCallbacks,
) {
    val colors = DurtTheme.colors
    val dropped = task.issue == SentIssue.Dropped
    TaskActionCard(
        task =
            TaskUi(
                title = task.title,
                subtitle = task.deadline.startPhrase(),
                status = if (dropped) PillStatus.Dropped else PillStatus.Missed,
                personName = task.toName,
                personColor = colors.avatarFor(task.toId),
            ),
        message = task.message(),
        primary =
            CardAction(
                stringResource(if (dropped) R.string.issue_new_time else R.string.issue_extend),
                { callbacks.onNewDeadline(task.taskId) },
                testTag = TestTags.ISSUE_NEW_TIME,
            ),
        secondary =
            if (dropped) {
                CardAction(
                    stringResource(R.string.issue_i_handle),
                    { callbacks.onSenderHandles(task.taskId) },
                    testTag = TestTags.ISSUE_I_HANDLE,
                )
            } else {
                CardAction(stringResource(R.string.cancel_confirm), { callbacks.onCancel(task.taskId) }, testTag = TestTags.ISSUE_CANCEL)
            },
    )
}

@Composable
private fun SentIssueUi.message(): String {
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

/** 7e: Eren missed a task from Sibel, and Sibel cancelled another. */
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

/** 5d: Sibel's tasks to Eren, one dropped with a note and one missed. */
@PreviewScreens
@Composable
private fun HomeScreenSentIssuesPreview() {
    IssuesPreviewOf(
        HomeUiState(
            myName = "Sibel",
            connected = true,
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
