package app.nuddle.domain

import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * The recipient's quiet hours (docs/domain.md): no poke rings inside the window, full screen included. The server
 * plans around them; the device checks again right before ringing. The window may cross midnight (23:00-07:00).
 */
data class QuietHours(
    val start: LocalTime,
    val end: LocalTime,
    val timeZone: TimeZone,
) {
    fun contains(instant: Instant): Boolean {
        val time = instant.toLocalDateTime(timeZone).time
        return when {
            start == end -> false
            start < end -> time >= start && time < end
            else -> time >= start || time < end
        }
    }

    companion object {
        private const val DEFAULT_START_HOUR = 23
        private const val DEFAULT_END_HOUR = 7

        /** Default 23:00-07:00 (docs/domain.md) until the first sync brings the person's own settings. */
        fun default(timeZone: TimeZone): QuietHours = QuietHours(LocalTime(DEFAULT_START_HOUR, 0), LocalTime(DEFAULT_END_HOUR, 0), timeZone)

        /** From the API's `Me.quietHours` ("23:00" or "23:00:00" plus an IANA zone). */
        fun parse(
            start: String,
            end: String,
            timeZone: String,
        ): QuietHours = QuietHours(LocalTime.parse(start), LocalTime.parse(end), TimeZone.of(timeZone))
    }
}
