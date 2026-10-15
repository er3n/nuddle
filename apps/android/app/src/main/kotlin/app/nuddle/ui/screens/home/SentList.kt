package app.nuddle.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.nuddle.R
import app.nuddle.data.Delivery
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.ComposeState
import app.nuddle.ui.components.PillStatus
import app.nuddle.ui.components.TaskRow
import app.nuddle.ui.components.TaskUi
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.theme.DurtTheme
import app.nuddle.ui.theme.avatarFor

/** 4i: one of my tasks to others; [fresh] is the one whose undo toast is on screen ("Yeni"). */
@Composable
internal fun SentRow(
    task: SentUi,
    fresh: String?,
    onOpen: () -> Unit,
) {
    val colors = DurtTheme.colors
    val isFresh = task.taskId == fresh
    val parts =
        if (task.awaitingApproval) {
            listOfNotNull(task.toName, stringResource(R.string.sent_photo_arrived))
        } else {
            listOfNotNull(
                task.toName,
                task.delivery.word(),
                task.deadline.counting(task.deadline.inlineText()),
                if (task.photo) stringResource(R.string.sent_photo) else null,
            )
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
        onClick = onOpen,
        highlighted = isFresh,
        modifier = Modifier.testTag(TestTags.HOME_SENT),
    )
}

/** "ulaştı" or "gördü" after the name; nothing while it is only sent (NUD-68). */
@Composable
internal fun Delivery.word(): String? =
    when (this) {
        Delivery.Sent -> null
        Delivery.Reached -> stringResource(R.string.sent_reached)
        Delivery.Seen -> stringResource(R.string.sent_seen)
    }

/** Written offline: not on the server yet, so there is nothing to open (6a). */
@Composable
internal fun QueuedRow(
    write: QueuedUi,
    myName: String,
) {
    TaskRow(
        task =
            TaskUi(
                title = write.text,
                subtitle = stringResource(R.string.queued_waiting),
                status = PillStatus.Queued,
                personName = myName,
                personColor = DurtTheme.colors.avatarFor(myName),
            ),
        onClick = null,
        modifier = Modifier.testTag(TestTags.HOME_QUEUED),
    )
}

/** NUD-68: the sender's tick after the name and the last hour counting down. */
@PreviewScreens
@Composable
private fun HomeScreenSentTickPreview() {
    DurtTheme {
        HomeScreen(
            state =
                HomeUiState(
                    myName = "Sibel",
                    connected = true,
                    tab = HomeTab.Sent,
                    sent =
                        listOf(
                            SentUi(
                                "task-1",
                                "Çayı demle",
                                null,
                                DeadlineText.Today("19:00", minutesLeft = 35),
                                false,
                                "eren",
                                "Eren",
                                delivery = Delivery.Seen,
                            ),
                            SentUi(
                                "task-2",
                                "Temizlikçi için para çek",
                                "1.000 TL",
                                DeadlineText.Tomorrow("09:00"),
                                true,
                                "eren",
                                "Eren",
                                delivery = Delivery.Reached,
                            ),
                            SentUi("task-3", "Faturayı öde", null, DeadlineText.Tomorrow("19:00"), true, "eren", "Eren"),
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
                    tab = HomeTab.Sent,
                    queued = listOf(QueuedUi("key-1", "sibel akşama kadar faturayı ödesin")),
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
                    compose = ComposeState.Failed("sibel cuma akşama kadar kuru temizlemeyi alsın"),
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
                    myName = "Sibel",
                    connected = true,
                    tab = HomeTab.Sent,
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
