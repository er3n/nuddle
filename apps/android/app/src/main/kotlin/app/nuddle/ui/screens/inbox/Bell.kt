package app.nuddle.ui.screens.inbox

import androidx.compose.runtime.Immutable
import app.nuddle.api.model.TaskStatus
import app.nuddle.data.EventTypes
import app.nuddle.data.auto
import app.nuddle.data.cameraUnavailable
import app.nuddle.data.db.ConnectionEntity
import app.nuddle.data.db.TaskEntity
import app.nuddle.data.db.TaskEventEntity
import app.nuddle.data.late
import app.nuddle.data.note
import app.nuddle.data.sinceLastDeadline
import app.nuddle.ui.screens.home.DeadlineText
import app.nuddle.ui.screens.home.WrittenText
import app.nuddle.ui.screens.home.deadlineText
import app.nuddle.ui.screens.home.timeText
import app.nuddle.ui.screens.home.writtenText
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** How a task of mine to someone else ended, as the bell says it (7b). */
enum class Finished { SaidDone, NoPhoto, Late, Approved, AutoApproved }

/** One line of the bell about my task to someone else (7b, open-questions#36). */
@Immutable
sealed interface BellEntry {
    val taskId: String
    val name: String
    val title: String

    /** "08:20": the day is the group's. */
    val time: String

    /** The human channel fired (7a). Marked while the task is still open, and then it opens the task. */
    data class Unreached(
        override val taskId: String,
        override val name: String,
        override val title: String,
        override val time: String,
        val deadline: DeadlineText,
        val open: Boolean,
    ) : BellEntry

    data class Done(
        override val taskId: String,
        override val name: String,
        override val title: String,
        override val time: String,
        val how: Finished,
    ) : BellEntry

    data class Dropped(
        override val taskId: String,
        override val name: String,
        override val title: String,
        override val time: String,
        val note: String?,
    ) : BellEntry
}

/** The entries of one day, newest first: "Bugün", "Dün" or the date. */
@Immutable
data class BellDay(
    val day: WrittenText,
    val entries: List<BellEntry>,
)

/** How far back the bell goes (open-questions#36). */
internal val BELL_WINDOW = 7.days

/**
 * My tasks to others from the last week, as text: who did not answer, who finished and how, who could not today.
 * Finished tasks are not pushed to me separately; they sit here (docs/product.md, Bell and communication).
 */
fun bellDays(
    tasks: List<TaskEntity>,
    events: List<TaskEventEntity>,
    me: String,
    connections: Map<String, ConnectionEntity>,
    now: Instant,
    timeZone: TimeZone,
): List<BellDay> {
    val mine = tasks.filter { it.senderId == me && it.recipientId != me }.associateBy { it.id }
    val entries =
        events
            .filter { Instant.fromEpochMilliseconds(it.at) > now - BELL_WINDOW }
            .sortedByDescending { it.at }
            .mapNotNull { event ->
                val task = mine[event.taskId] ?: return@mapNotNull null
                val name = connections[task.recipientId]?.displayName ?: return@mapNotNull null
                val at = Instant.fromEpochMilliseconds(event.at)
                entry(event, task, name, writtenText(at, now, timeZone).time, now, timeZone)?.let { at to it }
            }
    return entries
        .groupBy { (at, _) -> at.toLocalDateTime(timeZone).date }
        .map { (_, day) -> BellDay(writtenText(day.first().first, now, timeZone), day.map { it.second }) }
}

private fun entry(
    event: TaskEventEntity,
    task: TaskEntity,
    name: String,
    time: String,
    now: Instant,
    timeZone: TimeZone,
): BellEntry? =
    when (event.type) {
        EventTypes.HUMAN_NOTIFIED -> {
            val deadline = task.timeText(now, timeZone)
            val open = task.status == TaskStatus.SCHEDULED.value || task.status == TaskStatus.REJECTED.value
            BellEntry.Unreached(task.id, name, task.title, time, deadline, open)
        }

        EventTypes.DONE -> {
            val how =
                when {
                    event.late -> Finished.Late
                    event.cameraUnavailable -> Finished.NoPhoto
                    else -> Finished.SaidDone
                }
            BellEntry.Done(task.id, name, task.title, time, how)
        }

        EventTypes.APPROVED -> {
            BellEntry.Done(task.id, name, task.title, time, if (event.auto) Finished.AutoApproved else Finished.Approved)
        }

        EventTypes.DROPPED -> {
            BellEntry.Dropped(task.id, name, task.title, time, event.note)
        }

        else -> {
            null
        }
    }

/** My open tasks to others whose human channel fired this round: marked in the bell and counted on its badge. */
fun openUnreached(
    tasks: List<TaskEntity>,
    events: Map<String, List<TaskEventEntity>>,
    me: String,
): Int =
    tasks.count { task ->
        task.senderId == me &&
            task.recipientId != me &&
            (task.status == TaskStatus.SCHEDULED.value || task.status == TaskStatus.REJECTED.value) &&
            events[task.id].orEmpty().sinceLastDeadline().any { it.type == EventTypes.HUMAN_NOTIFIED }
    }
