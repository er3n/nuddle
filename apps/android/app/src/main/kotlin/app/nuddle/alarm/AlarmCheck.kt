package app.nuddle.alarm

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.nuddle.data.db.PokeStepDao
import app.nuddle.data.db.TaskDao
import app.nuddle.data.outbox.reportingFailures
import app.nuddle.data.session.MissedRingStore
import app.nuddle.data.session.SessionStore
import app.nuddle.domain.AlarmScheduler
import app.nuddle.domain.Diagnostics
import app.nuddle.domain.MissedRingRules
import app.nuddle.domain.PokeRules
import app.nuddle.domain.QuietHours
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.datetime.TimeZone
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Instant

/** A phone dropped alarms it should have had; sent as a non-fatal so the crash report shows how often (ADR 0023). */
class AlarmsMissing(
    missing: Int,
    lost: Boolean,
) : IllegalStateException("alarm check: $missing missing, lost=$lost")

/** What one hourly check found. */
data class AlarmCheckResult(
    /** Alarms that should have been set and were not; set again. */
    val reinstalled: Int,
    /** The newest step that came due since the last check without an alarm and would have rung. */
    val lostAt: Instant?,
    /** Whether any poke is still armed; the job stops itself when none is. */
    val armed: Boolean,
)

/**
 * The hourly check (docs/ux/platform.md, Alarms and pokes; NUD-125): compares the alarms this phone should have with
 * the ones AlarmManager really holds, sets the missing ones again and reports it. A step that came due without its
 * alarm feeds missed-ring detection like a force stop does (2g).
 */
class AlarmCheck
    @Inject
    constructor(
        private val alarms: PokeAlarms,
        private val tasks: TaskDao,
        private val steps: PokeStepDao,
        private val scheduler: AlarmScheduler,
        private val missedRings: MissedRingStore,
        private val sessions: SessionStore,
        private val clock: Clock,
        private val timeZone: TimeZone,
        private val diagnostics: Diagnostics = Diagnostics.None,
    ) {
        suspend fun run(): AlarmCheckResult {
            val now = clock.now()
            val since = missedRings.lastAlarmCheck()
            missedRings.markAlarmCheck(now)
            val toArm = alarms.toArm()
            val missing = toArm.count { !scheduler.isScheduled(it.key) }
            val lostAt = since?.let { lostSince(it, now) }
            lostAt?.let { missedRings.lost(it) }
            diagnostics.log("alarm check armed=${toArm.size} missing=$missing lost=${lostAt != null}")
            if (missing > 0 || lostAt != null) diagnostics.record(AlarmsMissing(missing, lostAt != null))
            // The whole set again, not only the missing ones: it also refreshes the direct boot copy and the job itself.
            if (missing > 0) alarms.rearm() else alarms.remember(toArm)
            return AlarmCheckResult(missing, lostAt, toArm.isNotEmpty())
        }

        private suspend fun lostSince(
            since: Instant,
            now: Instant,
        ): Instant? {
            val all = steps.all()
            val unarmed = all.map { it.toStep() }.filterNot { scheduler.isScheduled(it.key) }
            return MissedRingRules.lostWhileStopped(
                steps = unarmed,
                tasks = tasks.all().associate { it.id to it.toLocalState() },
                fired = all.filter { it.firedAt != null }.map { it.toStep().key }.toSet(),
                stoppedAt = since,
                now = now,
                quietHours = sessions.quietHours() ?: QuietHours.default(timeZone),
                arrivedTooLate =
                    all
                        .filter { PokeRules.arrivedTooLate(it.toStep(), it.receivedAt?.let(Instant::fromEpochMilliseconds)) }
                        .map { it.toStep().key }
                        .toSet(),
            )
        }
    }

@HiltWorker
class AlarmCheckWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val check: AlarmCheck,
        private val diagnostics: Diagnostics,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result =
            diagnostics.reportingFailures("alarm check") {
                check.run()
                Result.success()
            }
    }
