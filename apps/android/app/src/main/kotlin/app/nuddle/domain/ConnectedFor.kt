package app.nuddle.domain

import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.periodUntil
import kotlin.time.Instant

/** How long two people have been connected: "3 aydır bağlısınız" (5b, open-questions#37). Whole units, rounded down. */
sealed interface ConnectedFor {
    data object Today : ConnectedFor

    data class Days(
        val value: Int,
    ) : ConnectedFor

    data class Months(
        val value: Int,
    ) : ConnectedFor

    data class Years(
        val value: Int,
    ) : ConnectedFor

    companion object {
        /** Calendar units in the person's time zone, so a month is a month. A time in the future (clock skew) is today. */
        fun between(
            since: Instant,
            now: Instant,
            timeZone: TimeZone,
        ): ConnectedFor {
            if (since >= now) return Today
            val period: DateTimePeriod = since.periodUntil(now, timeZone)
            return when {
                period.years > 0 -> Years(period.years)
                period.months > 0 -> Months(period.months)
                period.days > 0 -> Days(period.days)
                else -> Today
            }
        }
    }
}
