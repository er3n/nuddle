package app.nuddle.ui.screens.home

import androidx.compose.runtime.Immutable
import app.nuddle.api.model.FinishMode
import app.nuddle.api.model.TaskStatus
import app.nuddle.data.db.TaskEntity
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

private val HOUR_MINUTE =
    LocalTime.Format {
        hour()
        char(':')
        minute()
    }

/** The deadline as the next-up card says it: "Bugün 18:00", "Yarın 09:00", otherwise "14 Ekim 09:00". */
sealed interface DeadlineText {
    val time: String

    data class Today(
        override val time: String,
    ) : DeadlineText

    data class Tomorrow(
        override val time: String,
    ) : DeadlineText

    /** `month` is 1 to 12. TODO(open-questions#27): the wording for deadlines after tomorrow. */
    data class OnDate(
        val day: Int,
        val month: Int,
        override val time: String,
    ) : DeadlineText
}

/** 2f: the "Sıradaki" card for the task that is due first. */
@Immutable
data class NextUpUi(
    val taskId: String,
    val title: String,
    val detail: String?,
    val deadline: DeadlineText,
    val photo: Boolean,
)

/**
 * The open task to self that is due first, or null. Tasks from other people arrive with slice 3, the other
 * statuses (awaiting approval, missed) with slices 4 and 5.
 */
fun nextUp(
    tasks: List<TaskEntity>,
    me: String,
    now: Instant,
    timeZone: TimeZone,
): NextUpUi? =
    tasks
        .filter { it.senderId == me && it.recipientId == me && it.inRecipientList && it.status == TaskStatus.SCHEDULED.value }
        .minByOrNull { it.deadline }
        ?.let { task ->
            NextUpUi(
                taskId = task.id,
                title = task.title,
                detail = task.detail,
                deadline = deadlineText(Instant.fromEpochMilliseconds(task.deadline), now, timeZone),
                photo = task.finishMode == FinishMode.PHOTO.value,
            )
        }

fun deadlineText(
    deadline: Instant,
    now: Instant,
    timeZone: TimeZone,
): DeadlineText {
    val local = deadline.toLocalDateTime(timeZone)
    val time = local.time.format(HOUR_MINUTE)
    return when (now.toLocalDateTime(timeZone).date.daysUntil(local.date)) {
        0 -> DeadlineText.Today(time)
        1 -> DeadlineText.Tomorrow(time)
        else -> DeadlineText.OnDate(local.date.day, local.date.month.number, time)
    }
}
