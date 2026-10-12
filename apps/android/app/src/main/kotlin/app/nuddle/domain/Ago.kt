package app.nuddle.domain

import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** How long ago something happened, as the bell says it ("2 sa önce", 1c). */
sealed interface Ago {
    data object Now : Ago

    data class Minutes(
        val value: Int,
    ) : Ago

    data class Hours(
        val value: Int,
    ) : Ago

    data class Days(
        val value: Int,
    ) : Ago

    companion object {
        /** Under a minute is "şimdi"; whole units, rounded down. A time in the future (clock skew) is "şimdi". */
        fun between(
            then: Instant,
            now: Instant,
        ): Ago {
            val elapsed = now - then
            return when {
                elapsed < 1.minutes -> Now
                elapsed < 1.hours -> Minutes(elapsed.inWholeMinutes.toInt())
                elapsed < 1.days -> Hours(elapsed.inWholeHours.toInt())
                else -> Days(elapsed.inWholeDays.toInt())
            }
        }
    }
}
