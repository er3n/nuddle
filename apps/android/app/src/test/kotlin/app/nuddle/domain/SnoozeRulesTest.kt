package app.nuddle.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Snooze (docs/domain.md, Time rules; open-questions#1): the same rule as the server's `nextSnooze`. */
class SnoozeRulesTest {
    private val now = Instant.parse("2026-10-12T08:00:00+03:00")
    private val deadline = now + 1.hours

    @Test
    fun `15 minutes, then 10 as the last one, then none`() {
        assertEquals(Snooze(15, last = false), SnoozeRules.next(0, now, deadline))
        assertEquals(Snooze(10, last = true), SnoozeRules.next(1, now, deadline))
        assertNull(SnoozeRules.next(2, now, deadline))
    }

    @Test
    fun `never up to or past the deadline`() {
        assertNull(SnoozeRules.next(0, now, now + 15.minutes))
        assertEquals(Snooze(15, last = false), SnoozeRules.next(0, now, now + 16.minutes))
        assertNull(SnoozeRules.next(1, now, now + 10.minutes))
    }
}
