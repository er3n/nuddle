package app.nuddle.ui.screens.home

import androidx.compose.runtime.Immutable
import app.nuddle.api.model.FinishMode
import app.nuddle.api.model.TaskStatus
import app.nuddle.data.db.ConnectionEntity
import app.nuddle.data.db.TaskEntity
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/** 4i, 5c: one task I wrote to someone else, as a row under "Senin yazdıkların". */
@Immutable
data class SentUi(
    val taskId: String,
    val title: String,
    val detail: String?,
    val deadline: DeadlineText,
    val photo: Boolean,
    val toId: String,
    val toName: String,
)

/**
 * My open tasks to other people, due first on top. Slice 3 has only scheduled ones; awaiting approval, dropped and
 * missed come with slices 4 and 5. A task to someone sync has no connection for is left out: there is no name.
 */
fun sentTasks(
    tasks: List<TaskEntity>,
    me: String,
    connections: Map<String, ConnectionEntity>,
    now: Instant,
    timeZone: TimeZone,
): List<SentUi> =
    tasks
        .filter { it.senderId == me && it.recipientId != me && it.status == TaskStatus.SCHEDULED.value }
        .sortedBy { it.deadline }
        .mapNotNull { task ->
            val to = connections[task.recipientId] ?: return@mapNotNull null
            SentUi(
                taskId = task.id,
                title = task.title,
                detail = task.detail,
                deadline = deadlineText(Instant.fromEpochMilliseconds(task.deadline), now, timeZone),
                photo = task.finishMode == FinishMode.PHOTO.value,
                toId = to.personId,
                toName = to.displayName,
            )
        }
