package app.nuddle.alarm

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.nuddle.data.session.SessionStore
import app.nuddle.domain.LockedPlan
import app.nuddle.domain.LockedStep
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Starts and stops the hourly alarm check (docs/ux/platform.md, Hourly check; NUD-125). */
fun interface AlarmCheckScheduling {
    /** Runs the check about every hour while [active], and not at all otherwise. */
    fun keepChecking(active: Boolean)
}

/** What has to follow whenever the set of armed pokes changes: the direct boot copy and the hourly check. */
interface ArmedPokes {
    suspend fun armed(steps: List<LockedStep>)

    /** Signing out or a reset: no poke of this person is armed any more. */
    suspend fun cleared()

    /** Follows nothing: the default where tests build [PokeAlarms] without caring. */
    object None : ArmedPokes {
        override suspend fun armed(steps: List<LockedStep>) = Unit

        override suspend fun cleared() = Unit
    }
}

class ArmedPokesKeeper
    @Inject
    constructor(
        private val store: DirectBootStore,
        private val sessions: SessionStore,
        private val checks: AlarmCheckScheduling,
    ) : ArmedPokes {
        override suspend fun armed(steps: List<LockedStep>) {
            store.savePlan(LockedPlan(steps, sessions.quietHours()))
            checks.keepChecking(steps.isNotEmpty())
        }

        override suspend fun cleared() {
            store.clear()
            checks.keepChecking(false)
        }
    }

/** The periodic WorkManager job behind [AlarmCheckScheduling]. */
class WorkManagerAlarmChecks(
    private val workManager: WorkManager,
) : AlarmCheckScheduling {
    override fun keepChecking(active: Boolean) {
        if (active) {
            val request = PeriodicWorkRequestBuilder<AlarmCheckWorker>(CHECK_EVERY_HOURS, TimeUnit.HOURS).build()
            // KEEP: a re-arm after every sync must not push the next check further away.
            workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        } else {
            workManager.cancelUniqueWork(WORK_NAME)
        }
    }

    private companion object {
        const val WORK_NAME = "alarm-check"
        const val CHECK_EVERY_HOURS = 1L
    }
}
