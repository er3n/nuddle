package app.nuddle.domain

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** The snooze Ertele would give now: its length, and whether it is the last one ("son hakkın"). */
data class Snooze(
    val minutes: Int,
    val last: Boolean,
)

/**
 * The same rule the server applies (task-machine `nextSnooze`), so the device can label Ertele before it asks and
 * ring at the right time even offline. The server still decides: it refuses a snooze it does not allow.
 * TODO(open-questions#1): 2 snoozes, 15 then 10 minutes, never past the deadline.
 */
object SnoozeRules {
    private val LENGTHS_MINUTES = listOf(15, 10)

    /** Null when none is left or it would run past the deadline: Ertele is not offered. */
    fun next(
        used: Int,
        now: Instant,
        deadline: Instant,
    ): Snooze? {
        val minutes = LENGTHS_MINUTES.getOrNull(used) ?: return null
        if (now + minutes.minutes >= deadline) return null
        return Snooze(minutes, last = used + 1 == LENGTHS_MINUTES.size)
    }
}
