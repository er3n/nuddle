package app.nuddle.domain

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ConnectedForTest {
    private val istanbul = TimeZone.of("Europe/Istanbul")
    private val now = Instant.parse("2026-10-12T15:00:00+03:00")

    private fun since(at: String) = ConnectedFor.between(Instant.parse(at), now, istanbul)

    @Test
    fun `counts whole calendar units, rounded down`() {
        assertEquals(ConnectedFor.Today, since("2026-10-12T09:00:00+03:00"))
        assertEquals(ConnectedFor.Today, since("2026-10-11T16:00:00+03:00"))
        assertEquals(ConnectedFor.Days(1), since("2026-10-11T15:00:00+03:00"))
        assertEquals(ConnectedFor.Days(29), since("2026-09-13T15:00:00+03:00"))
        assertEquals(ConnectedFor.Months(1), since("2026-09-12T15:00:00+03:00"))
        assertEquals(ConnectedFor.Months(3), since("2026-07-11T14:32:00+03:00"))
        assertEquals(ConnectedFor.Months(11), since("2025-10-13T15:00:00+03:00"))
        assertEquals(ConnectedFor.Years(1), since("2025-10-12T15:00:00+03:00"))
    }

    @Test
    fun `a time in the future is today`() {
        assertEquals(ConnectedFor.Today, since("2026-10-12T15:05:00+03:00"))
    }
}
