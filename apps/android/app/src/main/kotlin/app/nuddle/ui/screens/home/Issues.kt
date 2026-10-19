package app.nuddle.ui.screens.home

import androidx.compose.runtime.Immutable
import app.nuddle.api.model.FinishMode
import app.nuddle.api.model.TaskStatus
import app.nuddle.data.EventTypes
import app.nuddle.data.db.ConnectionEntity
import app.nuddle.data.db.TaskEntity
import app.nuddle.data.db.TaskEventEntity
import app.nuddle.data.note
import app.nuddle.data.reason
import app.nuddle.data.sinceLastDeadline
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** 7e: a task to me whose deadline passed. [fromName] is null for a task to self. */
@Immutable
data class MissedUi(
    val taskId: String,
    val title: String,
    val deadline: DeadlineText,
    val fromId: String? = null,
    val fromName: String? = null,
    /** Null for a task to self or without a number from sync: no Ara then. */
    val phone: String? = null,
)

/** 7e: the sender took a task to me back. */
@Immutable
data class CancelledUi(
    val taskId: String,
    val title: String,
    val byName: String,
    val note: String?,
    /** Kime changed (NUD-114): the sender gave the task to someone else, unnamed here. */
    val reassigned: Boolean = false,
)

enum class SentIssue { Dropped, Missed }

/** 5d: my task to someone else that they dropped or missed, waiting for my answer. */
@Immutable
data class SentIssueUi(
    val taskId: String,
    val title: String,
    val toId: String,
    val toName: String,
    val deadline: DeadlineText,
    val issue: SentIssue,
    /** The drop note. */
    val note: String? = null,
    val photo: Boolean = false,
    /** Pokes that rang for the current deadline. */
    val pokes: Int = 0,
    /** The human channel told me ("sana da haber verdim"). */
    val toldMe: Boolean = false,
)

/** What on home needs an answer: 7e for tasks to me, 5d for my tasks to others. */
@Immutable
data class IssuesUi(
    val missed: List<MissedUi> = emptyList(),
    val cancelled: List<CancelledUi> = emptyList(),
    val sent: List<SentIssueUi> = emptyList(),
) {
    val toMe: Boolean get() = missed.isNotEmpty() || cancelled.isNotEmpty()
    val isEmpty: Boolean get() = !toMe && sent.isEmpty()
}

/** A cancelled row stays this long after the cancel (open-questions#36). */
internal val CANCELLED_ROW_TTL = 1.days

/**
 * The cards from the stored tasks and their events. A missed task to me shows while the server keeps it in my list
 * (one day); a cancel shows for a day, but not "Ben hallederim" on a task I dropped myself. Newest first.
 */
fun issues(
    tasks: List<TaskEntity>,
    events: Map<String, List<TaskEventEntity>>,
    me: String,
    connections: Map<String, ConnectionEntity>,
    now: Instant,
    timeZone: TimeZone,
): IssuesUi {
    fun deadline(task: TaskEntity) = task.timeText(now, timeZone)
    val newestFirst = tasks.sortedByDescending { it.statusChangedAt }
    val toMe = newestFirst.filter { it.recipientId == me }
    val missed =
        toMe
            .filter { it.status == TaskStatus.MISSED.value && it.inRecipientList }
            .mapNotNull { task ->
                if (task.senderId == me) return@mapNotNull MissedUi(task.id, task.title, deadline(task))
                val from = connections[task.senderId] ?: return@mapNotNull null
                MissedUi(task.id, task.title, deadline(task), from.personId, from.displayName, from.phone)
            }
    val cancelled =
        toMe
            .filter { it.senderId != me && it.status == TaskStatus.CANCELLED.value }
            .filter { now < Instant.fromEpochMilliseconds(it.statusChangedAt) + CANCELLED_ROW_TTL }
            .mapNotNull { task ->
                val cancel = events[task.id].orEmpty().lastOrNull { it.type == EventTypes.CANCELLED }
                if (cancel == null || cancel.reason !in SHOWN_CANCELS) return@mapNotNull null
                val by = connections[task.senderId] ?: return@mapNotNull null
                CancelledUi(task.id, task.title, by.displayName, cancel.note, reassigned = cancel.reason == REASSIGNED)
            }
    val sent =
        newestFirst
            .filter { it.senderId == me && it.recipientId != me }
            .mapNotNull { task ->
                val issue =
                    when (task.status) {
                        TaskStatus.DROPPED.value -> SentIssue.Dropped
                        TaskStatus.MISSED.value -> SentIssue.Missed
                        else -> return@mapNotNull null
                    }
                val to = connections[task.recipientId] ?: return@mapNotNull null
                val current = events[task.id].orEmpty().sinceLastDeadline()
                SentIssueUi(
                    taskId = task.id,
                    title = task.title,
                    toId = to.personId,
                    toName = to.displayName,
                    deadline = deadline(task),
                    issue = issue,
                    note = current.lastOrNull { it.type == EventTypes.DROPPED }?.note,
                    photo = task.finishMode == FinishMode.PHOTO.value,
                    pokes = current.count { it.type == EventTypes.POKE_FIRED },
                    toldMe = current.any { it.type == EventTypes.HUMAN_NOTIFIED },
                )
            }
    return IssuesUi(missed, cancelled, sent)
}

private const val SENDER_CANCELLED = "sender_cancelled"
private const val REASSIGNED = "reassigned"

/** The cancels the recipient sees a row for: the sender took the task back, or gave it to someone else. */
private val SHOWN_CANCELS = setOf(SENDER_CANCELLED, REASSIGNED)
