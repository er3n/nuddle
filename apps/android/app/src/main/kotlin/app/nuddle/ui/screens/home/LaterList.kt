package app.nuddle.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.nuddle.R
import app.nuddle.api.model.FinishMode
import app.nuddle.api.model.TaskStatus
import app.nuddle.data.db.ConnectionEntity
import app.nuddle.data.db.TaskEntity
import app.nuddle.data.db.shownTime
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.MY_AVATAR
import app.nuddle.ui.components.PillStatus
import app.nuddle.ui.components.TaskRow
import app.nuddle.ui.components.TaskUi
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.theme.DurtTheme
import app.nuddle.ui.theme.avatarFor
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/** 5c: an open task to me after the next-up card, a row in Bana (NUD-61, NUD-67). */
@Immutable
data class LaterUi(
    val taskId: String,
    val title: String,
    val detail: String?,
    val deadline: DeadlineText,
    val photo: Boolean,
    /** Who wrote it; null for a task to self. */
    val fromId: String?,
    val fromName: String?,
    val status: PillStatus,
    /** A repeating task's rule (ADR 0021); null for a one-off. */
    val repeat: RepeatUi? = null,
)

private val LATER_STATUSES =
    mapOf(
        TaskStatus.SCHEDULED.value to PillStatus.Scheduled,
        TaskStatus.REJECTED.value to PillStatus.Rejected,
        TaskStatus.AWAITING_APPROVAL.value to PillStatus.AwaitingApproval,
    )

/**
 * Every open task to me except [nextUpId], due first: without these a second task to me stayed hidden until the first
 * was done (NUD-61). A task from someone sync has no connection for is left out, as on the next-up card.
 */
fun laterTasks(
    tasks: List<TaskEntity>,
    me: String,
    connections: Map<String, ConnectionEntity>,
    nextUpId: String?,
    now: Instant,
    timeZone: TimeZone,
): List<LaterUi> =
    tasks
        .filter { it.recipientId == me && it.inRecipientList && it.status in LATER_STATUSES && it.id != nextUpId }
        .filter { it.senderId == me || it.senderId in connections }
        .sortedBy { it.shownTime }
        .map { task ->
            val from = connections[task.senderId]?.takeIf { task.senderId != me }
            LaterUi(
                taskId = task.id,
                title = task.title,
                detail = task.detail,
                deadline = task.timeText(now, timeZone),
                photo = task.finishMode == FinishMode.PHOTO.value,
                fromId = from?.personId,
                fromName = from?.displayName,
                status = LATER_STATUSES.getValue(task.status),
                repeat = task.repeat,
            )
        }

/** 5c: an open task to me as a row in Bana, from someone ("Sibel · bu akşam") or to self ("Kendine · ..."). */
@Composable
internal fun LaterRow(
    task: LaterUi,
    myName: String,
    onClick: () -> Unit,
) {
    val colors = DurtTheme.colors
    val parts =
        listOfNotNull(
            task.fromName ?: stringResource(R.string.home_to_self),
            task.detail,
            task.deadline.counting(task.deadline.inlineText()),
            task.repeat?.text(),
        )
    TaskRow(
        task =
            TaskUi(
                title = task.title,
                subtitle = parts.reduce { line, part -> stringResource(R.string.separator_dot, line, part) },
                status = task.status,
                personName = task.fromName ?: myName,
                personColor = task.fromId?.let { colors.avatarFor(it) } ?: colors.avatarSelf,
                personId = task.fromId ?: MY_AVATAR,
            ),
        onClick = onClick,
        modifier = Modifier.testTag(TestTags.HOME_LATER),
    )
}

/** HomeFull.png in tabs (NUD-67): the next-up card, then the rest written to me and to myself. */
@PreviewScreens
@Composable
private fun HomeScreenFullPreview() {
    DurtTheme {
        HomeScreen(
            state =
                HomeUiState(
                    myName = "Eren",
                    connected = true,
                    nextUp = NextUpUi("task-1", "Para çek", "1.000 TL", DeadlineText.Tomorrow("09:00"), photo = false),
                    later =
                        listOf(
                            LaterUi(
                                "task-2",
                                "Gelirken ekmek al",
                                null,
                                DeadlineText.Today("19:00"),
                                false,
                                "sibel",
                                "Sibel",
                                PillStatus.Scheduled,
                            ),
                            LaterUi(
                                "task-3",
                                "Spor salonu üyeliğini yenile",
                                null,
                                DeadlineText.Tomorrow("12:00"),
                                false,
                                null,
                                null,
                                PillStatus.Scheduled,
                            ),
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
