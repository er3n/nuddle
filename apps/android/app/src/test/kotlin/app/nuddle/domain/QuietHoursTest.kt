package app.nuddle.domain

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class QuietHoursTest {
    private val istanbul = TimeZone.of("Europe/Istanbul")
    private val defaults = QuietHours.default(istanbul)

    private fun at(time: String) = Instant.parse("2026-10-12T$time+03:00")

    @Test
    fun `the next start is today before it, tomorrow after it, none without a window (NUD-163)`() {
        assertEquals(Instant.parse("2026-10-11T23:00:00+03:00"), defaults.nextStart(Instant.parse("2026-10-11T22:50:00+03:00")))
        assertEquals(Instant.parse("2026-10-12T23:00:00+03:00"), defaults.nextStart(Instant.parse("2026-10-12T02:00:00+03:00")))
        assertEquals(Instant.parse("2026-10-12T23:00:00+03:00"), defaults.nextStart(Instant.parse("2026-10-11T23:00:00+03:00")))
        assertNull(defaults.copy(end = defaults.start).nextStart(Instant.parse("2026-10-11T22:50:00+03:00")))
    }

    @Test
    fun `the default window crosses midnight`() {
        assertTrue(defaults.contains(at("23:00:00")))
        assertTrue(defaults.contains(at("03:00:00")))
        assertTrue(defaults.contains(at("06:59:59")))
        assertFalse(defaults.contains(at("07:00:00")))
        assertFalse(defaults.contains(at("22:59:59")))
    }

    @Test
    fun `the window is read in the person's time zone`() {
        // 21:30 UTC is 00:30 in Istanbul.
        assertTrue(defaults.contains(Instant.parse("2026-10-12T21:30:00Z")))
        val london = QuietHours.default(TimeZone.of("Europe/London"))
        assertFalse(london.contains(Instant.parse("2026-10-12T21:30:00Z")))
    }

    @Test
    fun `a same day window and the API format parse`() {
        val nap = QuietHours.parse("13:00:00", "15:00", "Europe/Istanbul")
        assertTrue(nap.contains(at("14:00:00")))
        assertFalse(nap.contains(at("15:00:00")))
    }

    @Test
    fun `an empty window never mutes`() {
        assertFalse(QuietHours.parse("07:00", "07:00", "Europe/Istanbul").contains(at("07:00:00")))
    }
}
