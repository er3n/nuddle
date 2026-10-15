package app.nuddle.ui

import app.nuddle.ui.screens.home.DeadlineChoice
import app.nuddle.ui.screens.home.firstPokeAt
import app.nuddle.ui.screens.home.nextOccurrence
import app.nuddle.ui.screens.home.quickDeadlines
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** The deadline answers in the preview (docs/ux/screens.md 2b, open-questions#28). */
class PreviewTest {
    private val istanbul = TimeZone.of("Europe/Istanbul")

    @Test
    fun `this evening is offered only while it is ahead`() {
        assertEquals(
            listOf(
                DeadlineChoice.TonightEvening(Instant.parse("2026-10-11T19:00:00+03:00")),
                DeadlineChoice.TomorrowMorning(Instant.parse("2026-10-12T09:00:00+03:00")),
            ),
            quickDeadlines(Instant.parse("2026-10-11T18:59:00+03:00"), istanbul),
        )
        assertEquals(
            listOf(DeadlineChoice.TomorrowMorning(Instant.parse("2026-10-12T09:00:00+03:00"))),
            quickDeadlines(Instant.parse("2026-10-11T19:00:00+03:00"), istanbul),
        )
    }

    @Test
    fun `a picked time is today if still ahead, otherwise tomorrow`() {
        val now = Instant.parse("2026-10-11T14:00:00+03:00")
        assertEquals(Instant.parse("2026-10-11T16:30:00+03:00"), nextOccurrence(LocalTime(16, 30), now, istanbul))
        assertEquals(Instant.parse("2026-10-12T14:00:00+03:00"), nextOccurrence(LocalTime(14, 0), now, istanbul))
        assertEquals(Instant.parse("2026-10-12T08:00:00+03:00"), nextOccurrence(LocalTime(8, 0), now, istanbul))
    }

    @Test
    fun `a moment with a lead says when the pokes begin, a deadline or no lead says nothing (NUD-82)`() {
        val pickup = Instant.parse("2026-10-12T17:30:00+03:00")
        val istanbul = TimeZone.of("Europe/Istanbul")
        assertEquals("17:10", firstPokeAt(pickup, isMoment = true, leadMinutes = 20, istanbul))
        assertEquals(null, firstPokeAt(pickup, isMoment = true, leadMinutes = 0, istanbul))
        assertEquals(null, firstPokeAt(pickup, isMoment = true, leadMinutes = null, istanbul))
        assertEquals(null, firstPokeAt(pickup, isMoment = false, leadMinutes = 20, istanbul))
    }
}
