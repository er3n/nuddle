package app.nuddle.ui

import app.nuddle.testing.Cast
import app.nuddle.testing.aTask
import app.nuddle.ui.screens.home.DeadlineText
import app.nuddle.ui.screens.home.deadlineText
import app.nuddle.ui.screens.home.nextUp
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The "Sıradaki" card (docs/ux/screens.md 2f): which task it shows and how the deadline reads. */
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
        val card = nextUp(tasks, Cast.EREN_ID, now, istanbul)
        assertEquals("first", card?.taskId)
        assertEquals("Temizlikçi için para çek", card?.title)
        assertEquals("1.000 TL", card?.detail)
        assertEquals(DeadlineText.Tomorrow("09:00"), card?.deadline)
        assertTrue(card?.photo == true)
    }

    @Test
    fun `tasks from someone else and closed tasks are not next up yet`() {
        val fromSelin = aTask(id = "from-selin")
        val dropped = selfTask("dropped", "2026-10-12T09:00:00+03:00", status = "DROPPED")
        val hidden = selfTask("hidden", "2026-10-12T09:00:00+03:00").copy(inRecipientList = false)
        assertNull(nextUp(listOf(fromSelin, dropped, hidden), Cast.EREN_ID, now, istanbul))
    }

    @Test
    fun `a done task is not a photo task`() {
        val task = selfTask("t", "2026-10-12T09:00:00+03:00").copy(finishMode = "done")
        assertFalse(nextUp(listOf(task), Cast.EREN_ID, now, istanbul)?.photo ?: true)
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
