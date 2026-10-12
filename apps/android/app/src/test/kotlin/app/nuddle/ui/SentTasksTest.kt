package app.nuddle.ui

import app.nuddle.testing.Cast
import app.nuddle.testing.aTask
import app.nuddle.testing.selinConnection
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
    private val selin = mapOf(Cast.SELIN_ID to selinConnection())

    private fun toSelin(
        id: String,
        deadline: String,
        status: String = "SCHEDULED",
    ) = aTask(id = id, status = status, deadline = Instant.parse(deadline)).copy(senderId = Cast.EREN_ID, recipientId = Cast.SELIN_ID)

    @Test
    fun `my open tasks to others, due first on top, with who they are for`() {
        val tasks =
            listOf(
                toSelin("later", "2026-10-13T09:00:00+03:00"),
                toSelin("first", "2026-10-12T09:00:00+03:00"),
                toSelin("cancelled", "2026-10-12T08:00:00+03:00", status = "CANCELLED"),
            )
        val sent = sentTasks(tasks, Cast.EREN_ID, selin, now, istanbul)
        assertEquals(listOf("first", "later"), sent.map { it.taskId })
        assertEquals("Selin" to Cast.SELIN_ID, sent.first().toName to sent.first().toId)
        assertEquals(DeadlineText.Tomorrow("09:00"), sent.first().deadline)
    }

    @Test
    fun `a photo waiting for my approval comes first, and a re-requested task stays listed`() {
        val tasks =
            listOf(
                toSelin("soon", "2026-10-12T09:00:00+03:00"),
                toSelin("photo", "2026-10-13T09:00:00+03:00", status = "AWAITING_APPROVAL"),
                toSelin("again", "2026-10-12T10:00:00+03:00", status = "REJECTED"),
            )
        val sent = sentTasks(tasks, Cast.EREN_ID, selin, now, istanbul)
        assertEquals(listOf("photo", "soon", "again"), sent.map { it.taskId })
        assertEquals(listOf(true, false, false), sent.map { it.awaitingApproval })
        assertEquals(listOf(false, false, true), sent.map { it.reRequested })
    }

    @Test
    fun `tasks to self, tasks to me, and tasks to someone without a connection are not listed`() {
        val toSelf = aTask(id = "self").copy(senderId = Cast.EREN_ID)
        val toMe = aTask(id = "to-me")
        val unknown = toSelin("unknown", "2026-10-12T09:00:00+03:00")
        assertEquals(emptyList(), sentTasks(listOf(toSelf, toMe), Cast.EREN_ID, selin, now, istanbul))
        assertEquals(emptyList(), sentTasks(listOf(unknown), Cast.EREN_ID, emptyMap(), now, istanbul))
    }
}
