package app.nuddle.ui.screens.home

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.nuddle.R
import app.nuddle.api.model.FinishMode
import app.nuddle.api.model.TaskStatus
import app.nuddle.data.db.ConnectionEntity
import app.nuddle.data.db.TaskEntity
import app.nuddle.data.db.shownTime
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.PillStatus
import app.nuddle.ui.components.TaskRow
import app.nuddle.ui.components.TaskUi
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.theme.DurtTheme
import app.nuddle.ui.theme.avatarFor
import app.nuddle.ui.uppercaseForUi
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/** 5c: an open task to me after the next-up card, as a row under "Sana yazılanlar" or "Kendine" (NUD-61). */
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
            )
        }

/** "Sana yazılanlar" for tasks from others, then "Kendine"; a task to self opens its detail (7c). */
@Composable
internal fun ColumnScope.LaterSection(
    later: List<LaterUi>,
    myName: String,
    onOpenSelf: (taskId: String) -> Unit,
) {
    val (fromOthers, toSelf) = later.partition { it.fromId != null }
    if (fromOthers.isNotEmpty()) {
        Heading(R.string.home_to_me)
        fromOthers.forEach { LaterRow(it, myName, onClick = null) }
    }
    if (toSelf.isNotEmpty()) {
        Heading(R.string.home_to_self)
        toSelf.forEach { task -> LaterRow(task, myName, onClick = { onOpenSelf(task.taskId) }) }
    }
}

@Composable
private fun Heading(text: Int) {
    Text(
        text = stringResource(text).uppercaseForUi(),
        style = DurtTheme.type.overline,
        color = DurtTheme.colors.textTertiary,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun LaterRow(
    task: LaterUi,
    myName: String,
    onClick: (() -> Unit)?,
) {
    val colors = DurtTheme.colors
    val parts = listOfNotNull(task.fromName, task.detail, task.deadline.inlineText())
    TaskRow(
        task =
            TaskUi(
                title = task.title,
                subtitle = parts.reduce { line, part -> stringResource(R.string.separator_dot, line, part) },
                status = task.status,
                personName = task.fromName ?: myName,
                personColor = task.fromId?.let { colors.avatarFor(it) } ?: colors.avatarSelf,
            ),
        onClick = onClick,
        modifier = Modifier.testTag(TestTags.HOME_LATER),
    )
}

/** HomeFull.png: the next-up card, then the rest written to me and to myself. */
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
