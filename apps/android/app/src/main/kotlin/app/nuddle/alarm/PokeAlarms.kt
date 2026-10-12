package app.nuddle.alarm

import app.nuddle.data.db.PokeStepDao
import app.nuddle.data.db.PokeStepEntity
import app.nuddle.data.db.TaskDao
import app.nuddle.data.db.TaskEntity
import app.nuddle.data.sync.AlarmRearmer
import app.nuddle.data.sync.OPEN_STATUSES
import app.nuddle.domain.AlarmKey
import app.nuddle.domain.AlarmScheduler
import app.nuddle.domain.LocalTaskState
import app.nuddle.domain.PokeLevel
import app.nuddle.domain.PokeRules
import app.nuddle.domain.PokeStep
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Instant

internal fun PokeStepEntity.toStep() =
    PokeStep(taskId, planVersion, seq, Instant.fromEpochMilliseconds(plannedAt), PokeLevel.valueOf(level))

internal fun TaskEntity.toLocalState() = LocalTaskState(currentPlanVersion = planVersion, open = status in OPEN_STATUSES)

/** Keeps the alarms in AlarmManager equal to the stored plans (ADR 0005). */
@Singleton
class PokeAlarms
    @Inject
    constructor(
        private val tasks: TaskDao,
        private val steps: PokeStepDao,
        private val scheduler: AlarmScheduler,
        private val clock: Clock,
    ) : AlarmRearmer {
        override suspend fun rearm() {
            val all = steps.all()
            val states = tasks.all().associate { it.id to it.toLocalState() }
            val fired = all.filter { it.firedAt != null }.map { it.toStep().key }.toSet()
            val toArm = PokeRules.stepsToArm(all.map { it.toStep() }, states, fired, clock.now())
            val armed = toArm.map { it.key }.toSet()
            all.map { it.toStep().key }.filterNot { it in armed }.forEach(scheduler::cancel)
            toArm.forEach(scheduler::schedule)
        }

        override suspend fun applyPlan(
            taskId: String,
            version: Int,
            steps: List<PokeStepEntity>,
        ) {
            this.steps
                .forTask(taskId)
                .filter { it.planVersion != version }
                .forEach { scheduler.cancel(it.toStep().key) }
            this.steps.replacePlan(taskId, version, steps)
        }

        /** Done, Snooze and Can't today stop the task's alarms at once, even offline (docs/domain.md, step 6). */
        suspend fun stopTask(taskId: String) {
            steps.forTask(taskId).forEach { scheduler.cancel(it.toStep().key) }
        }

        /** Signing out: no alarm of this person may ring on this phone any more. */
        suspend fun stopAll() {
            steps.all().forEach { scheduler.cancel(it.toStep().key) }
        }

        suspend fun step(key: AlarmKey): PokeStep? = steps.get(key.taskId, key.planVersion, key.seq)?.toStep()
    }
