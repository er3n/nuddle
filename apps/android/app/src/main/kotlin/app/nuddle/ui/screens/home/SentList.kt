package app.nuddle.ui.screens.home

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.nuddle.R
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.ComposeState
import app.nuddle.ui.components.PillStatus
import app.nuddle.ui.components.TaskRow
import app.nuddle.ui.components.TaskUi
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.theme.DurtTheme
import app.nuddle.ui.theme.avatarFor
import app.nuddle.ui.uppercaseForUi

/** 4i: "Senin yazdıkların" and a TaskRow per task; [fresh] is the one whose undo toast is on screen ("Yeni"). */
@Composable
internal fun ColumnScope.SentSection(
    sent: List<SentUi>,
    fresh: String?,
    onOpen: (taskId: String, awaitingApproval: Boolean) -> Unit,
    issues: List<SentIssueUi> = emptyList(),
    issueActions: IssueCallbacks = IssueCallbacks(),
    queued: List<QueuedUi> = emptyList(),
    myName: String = "",
) {
    val colors = DurtTheme.colors
    Text(
        text = stringResource(R.string.home_sent).uppercaseForUi(),
        style = DurtTheme.type.overline,
        color = colors.textTertiary,
        modifier = Modifier.semantics { heading() },
    )
    // Dropped and missed wait for my answer, so they come first (5d).
    issues.forEach { SentIssueCard(it, issueActions) }
    // Written offline: not on the server yet, so there is nothing to open (6a).
    queued.forEach { write ->
        TaskRow(
            task =
                TaskUi(
                    title = write.text,
                    subtitle = stringResource(R.string.queued_waiting),
                    status = PillStatus.Queued,
                    personName = myName,
                    personColor = colors.avatarFor(myName),
                ),
            onClick = null,
            modifier = Modifier.testTag(TestTags.HOME_QUEUED),
        )
    }
    sent.forEach { task ->
        val isFresh = task.taskId == fresh
        val parts =
            if (task.awaitingApproval) {
                listOfNotNull(task.detail, stringResource(R.string.sent_photo_arrived))
            } else {
                listOfNotNull(task.detail, task.deadline.inlineText(), if (task.photo) stringResource(R.string.sent_photo) else null)
            }
        TaskRow(
            task =
                TaskUi(
                    title = task.title,
                    subtitle = parts.reduce { line, part -> stringResource(R.string.separator_dot, line, part) },
                    status =
                        when {
                            isFresh -> PillStatus.New
                            task.awaitingApproval -> PillStatus.AwaitingMyApproval
                            task.reRequested -> PillStatus.Rejected
                            else -> PillStatus.Scheduled
                        },
                    personName = task.toName,
                    personColor = colors.avatarFor(task.toId),
                ),
            onClick = { onOpen(task.taskId, task.awaitingApproval) },
            highlighted = isFresh,
            modifier = Modifier.testTag(TestTags.HOME_SENT),
        )
    }
}

/** ErrOffline.png */
@PreviewScreens
@Composable
private fun HomeScreenOfflinePreview() {
    DurtTheme {
        HomeScreen(
            state =
                HomeUiState(
                    myName = "Eren",
                    connected = true,
                    offline = true,
                    queued = listOf(QueuedUi("key-1", "selin akşama kadar faturayı ödesin")),
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

/** ErrSend.png */
@PreviewScreens
@Composable
private fun HomeScreenSendFailedPreview() {
    DurtTheme {
        HomeScreen(
            state =
                HomeUiState(
                    myName = "Eren",
                    connected = true,
                    compose = ComposeState.Failed("selin cuma akşama kadar kuru temizlemeyi alsın"),
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

@PreviewScreens
@Composable
private fun HomeScreenQuickSentPreview() {
    DurtTheme {
        HomeScreen(
            state =
                HomeUiState(
                    myName = "Selin",
                    connected = true,
                    sent =
                        listOf(
                            SentUi("task-1", "Temizlikçi için para çek", "1.000 TL", DeadlineText.Tomorrow("09:00"), true, "eren", "Eren"),
                        ),
                    undo =
                        UndoUi(
                            taskId = "task-1",
                            rawText = "eren yarın 9dan önce temizlikçi için 1000 çeksin",
                            title = "Temizlikçi için para çek",
                            detail = "1.000 TL",
                            deadline = DeadlineText.Tomorrow("09:00"),
                            photo = true,
                            to = "Eren",
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
