package app.nuddle.domain

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** The snooze Ertele would give now: its length, when it rings again, and whether it is the last one ("son hakkın"). */
data class Snooze(
    val minutes: Int,
    val until: Instant,
    val last: Boolean,
)

/**
 * The same rule the server applies (task-machine `nextSnooze`), so the device can label Ertele before it asks and
 * ring at the right time even offline. The server still decides: it refuses a snooze it does not allow.
 * open-questions#1 (NUD-63): 2 snoozes, 15 then 10 minutes, shortened so 5 minutes remain before the deadline, never
 * under 5 minutes.
 */
object SnoozeRules {
    private val LENGTHS_MINUTES = listOf(15, 10)
    private const val AFTER_SNOOZE_MINUTES = 5
    private const val MIN_SNOOZE_MINUTES = 5

    /** Null when none is left or too little time is: Ertele is not offered. */
    fun next(
        used: Int,
        now: Instant,
        deadline: Instant,
    ): Snooze? {
        val planned = LENGTHS_MINUTES.getOrNull(used) ?: return null
        val room = (deadline - now - AFTER_SNOOZE_MINUTES.minutes).inWholeMinutes.toInt()
        val minutes = minOf(planned, room)
        if (minutes < MIN_SNOOZE_MINUTES) return null
        return Snooze(minutes, now + minutes.minutes, last = used + 1 == LENGTHS_MINUTES.size)
    }
}
