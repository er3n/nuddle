package app.nuddle.ui.screens.home

import androidx.compose.runtime.Immutable
import app.nuddle.api.model.FinishMode
import app.nuddle.data.db.TaskEntity
import app.nuddle.domain.QuietHours
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

// Parts of the day as the extraction prompt reads them (apps/web/src/infra/llm): akşam 19:00, sabah 09:00.
// TODO(open-questions#28): the times behind "Bugün akşam" and "Yarın sabah".
private val EVENING = LocalTime(19, 0)
private val MORNING = LocalTime(9, 0)

/** The answers to "Ne zamana kadar?" (2b). [Picked] comes from "Saat seç". */
sealed interface DeadlineChoice {
    val at: Instant

    data class TonightEvening(
        override val at: Instant,
    ) : DeadlineChoice

    data class TomorrowMorning(
        override val at: Instant,
    ) : DeadlineChoice

    data class Picked(
        override val at: Instant,
        val time: String,
    ) : DeadlineChoice
}

/** 2b: what was understood from the text, and the one question (the deadline). */
@Immutable
data class PreviewUi(
    val rawText: String,
    val title: String,
    val detail: String?,
    val choices: List<DeadlineChoice>,
    val selected: DeadlineChoice?,
    val quietStart: String,
    val quietEnd: String,
    val sending: Boolean = false,
)

/** 2a: the task that just went out without a question, for the undo toast. */
@Immutable
data class UndoUi(
    val taskId: String,
    val rawText: String,
    val title: String,
    val detail: String?,
    val deadline: DeadlineText,
    val photo: Boolean,
)

/** "Bugün akşam" only while this evening is still ahead, then "Yarın sabah". */
fun quickDeadlines(
    now: Instant,
    timeZone: TimeZone,
): List<DeadlineChoice> {
    val today = now.toLocalDateTime(timeZone).date
    val evening = LocalDateTime(today, EVENING).toInstant(timeZone)
    val morning = LocalDateTime(today.plus(DatePeriod(days = 1)), MORNING).toInstant(timeZone)
    return listOfNotNull(
        if (evening > now) DeadlineChoice.TonightEvening(evening) else null,
        DeadlineChoice.TomorrowMorning(morning),
    )
}

/** "Saat seç" gives a time of day: the next time the clock shows it, today or tomorrow. */
fun nextOccurrence(
    time: LocalTime,
    now: Instant,
    timeZone: TimeZone,
): Instant {
    val today = now.toLocalDateTime(timeZone).date
    val todayAt = LocalDateTime(today, time).toInstant(timeZone)
    return if (todayAt > now) todayAt else LocalDateTime(today.plus(DatePeriod(days = 1)), time).toInstant(timeZone)
}

/** The preview as it opens: nothing chosen yet. */
fun previewFor(
    rawText: String,
    title: String,
    detail: String?,
    quiet: QuietHours,
    now: Instant,
): PreviewUi =
    PreviewUi(
        rawText = rawText,
        title = title,
        detail = detail,
        choices = quickDeadlines(now, quiet.timeZone),
        selected = null,
        quietStart = quiet.start.format(HOUR_MINUTE),
        quietEnd = quiet.end.format(HOUR_MINUTE),
    )

/** The undo toast for a task that just went out. */
fun undoFor(
    task: TaskEntity,
    rawText: String,
    now: Instant,
    timeZone: TimeZone,
): UndoUi =
    UndoUi(
        taskId = task.id,
        rawText = rawText,
        title = task.title,
        detail = task.detail,
        deadline = deadlineText(Instant.fromEpochMilliseconds(task.deadline), now, timeZone),
        photo = task.finishMode == FinishMode.PHOTO.value,
    )
