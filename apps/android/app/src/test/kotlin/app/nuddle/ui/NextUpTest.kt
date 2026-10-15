package app.nuddle.ui

import app.nuddle.testing.Cast
import app.nuddle.testing.aStep
import app.nuddle.testing.aTask
import app.nuddle.testing.sibelConnection
import app.nuddle.ui.screens.home.DeadlineText
import app.nuddle.ui.screens.home.FromUi
import app.nuddle.ui.screens.home.NextUpStatus
import app.nuddle.ui.screens.home.WrittenText
import app.nuddle.ui.screens.home.deadlineText
import app.nuddle.ui.screens.home.minutesLeft
import app.nuddle.ui.screens.home.nextUp
import app.nuddle.ui.screens.home.writtenText
import app.nuddle.ui.screens.task.pokeStart
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
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
    fun `an at task shows and sorts by its moment, not by the deadline after its grace (ADR 0017)`() {
        // "2 dakika sonra su iç": moment 20:02, missed at 20:32.
        val water =
            selfTask("water", "2026-10-11T20:32:00+03:00")
                .copy(remindAt = Instant.parse("2026-10-11T20:02:00+03:00").toEpochMilliseconds())
        val bill = selfTask("bill", "2026-10-11T20:20:00+03:00")
        val card = nextUp(listOf(bill, water), Cast.EREN_ID, emptyMap(), now, istanbul)
        assertEquals("water", card?.taskId)
        assertEquals(DeadlineText.Today("20:02", moment = true, minutesLeft = 2), card?.deadline)
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
        assertEquals(FromUi("Sibel", WrittenText.Today("14:32"), Cast.SIBEL_PHONE, Cast.SIBEL_ID), card?.from)
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

    @Test
    fun `the countdown covers the last hour only, rounded up to whole minutes (NUD-68)`() {
        val at = Instant.parse("2026-10-11T20:00:00+03:00")
        assertNull(minutesLeft(at, at), "due now: no countdown")
        assertNull(minutesLeft(at, at + 1.seconds), "passed")
        assertEquals(1, minutesLeft(at, at - 10.seconds))
        assertEquals(35, minutesLeft(at, at - 34.minutes - 30.seconds))
        assertEquals(60, minutesLeft(at, at - 60.minutes))
        assertNull(minutesLeft(at, at - 61.minutes))
    }

    @Test
    fun `poking starts at the first step of the current plan, while none rang and it is ahead (NUD-68)`() {
        val now = Instant.parse("2026-10-12T07:00:00+03:00")
        val plan =
            listOf(
                aStep(1, "2026-10-12T08:30:00+03:00"),
                aStep(0, "2026-10-12T08:00:00+03:00"),
                aStep(0, "2026-10-12T06:00:00+03:00", planVersion = 0),
            )
        assertEquals(Instant.parse("2026-10-12T08:00:00+03:00"), pokeStart(plan, 1, now))
        assertNull(pokeStart(plan.map { if (it.seq == 0) it.copy(firedAt = 1) else it }, 1, now), "it rang already")
        assertNull(pokeStart(plan, 1, Instant.parse("2026-10-12T08:10:00+03:00")), "the first step is behind")
        assertNull(pokeStart(emptyList(), 1, now))
    }
}
