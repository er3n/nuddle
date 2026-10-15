package app.nuddle.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import kotlin.time.Clock

/** A beat at the start of every minute, so a "35 dk kaldı" on screen counts down (NUD-68). */
fun interface MinuteTicks {
    fun ticks(): Flow<Unit>
}

/** The first beat at once, then one as each minute of [clock] begins. */
class ClockMinuteTicks
    @Inject
    constructor(
        private val clock: Clock,
    ) : MinuteTicks {
        override fun ticks(): Flow<Unit> =
            flow {
                while (true) {
                    emit(Unit)
                    delay(MINUTE_MS - clock.now().toEpochMilliseconds() % MINUTE_MS)
                }
            }

        private companion object {
            const val MINUTE_MS = 60_000L
        }
    }
