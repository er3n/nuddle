package app.nuddle.ui

import app.nuddle.data.db.TaskEventEntity
import app.nuddle.testing.Cast
import app.nuddle.testing.aTask
import app.nuddle.testing.sibelConnection
import app.nuddle.ui.screens.home.CancelledUi
import app.nuddle.ui.screens.home.DeadlineText
import app.nuddle.ui.screens.home.MissedUi
import app.nuddle.ui.screens.home.SentIssue
import app.nuddle.ui.screens.home.issues
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Home's cards that wait for an answer (docs/ux/screens.md 7e, 5d; open-questions#36). */
class IssuesTest {
    private val istanbul = TimeZone.of("Europe/Istanbul")
    private val now = Instant.parse("2026-10-12T10:00:00+03:00")
    private val sibel = mapOf(Cast.SIBEL_ID to sibelConnection())

    private fun event(
        type: String,
        payload: String = "{}",
        taskId: String = Cast.TASK_ID,
        ago: Duration = 1.hours,
    ) = TaskEventEntity(
        id = "$type-${ago.inWholeMinutes}",
        taskId = taskId,
        actorId = null,
        type = type,
        payload = payload,
        at = (now - ago).toEpochMilliseconds(),
    )

    @Test
    fun `a missed task to me shows while it is in my list, with how to reach the sender`() {
        val missed = aTask(status = "MISSED").copy(statusChangedAt = (now - 1.hours).toEpochMilliseconds())
        val gone = aTask(id = "gone", status = "MISSED").copy(inRecipientList = false)
        val selfMissed = aTask(id = "self", status = "MISSED").copy(senderId = Cast.EREN_ID)

        val result = issues(listOf(missed, gone, selfMissed), emptyMap(), Cast.EREN_ID, sibel, now, istanbul)

        assertEquals(
            listOf(
                MissedUi(Cast.TASK_ID, "Temizlikçi için para çek", DeadlineText.Today("09:00"), Cast.SIBEL_ID, "Sibel", Cast.SIBEL_PHONE),
                MissedUi("self", "Temizlikçi için para çek", DeadlineText.Today("09:00")),
            ),
            result.missed,
        )
    }

    @Test
    fun `a cancel by the sender shows for a day with its note, but not Ben hallederim on a task I dropped`() {
        val cancelled = aTask(status = "CANCELLED").copy(statusChangedAt = (now - 23.hours).toEpochMilliseconds())
        val cancelEvent = event("cancelled", """{"reason":"sender_cancelled","note":"Ben hallettim"}""")
        val taken = aTask(id = "taken", status = "CANCELLED").copy(statusChangedAt = now.toEpochMilliseconds())
        val takenEvent = event("cancelled", """{"reason":"sender_handles"}""", taskId = "taken")
        val events = mapOf(Cast.TASK_ID to listOf(cancelEvent), "taken" to listOf(takenEvent))

        val result = issues(listOf(cancelled, taken), events, Cast.EREN_ID, sibel, now, istanbul)
        assertEquals(listOf(CancelledUi(Cast.TASK_ID, "Temizlikçi için para çek", "Sibel", "Ben hallettim")), result.cancelled)

        val dayLater = issues(listOf(cancelled), events, Cast.EREN_ID, sibel, now + 1.hours, istanbul)
        assertTrue(dayLater.cancelled.isEmpty())
    }

    @Test
    fun `a task given to someone else says so, without naming them (NUD-114)`() {
        val gone = aTask(status = "CANCELLED").copy(statusChangedAt = now.toEpochMilliseconds())
        val event = event("cancelled", """{"reason":"reassigned","note":null,"to":"defne"}""")

        val result = issues(listOf(gone), mapOf(Cast.TASK_ID to listOf(event)), Cast.EREN_ID, sibel, now, istanbul)

        assertEquals(
            listOf(CancelledUi(Cast.TASK_ID, "Temizlikçi için para çek", "Sibel", null, reassigned = true)),
            result.cancelled,
        )
    }

    private fun toSibel(
        id: String,
        status: String,
    ) = aTask(id = id, status = status).copy(senderId = Cast.EREN_ID, recipientId = Cast.SIBEL_ID)

    @Test
    fun `my dropped task carries the note, my missed one the pokes of its current deadline`() {
        val events =
            mapOf(
                "dropped" to listOf(event("dropped", """{"note":"Vaktim olmadı"}""", taskId = "dropped")),
                "missed" to
                    listOf(
                        event("created", taskId = "missed", ago = 50.hours),
                        event("poke_fired", """{"level":"SOUND"}""", taskId = "missed", ago = 49.hours),
                        event("human_notified", taskId = "missed", ago = 48.hours),
                        // "Süreyi uzat" once: the count starts again.
                        event("edited", """{"reopened":true}""", taskId = "missed", ago = 3.hours),
                        event("poke_fired", """{"level":"SOUND"}""", taskId = "missed", ago = 90.minutes),
                        event("poke_fired", """{"level":"FULL_SCREEN"}""", taskId = "missed", ago = 75.minutes),
                    ),
            )
        val tasks = listOf(toSibel("dropped", "DROPPED"), toSibel("missed", "MISSED"), toSibel("open", "SCHEDULED"))

        val sent = issues(tasks, events, Cast.EREN_ID, sibel, now, istanbul).sent

        assertEquals(listOf("dropped", "missed"), sent.map { it.taskId }.sorted())
        val dropped = sent.first { it.taskId == "dropped" }
        assertEquals(SentIssue.Dropped to "Vaktim olmadı", dropped.issue to dropped.note)
        val missed = sent.first { it.taskId == "missed" }
        assertEquals(SentIssue.Missed, missed.issue)
        assertEquals(2, missed.pokes)
        assertEquals(false, missed.toldMe)
        assertTrue(missed.photo)
    }
}
