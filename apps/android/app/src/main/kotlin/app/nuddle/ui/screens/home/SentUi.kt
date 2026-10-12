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
    /** A photo came and waits for my approval (4f). */
    val awaitingApproval: Boolean = false,
    /** I asked for another photo (4g). */
    val reRequested: Boolean = false,
)

/** My tasks to others that are still going: to do, a photo waiting for me, asked again. */
private val SENT_STATUSES =
    setOf(TaskStatus.SCHEDULED.value, TaskStatus.AWAITING_APPROVAL.value, TaskStatus.REJECTED.value)

/**
 * My open tasks to other people: a photo waiting for my approval on top, then due first. Dropped and missed are
 * cards of their own ([issues], 5d). A task to someone sync has no connection for is left out: there is no name.
 */
fun sentTasks(
    tasks: List<TaskEntity>,
    me: String,
    connections: Map<String, ConnectionEntity>,
    now: Instant,
    timeZone: TimeZone,
): List<SentUi> =
    tasks
        .filter { it.senderId == me && it.recipientId != me && it.status in SENT_STATUSES }
        .sortedWith(compareBy({ it.status != TaskStatus.AWAITING_APPROVAL.value }, { it.deadline }))
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
                awaitingApproval = task.status == TaskStatus.AWAITING_APPROVAL.value,
                reRequested = task.status == TaskStatus.REJECTED.value,
            )
        }
