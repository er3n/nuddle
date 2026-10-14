package app.nuddle.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** open-questions#1 (NUD-63): 15 then 10 minutes, 5 minutes left before the deadline, never under 5. */
class SnoozeRulesTest {
    private val now = Instant.parse("2026-10-11T19:40:00Z")

    @Test
    fun `15 minutes, then 10 as the last one, then none`() {
        val deadline = now + 2.minutes * 60
        assertEquals(Snooze(15, now + 15.minutes, last = false), SnoozeRules.next(0, now, deadline))
        assertEquals(Snooze(10, now + 10.minutes, last = true), SnoozeRules.next(1, now, deadline))
        assertNull(SnoozeRules.next(2, now, deadline))
    }

    @Test
    fun `shortened so 5 minutes remain before the deadline, and none under 5 minutes`() {
        assertEquals(Snooze(9, now + 9.minutes, last = false), SnoozeRules.next(0, now, now + 14.minutes + 30.seconds))
        assertEquals(Snooze(15, now + 15.minutes, last = false), SnoozeRules.next(0, now, now + 20.minutes))
        assertEquals(Snooze(5, now + 5.minutes, last = true), SnoozeRules.next(1, now, now + 10.minutes))
        assertNull(SnoozeRules.next(0, now, now + 9.minutes + 59.seconds))
    }
}
