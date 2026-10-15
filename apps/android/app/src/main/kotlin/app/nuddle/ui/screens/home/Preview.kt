package app.nuddle.ui.screens.home

import androidx.compose.runtime.Immutable
import app.nuddle.api.model.FinishMode
import app.nuddle.api.model.Reachability
import app.nuddle.api.model.WriteTaskResult
import app.nuddle.data.WriteOutcome
import app.nuddle.data.db.ConnectionEntity
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
import java.util.Locale
import kotlin.time.Instant

// Parts of the day as the extraction prompt reads them (apps/web/src/infra/llm): akşam 19:00, sabah 09:00.
// The times behind "Bugün 19:00" and "Yarın 09:00": open-questions#28.
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
    /** Who it is for; null for a task to self ("Kendine") or while "Kime?" is unanswered. */
    val to: String? = null,
    /** 4a: the answers to "Kime?" when the server asked it; empty otherwise. */
    val recipients: List<RecipientChoice> = emptyList(),
    /** The chosen recipient; set from the text when only the deadline was asked. */
    val recipient: RecipientChoice? = RecipientChoice.Self,
    /** The deadline the text already gave, when the question was "Kime?"; then "Ne zamana kadar?" is not asked. */
    val knownDeadline: DeadlineText? = null,
    /** The rule the text gave (ADR 0021): "Her gün 09:00" under the time; the picked time is its first occurrence. */
    val repeat: RepeatUi? = null,
    /** For a moment ([knownDeadline] `moment`): how long after it the task is missed, from the extraction. */
    val missAfterMinutes: Int? = null,
    /** 4a, a task to someone else: "Fotoğrafla, sen onayla" is chosen; [photoSuggested] when the text was about money. */
    val photo: Boolean = false,
    val photoSuggested: Boolean = false,
    /** 4a: how far pokes reach the recipient now; null for a task to self. */
    val reach: Reachability? = null,
) {
    val toOther: Boolean get() = recipient is RecipientChoice.Person

    val ready: Boolean get() = recipient != null && (knownDeadline != null || selected != null)
}

/** The same time as a moment or as a deadline: a tap on the 2b time row switches (open-questions#43). */
fun DeadlineText.asMoment(moment: Boolean): DeadlineText =
    when (this) {
        is DeadlineText.Today -> copy(moment = moment)
        is DeadlineText.Tomorrow -> copy(moment = moment)
        is DeadlineText.OnDate -> copy(moment = moment)
    }

/** One answer to "Kime?" (4a). */
sealed interface RecipientChoice {
    data object Self : RecipientChoice

    data class Person(
        val personId: String,
        val name: String,
    ) : RecipientChoice
}

/** 2a: the task that just went out without a question, for the undo toast. */
@Immutable
data class UndoUi(
    val taskId: String,
    val rawText: String,
    val title: String,
    val detail: String?,
    val deadline: DeadlineText,
    val photo: Boolean,
    /** The recipient's name (4i, "Gönderildi"); null for a task to self (2a, "Kuruldu"). */
    val to: String? = null,
)

/** 5c: shown for 4 s after a task is done; [caughtAt] is the poke number that caught the person, if one rang. */
@Immutable
data class DoneNoteUi(
    val title: String,
    val caughtAt: Int?,
)

/** "Bugün 19:00" only while this evening is still ahead, then "Yarın 09:00". */
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
    to: String?,
    now: Instant,
    timeZone: TimeZone,
): UndoUi =
    UndoUi(
        taskId = task.id,
        rawText = rawText,
        title = task.title,
        detail = task.detail,
        deadline = task.timeText(now, timeZone),
        photo = task.finishMode == FinishMode.PHOTO.value,
        to = to,
    )

private val TURKISH = Locale.forLanguageTag("tr")

private fun String.folded() = trim().lowercase(TURKISH)

/** The extraction's recipient: "self" (or none) is Kendine, a name must match exactly one connection. */
internal fun recipientNamed(
    name: String?,
    connections: List<ConnectionEntity>,
): RecipientChoice? {
    if (name == null || name.folded() == "self") return RecipientChoice.Self
    val match = connections.singleOrNull { it.displayName.folded() == name.folded() } ?: return null
    return RecipientChoice.Person(match.personId, match.displayName)
}

/**
 * The preview for what the server asked (2b, 4a), with the deadline the text already gave when only "Kime?" was
 * asked; null when there is nothing to ask on a sheet (no title, a question without a design, or a name no
 * connection matches).
 */
fun previewFor(
    rawText: String,
    ask: WriteOutcome.Ask,
    connections: List<ConnectionEntity>,
    quiet: QuietHours,
    now: Instant,
): Pair<PreviewUi, Instant?>? {
    val draft = ask.draft ?: return null
    val title = draft.title ?: return null
    val photo = draft.finishMode == FinishMode.PHOTO
    val repeat = draft.repeat?.let { RepeatUi(it.every.value, it.days.orEmpty(), it.time) }
    val base = previewFor(rawText, title, draft.detail, quiet, now).copy(photo = photo, photoSuggested = photo, repeat = repeat)
    return when (ask.question) {
        WriteTaskResult.Question.DEADLINE -> {
            when (val recipient = recipientNamed(draft.recipient, connections) ?: return null) {
                RecipientChoice.Self -> {
                    base to null
                }

                is RecipientChoice.Person -> {
                    val reach = connections.first { it.personId == recipient.personId }.reachability
                    base.copy(recipient = recipient, to = recipient.name, reach = Reachability.decode(reach) ?: Reachability.NONE) to null
                }
            }
        }

        WriteTaskResult.Question.RECIPIENT -> {
            val known = draft.deadline?.takeIf { it > now }
            val people = connections.map { RecipientChoice.Person(it.personId, it.displayName) }
            base.copy(
                recipients = listOf(RecipientChoice.Self) + people,
                recipient = null,
                knownDeadline = known?.let { deadlineText(it, now, quiet.timeZone, draft.moment) },
                missAfterMinutes = draft.missAfterMinutes,
            ) to known
        }

        else -> {
            null
        }
    }
}
