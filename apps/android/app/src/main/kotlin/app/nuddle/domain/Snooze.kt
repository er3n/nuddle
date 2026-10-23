package app.nuddle.domain

import kotlin.time.Duration.Companion.milliseconds
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
 * open-questions#1 (NUD-63): 2 snoozes, 15 then 10 minutes, shortened so 5 minutes remain before the deadline and so
 * it ends before quiet hours (NUD-163), never under 5 minutes.
 */
object SnoozeRules {
    private val LENGTHS_MINUTES = listOf(15, 10)
    private const val AFTER_SNOOZE_MINUTES = 5
    private const val MIN_SNOOZE_MINUTES = 5

    /**
     * Null when none is left or too little time is: Ertele is not offered. A snooze also ends before [quietStart], the
     * next start of quiet hours where it could not ring (NUD-163); see [quietStart].
     */
    fun next(
        used: Int,
        now: Instant,
        deadline: Instant,
        quietStart: Instant? = null,
    ): Snooze? {
        val planned = LENGTHS_MINUTES.getOrNull(used) ?: return null
        val room = (deadline - now - AFTER_SNOOZE_MINUTES.minutes).inWholeMinutes.toInt()
        val beforeQuiet = quietStart?.let { (it - now - 1.milliseconds).inWholeMinutes.toInt() } ?: Int.MAX_VALUE
        val minutes = minOf(planned, room, beforeQuiet)
        if (minutes < MIN_SNOOZE_MINUTES) return null
        return Snooze(minutes, now + minutes.minutes, last = used + 1 == LENGTHS_MINUTES.size)
    }

    /**
     * The quiet hours a snooze must end before (NUD-163): their next start, unless the task is an `at` task, whose
     * pokes ring in its own window (open-questions#47), or quiet hours already hold. Null: no such limit.
     */
    fun quietStart(
        quietHours: QuietHours?,
        moment: Boolean,
        now: Instant,
    ): Instant? = quietHours?.takeUnless { moment || it.contains(now) }?.nextStart(now)
}
