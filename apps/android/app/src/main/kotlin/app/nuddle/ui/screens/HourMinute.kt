package app.nuddle.ui.screens

import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

private val HOUR_MINUTE =
    LocalTime.Format {
        hour()
        char(':')
        minute()
    }

/** "21:15" in [timeZone]: the end of the sign-in code wait (6c). Times take no suffix (open-questions#21). */
fun Instant.hourMinute(timeZone: TimeZone): String = toLocalDateTime(timeZone).time.format(HOUR_MINUTE)
