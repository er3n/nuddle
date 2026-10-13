package app.nuddle.ui

import app.nuddle.testing.Cast
import app.nuddle.testing.aTask
import app.nuddle.testing.sibelConnection
import app.nuddle.ui.screens.home.DeadlineText
import app.nuddle.ui.screens.home.sentTasks
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** "Senin yazdıkların" (docs/ux/screens.md 4i): which of my tasks to others are listed, in which order. */
class SentTasksTest {
    private val istanbul = TimeZone.of("Europe/Istanbul")
    private val now = Instant.parse("2026-10-11T20:00:00+03:00")
    private val sibel = mapOf(Cast.SIBEL_ID to sibelConnection())

    private fun toSibel(
        id: String,
        deadline: String,
        status: String = "SCHEDULED",
    ) = aTask(id = id, status = status, deadline = Instant.parse(deadline)).copy(senderId = Cast.EREN_ID, recipientId = Cast.SIBEL_ID)

    @Test
    fun `my open tasks to others, due first on top, with who they are for`() {
        val tasks =
            listOf(
                toSibel("later", "2026-10-13T09:00:00+03:00"),
                toSibel("first", "2026-10-12T09:00:00+03:00"),
                toSibel("cancelled", "2026-10-12T08:00:00+03:00", status = "CANCELLED"),
            )
        val sent = sentTasks(tasks, Cast.EREN_ID, sibel, now, istanbul)
        assertEquals(listOf("first", "later"), sent.map { it.taskId })
        assertEquals("Sibel" to Cast.SIBEL_ID, sent.first().toName to sent.first().toId)
        assertEquals(DeadlineText.Tomorrow("09:00"), sent.first().deadline)
    }

    @Test
    fun `a photo waiting for my approval comes first, and a re-requested task stays listed`() {
        val tasks =
            listOf(
                toSibel("soon", "2026-10-12T09:00:00+03:00"),
                toSibel("photo", "2026-10-13T09:00:00+03:00", status = "AWAITING_APPROVAL"),
                toSibel("again", "2026-10-12T10:00:00+03:00", status = "REJECTED"),
            )
        val sent = sentTasks(tasks, Cast.EREN_ID, sibel, now, istanbul)
        assertEquals(listOf("photo", "soon", "again"), sent.map { it.taskId })
        assertEquals(listOf(true, false, false), sent.map { it.awaitingApproval })
        assertEquals(listOf(false, false, true), sent.map { it.reRequested })
    }

    @Test
    fun `tasks to self, tasks to me, and tasks to someone without a connection are not listed`() {
        val toSelf = aTask(id = "self").copy(senderId = Cast.EREN_ID)
        val toMe = aTask(id = "to-me")
        val unknown = toSibel("unknown", "2026-10-12T09:00:00+03:00")
        assertEquals(emptyList(), sentTasks(listOf(toSelf, toMe), Cast.EREN_ID, sibel, now, istanbul))
        assertEquals(emptyList(), sentTasks(listOf(unknown), Cast.EREN_ID, emptyMap(), now, istanbul))
    }
}
