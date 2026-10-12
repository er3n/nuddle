package app.nuddle.alarm

import app.nuddle.data.db.PokeStepDao
import app.nuddle.data.db.TaskDao
import app.nuddle.data.session.MissedRingStore
import app.nuddle.data.session.SessionStore
import app.nuddle.domain.MissedRingRules
import app.nuddle.domain.QuietHours
import kotlinx.datetime.TimeZone
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Instant

/** When the app was last force stopped (`ApplicationExitInfo`, API 30+), null when the last exit was something else. */
fun interface ForceStops {
    fun last(): Instant?
}

/**
 * On launch (docs/ux/platform.md, Missed-ring detection): a force stop takes every alarm of the app with it. Pokes due
 * while the app was stopped did not ring, so the newest one is kept as lost, and the alarms are set again from Room.
 */
class MissedRingCheck
    @Inject
    constructor(
        private val forceStops: ForceStops,
        private val tasks: TaskDao,
        private val steps: PokeStepDao,
        private val alarms: PokeAlarms,
        private val store: MissedRingStore,
        private val sessions: SessionStore,
        private val clock: Clock,
        private val timeZone: TimeZone,
    ) {
        suspend fun onLaunch() {
            val stoppedAt = forceStops.last() ?: return
            val handled = store.handledStop()
            if (handled != null && handled >= stoppedAt) return
            store.markStopHandled(stoppedAt)
            val all = steps.all()
            val lost =
                MissedRingRules.lostWhileStopped(
                    steps = all.map { it.toStep() },
                    tasks = tasks.all().associate { it.id to it.toLocalState() },
                    fired = all.filter { it.firedAt != null }.map { it.toStep().key }.toSet(),
                    stoppedAt = stoppedAt,
                    now = clock.now(),
                    quietHours = sessions.quietHours() ?: QuietHours.default(timeZone),
                )
            lost?.let { store.lost(it) }
            alarms.rearm()
        }
    }
