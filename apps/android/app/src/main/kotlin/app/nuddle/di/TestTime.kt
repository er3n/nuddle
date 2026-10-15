package app.nuddle.di

import android.content.Context
import androidx.core.content.edit
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * Fake time of the `e2e` flavor (docs/testing.md, Fake time; NUD-65): the app's clock runs [offset] ahead of the
 * phone's, and the orchestrator moves it forward together with the server's clock instead of waiting. Alarms are set
 * that much earlier on the phone's own clock ([shift]). Other builds use the system clock with no offset.
 */
object TestTime {
    private const val PREFS = "e2e_time"
    private const val KEY = "offsetMs"

    @Volatile private var offsetMs: Long? = null

    fun offset(context: Context): Long =
        offsetMs ?: context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY, 0)
            .also { offsetMs = it }

    fun set(
        context: Context,
        ms: Long,
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit(commit = true) { putLong(KEY, ms) }
        offsetMs = ms
    }

    fun clock(context: Context): Clock =
        object : Clock {
            override fun now(): Instant = Clock.System.now() + offset(context).milliseconds
        }

    /** When an alarm for [at] on the app's clock has to go off on the phone's clock. */
    fun shift(
        context: Context,
        at: Instant,
    ): Instant = at - offset(context).milliseconds
}
