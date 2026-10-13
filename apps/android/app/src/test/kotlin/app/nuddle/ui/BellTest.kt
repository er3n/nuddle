package app.nuddle.ui

import app.nuddle.data.db.TaskEventEntity
import app.nuddle.testing.Cast
import app.nuddle.testing.aTask
import app.nuddle.testing.sibelConnection
import app.nuddle.ui.screens.home.DeadlineText
import app.nuddle.ui.screens.home.WrittenText
import app.nuddle.ui.screens.inbox.BellDay
import app.nuddle.ui.screens.inbox.BellEntry
import app.nuddle.ui.screens.inbox.Finished
import app.nuddle.ui.screens.inbox.bellDays
import app.nuddle.ui.screens.inbox.openUnreached
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** The bell as text for my tasks to others (docs/ux/screens.md 7b, open-questions#36). */
class BellTest {
    private val istanbul = TimeZone.of("Europe/Istanbul")
    private val now = Instant.parse("2026-10-12T10:00:00+03:00")
    private val sibel = mapOf(Cast.SIBEL_ID to sibelConnection())

    private fun toSibel(
        id: String,
        status: String,
        title: String = "Temizlikçi için para çek",
    ) = aTask(id = id, status = status).copy(senderId = Cast.EREN_ID, recipientId = Cast.SIBEL_ID, title = title)

    private fun event(
        taskId: String,
        type: String,
        at: String,
        payload: String = "{}",
    ) = TaskEventEntity(
        id = "$taskId-$type",
        taskId = taskId,
        actorId = null,
        type = type,
        payload = payload,
        at = Instant.parse(at).toEpochMilliseconds(),
    )

    @Test
    fun `who did not answer, who finished and how, who could not today, grouped by day, newest first`() {
        val tasks =
            listOf(
                toSibel("money", "SCHEDULED"),
                toSibel("trash", "DONE", "Çöpü at"),
                toSibel("bread", "DONE", "Gelirken ekmek al"),
                toSibel("cleaning", "DROPPED", "Kuru temizleme"),
                toSibel("late", "DONE", "Faturayı öde"),
            )
        val events =
            listOf(
                event("money", "human_notified", "2026-10-12T08:20:00+03:00"),
                event("money", "poke_fired", "2026-10-12T08:15:00+03:00"),
                event("trash", "done", "2026-10-12T07:58:00+03:00", """{"cameraUnavailable":false}"""),
                event("bread", "approved", "2026-10-11T19:02:00+03:00", """{"auto":false}"""),
                event("cleaning", "dropped", "2026-10-11T17:40:00+03:00", """{"note":"yarın hallederim"}"""),
                // Older than a week: gone from the bell.
                event("late", "done", "2026-10-05T09:00:00+03:00", """{"late":true}"""),
            )

        assertEquals(
            listOf(
                BellDay(
                    WrittenText.Today("08:20"),
                    listOf(
                        BellEntry.Unreached(
                            "money",
                            "Sibel",
                            "Temizlikçi için para çek",
                            "08:20",
                            DeadlineText.Today("09:00"),
                            open = true,
                        ),
                        BellEntry.Done("trash", "Sibel", "Çöpü at", "07:58", Finished.SaidDone),
                    ),
                ),
                BellDay(
                    WrittenText.Yesterday("19:02"),
                    listOf(
                        BellEntry.Done("bread", "Sibel", "Gelirken ekmek al", "19:02", Finished.Approved),
                        BellEntry.Dropped("cleaning", "Sibel", "Kuru temizleme", "17:40", "yarın hallederim"),
                    ),
                ),
            ),
            bellDays(tasks, events, Cast.EREN_ID, sibel, now, istanbul),
        )
    }

    @Test
    fun `how a task ended reads from its event`() {
        val tasks = listOf(toSibel("a", "DONE"), toSibel("b", "DONE"), toSibel("c", "DONE"))
        val events =
            listOf(
                event("a", "done", "2026-10-12T09:03:00+03:00", """{"late":true}"""),
                event("b", "done", "2026-10-12T09:02:00+03:00", """{"cameraUnavailable":true}"""),
                event("c", "approved", "2026-10-12T09:01:00+03:00", """{"auto":true}"""),
            )
        val hows = bellDays(tasks, events, Cast.EREN_ID, sibel, now, istanbul).single().entries.map { (it as BellEntry.Done).how }
        assertEquals(listOf(Finished.Late, Finished.NoPhoto, Finished.AutoApproved), hows)
    }

    @Test
    fun `tasks to me and to self stay out, and only open unreached tasks count on the badge`() {
        val toMe = aTask(id = "to-me", status = "DONE")
        val closed = toSibel("closed", "DONE")
        val open = toSibel("open", "SCHEDULED")
        val events =
            listOf(
                event("to-me", "done", "2026-10-12T09:00:00+03:00"),
                event("closed", "human_notified", "2026-10-12T08:00:00+03:00"),
                event("open", "human_notified", "2026-10-12T08:30:00+03:00"),
            )

        val entries = bellDays(listOf(toMe, closed, open), events, Cast.EREN_ID, sibel, now, istanbul).flatMap { it.entries }
        assertEquals(listOf("open" to true, "closed" to false), entries.map { it.taskId to (it as BellEntry.Unreached).open })
        assertEquals(1, openUnreached(listOf(toMe, closed, open), events.groupBy { it.taskId }, Cast.EREN_ID))
    }
}
