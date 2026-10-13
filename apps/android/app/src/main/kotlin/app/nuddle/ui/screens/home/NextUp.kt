package app.nuddle.ui.screens.home

import androidx.compose.runtime.Immutable
import app.nuddle.api.model.FinishMode
import app.nuddle.api.model.TaskStatus
import app.nuddle.data.db.ConnectionEntity
import app.nuddle.data.db.TaskEntity
import app.nuddle.data.db.isMoment
import app.nuddle.data.db.shownTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

internal val HOUR_MINUTE =
    LocalTime.Format {
        hour()
        char(':')
        minute()
    }

/**
 * The deadline as the next-up card says it: "Bugün 18:00", "Yarın 09:00", otherwise "14 Ekim 09:00". [moment] marks the
 * moment of an `at` task (ADR 0017): screens say "Zaman" instead of "Son zaman" (open-questions#43).
 */
sealed interface DeadlineText {
    val time: String
    val moment: Boolean

    data class Today(
        override val time: String,
        override val moment: Boolean = false,
    ) : DeadlineText

    data class Tomorrow(
        override val time: String,
        override val moment: Boolean = false,
    ) : DeadlineText

    /** `month` is 1 to 12. Wording for deadlines after tomorrow: open-questions#27. */
    data class OnDate(
        val day: Int,
        val month: Int,
        override val time: String,
        override val moment: Boolean = false,
    ) : DeadlineText
}

/** When someone else wrote a task: "14:32" today, "Dün 14:32", otherwise "12 Ekim 14:32" (open-questions#34). */
sealed interface WrittenText {
    val time: String

    data class Today(
        override val time: String,
    ) : WrittenText

    data class Yesterday(
        override val time: String,
    ) : WrittenText

    /** `month` is 1 to 12. */
    data class OnDate(
        val day: Int,
        val month: Int,
        override val time: String,
    ) : WrittenText
}

/** 4c: who wrote the task, when, and how to reach them (Call, Message). */
@Immutable
data class FromUi(
    val name: String,
    val writtenAt: WrittenText,
    /** Null when sync has no phone (an older server): no Call and Message icons then. */
    val phone: String?,
)

/** Where the next-up task stands for me: to do (2f, 4c), its photo with the sender (4e), or asked again (4g). */
enum class NextUpStatus { Scheduled, AwaitingApproval, ReRequested }

/** 2f, 4c, 4e, 4g: the "Sıradaki" card for the task that is due first. [from] is null for a task to self. */
@Immutable
data class NextUpUi(
    val taskId: String,
    val title: String,
    val detail: String?,
    val deadline: DeadlineText,
    val photo: Boolean,
    val from: FromUi? = null,
    val status: NextUpStatus = NextUpStatus.Scheduled,
    /** "08:24": when the photo went out (4e); null while it is still on its way. */
    val photoSentAt: String? = null,
    /** The sender's note with Tekrar iste (4g). */
    val note: String? = null,
    /** The photo is still on this phone and its upload failed (6e). */
    val uploadFailed: Boolean = false,
)

private val NEXT_UP_STATUSES =
    mapOf(
        TaskStatus.SCHEDULED.value to NextUpStatus.Scheduled,
        TaskStatus.REJECTED.value to NextUpStatus.ReRequested,
        TaskStatus.AWAITING_APPROVAL.value to NextUpStatus.AwaitingApproval,
    )

/**
 * The task to me that is up next, to self or from a connection, or null: what I still have to do (scheduled, asked
 * again) due first, then a photo waiting for the sender. A task from someone sync has no connection for is left out:
 * there is no name to show. Dropped and missed come with slice 5.
 */
fun nextUp(
    tasks: List<TaskEntity>,
    me: String,
    connections: Map<String, ConnectionEntity>,
    now: Instant,
    timeZone: TimeZone,
): NextUpUi? =
    tasks
        .filter { it.recipientId == me && it.inRecipientList && it.status in NEXT_UP_STATUSES }
        .filter { it.senderId == me || it.senderId in connections }
        .minWithOrNull(compareBy({ it.status == TaskStatus.AWAITING_APPROVAL.value }, { it.shownTime }))
        ?.let { task ->
            NextUpUi(
                taskId = task.id,
                title = task.title,
                detail = task.detail,
                deadline = task.timeText(now, timeZone),
                photo = task.finishMode == FinishMode.PHOTO.value,
                from =
                    connections[task.senderId]?.takeIf { task.senderId != me }?.let {
                        FromUi(it.displayName, writtenText(Instant.fromEpochMilliseconds(task.createdAt), now, timeZone), it.phone)
                    },
                status = NEXT_UP_STATUSES.getValue(task.status),
                photoSentAt =
                    task.photoSentAt?.let {
                        Instant
                            .fromEpochMilliseconds(it)
                            .toLocalDateTime(timeZone)
                            .time
                            .format(HOUR_MINUTE)
                    },
                note = task.reRequestNote,
            )
        }

fun writtenText(
    at: Instant,
    now: Instant,
    timeZone: TimeZone,
): WrittenText {
    val local = at.toLocalDateTime(timeZone)
    val time = local.time.format(HOUR_MINUTE)
    return when (local.date.daysUntil(now.toLocalDateTime(timeZone).date)) {
        0 -> WrittenText.Today(time)
        1 -> WrittenText.Yesterday(time)
        else -> WrittenText.OnDate(local.date.day, local.date.month.number, time)
    }
}

fun deadlineText(
    deadline: Instant,
    now: Instant,
    timeZone: TimeZone,
    moment: Boolean = false,
): DeadlineText {
    val local = deadline.toLocalDateTime(timeZone)
    val time = local.time.format(HOUR_MINUTE)
    return when (now.toLocalDateTime(timeZone).date.daysUntil(local.date)) {
        0 -> DeadlineText.Today(time, moment)
        1 -> DeadlineText.Tomorrow(time, moment)
        else -> DeadlineText.OnDate(local.date.day, local.date.month.number, time, moment)
    }
}

/** The time a task shows: its moment for an `at` task, otherwise its deadline (ADR 0017). */
fun TaskEntity.timeText(
    now: Instant,
    timeZone: TimeZone,
): DeadlineText = deadlineText(Instant.fromEpochMilliseconds(shownTime), now, timeZone, isMoment)
