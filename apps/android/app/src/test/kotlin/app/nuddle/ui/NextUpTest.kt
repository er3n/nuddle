package app.nuddle.ui

import app.nuddle.testing.Cast
import app.nuddle.testing.aTask
import app.nuddle.testing.sibelConnection
import app.nuddle.ui.screens.home.DeadlineText
import app.nuddle.ui.screens.home.FromUi
import app.nuddle.ui.screens.home.NextUpStatus
import app.nuddle.ui.screens.home.WrittenText
import app.nuddle.ui.screens.home.deadlineText
import app.nuddle.ui.screens.home.nextUp
import app.nuddle.ui.screens.home.writtenText
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The "Sıradaki" card (docs/ux/screens.md 2f, 4c): which task it shows and how the deadline reads. */
class NextUpTest {
    private val istanbul = TimeZone.of("Europe/Istanbul")
    private val now = Instant.parse("2026-10-11T20:00:00+03:00")

    private fun selfTask(
        id: String,
        deadline: String,
        status: String = "SCHEDULED",
    ) = aTask(id = id, status = status, deadline = Instant.parse(deadline)).copy(senderId = Cast.EREN_ID)

    @Test
    fun `shows the open task to self that is due first`() {
        val tasks =
            listOf(
                selfTask("later", "2026-10-13T09:00:00+03:00"),
                selfTask("first", "2026-10-12T09:00:00+03:00"),
                selfTask("done", "2026-10-11T21:00:00+03:00", status = "DONE"),
            )
        val card = nextUp(tasks, Cast.EREN_ID, emptyMap(), now, istanbul)
        assertEquals("first", card?.taskId)
        assertEquals("Temizlikçi için para çek", card?.title)
        assertEquals("1.000 TL", card?.detail)
        assertEquals(DeadlineText.Tomorrow("09:00"), card?.deadline)
        assertTrue(card?.photo == true)
    }

    @Test
    fun `closed tasks and tasks off my list are not next up`() {
        val dropped = selfTask("dropped", "2026-10-12T09:00:00+03:00", status = "DROPPED")
        val hidden = selfTask("hidden", "2026-10-12T09:00:00+03:00").copy(inRecipientList = false)
        assertNull(nextUp(listOf(dropped, hidden), Cast.EREN_ID, emptyMap(), now, istanbul))
    }

    @Test
    fun `a task from Sibel is next up with her name, when she wrote it and her phone`() {
        val fromSibel =
            aTask(id = "from-sibel", deadline = Instant.parse("2026-10-12T09:00:00+03:00"))
                .copy(createdAt = Instant.parse("2026-10-11T14:32:00+03:00").toEpochMilliseconds())
        val later = selfTask("later", "2026-10-13T09:00:00+03:00")
        val card = nextUp(listOf(later, fromSibel), Cast.EREN_ID, mapOf(Cast.SIBEL_ID to sibelConnection()), now, istanbul)
        assertEquals("from-sibel", card?.taskId)
        assertEquals(FromUi("Sibel", WrittenText.Today("14:32"), Cast.SIBEL_PHONE), card?.from)
    }

    @Test
    fun `a task from someone sync has no connection for is left out, and a task to self has no sender`() {
        assertNull(nextUp(listOf(aTask()), Cast.EREN_ID, emptyMap(), now, istanbul))
        val self = selfTask("self", "2026-10-12T09:00:00+03:00")
        assertNull(nextUp(listOf(self), Cast.EREN_ID, mapOf(Cast.SIBEL_ID to sibelConnection()), now, istanbul)?.from)
    }

    @Test
    fun `a task Sibel wrote to herself is not on my card`() {
        val hers = aTask().copy(recipientId = Cast.SIBEL_ID)
        assertNull(nextUp(listOf(hers), Cast.EREN_ID, mapOf(Cast.SIBEL_ID to sibelConnection()), now, istanbul))
    }

    @Test
    fun `a photo waiting for Sibel and a re-request are on the card, but what I still have to do comes first`() {
        val sibel = mapOf(Cast.SIBEL_ID to sibelConnection())
        val waiting =
            aTask(id = "waiting", status = "AWAITING_APPROVAL", deadline = Instant.parse("2026-10-12T08:00:00+03:00"))
                .copy(photoSentAt = Instant.parse("2026-10-11T19:24:00+03:00").toEpochMilliseconds())
        val asked = aTask(id = "asked", status = "REJECTED").copy(reRequestNote = "1.500 lazımdı")

        val alone = nextUp(listOf(waiting), Cast.EREN_ID, sibel, now, istanbul)
        assertEquals(NextUpStatus.AwaitingApproval to "19:24", alone?.status to alone?.photoSentAt)

        val both = nextUp(listOf(waiting, asked), Cast.EREN_ID, sibel, now, istanbul)
        assertEquals("asked", both?.taskId)
        assertEquals(NextUpStatus.ReRequested to "1.500 lazımdı", both?.status to both?.note)
    }

    @Test
    fun `when it was written reads as the time today, Dün, or a date`() {
        assertEquals(WrittenText.Today("00:05"), writtenText(Instant.parse("2026-10-11T00:05:00+03:00"), now, istanbul))
        assertEquals(WrittenText.Yesterday("23:50"), writtenText(Instant.parse("2026-10-10T23:50:00+03:00"), now, istanbul))
        assertEquals(WrittenText.OnDate(9, 10, "08:00"), writtenText(Instant.parse("2026-10-09T08:00:00+03:00"), now, istanbul))
    }

    @Test
    fun `a done task is not a photo task`() {
        val task = selfTask("t", "2026-10-12T09:00:00+03:00").copy(finishMode = "done")
        assertFalse(nextUp(listOf(task), Cast.EREN_ID, emptyMap(), now, istanbul)?.photo ?: true)
    }

    @Test
    fun `deadline reads as today, tomorrow or a date in the person's time zone`() {
        assertEquals(DeadlineText.Today("23:30"), deadlineText(Instant.parse("2026-10-11T23:30:00+03:00"), now, istanbul))
        assertEquals(DeadlineText.Tomorrow("00:15"), deadlineText(Instant.parse("2026-10-12T00:15:00+03:00"), now, istanbul))
        assertEquals(DeadlineText.OnDate(14, 10, "09:00"), deadlineText(Instant.parse("2026-10-14T09:00:00+03:00"), now, istanbul))
        // 22:30 UTC on the 11th is already the 12th in Istanbul.
        assertEquals(DeadlineText.Tomorrow("01:30"), deadlineText(Instant.parse("2026-10-11T22:30:00Z"), now, istanbul))
    }
}
