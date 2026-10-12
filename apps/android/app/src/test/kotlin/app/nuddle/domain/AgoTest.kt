package app.nuddle.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class AgoTest {
    private val now = Instant.parse("2026-10-12T14:00:00+03:00")

    @Test
    fun `whole units, rounded down`() {
        assertEquals(Ago.Now, Ago.between(now - 59.seconds, now))
        assertEquals(Ago.Minutes(5), Ago.between(now - 5.minutes - 30.seconds, now))
        assertEquals(Ago.Hours(2), Ago.between(now - 2.hours - 59.minutes, now))
        assertEquals(Ago.Days(3), Ago.between(now - 3.days, now))
    }

    @Test
    fun `a time in the future is now`() {
        assertEquals(Ago.Now, Ago.between(now + 5.minutes, now))
    }
}
