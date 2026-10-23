package app.nuddle.alarm

import app.nuddle.api.model.FinishMode
import app.nuddle.data.db.PokeStepDao
import app.nuddle.data.db.PokeStepEntity
import app.nuddle.data.db.TaskDao
import app.nuddle.data.db.TaskEntity
import app.nuddle.data.db.isMoment
import app.nuddle.data.sync.AlarmRearmer
import app.nuddle.data.sync.OPEN_STATUSES
import app.nuddle.domain.AlarmKey
import app.nuddle.domain.AlarmScheduler
import app.nuddle.domain.LocalTaskState
import app.nuddle.domain.LockedStep
import app.nuddle.domain.PokeLevel
import app.nuddle.domain.PokeRules
import app.nuddle.domain.PokeStep
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Instant

internal fun PokeStepEntity.toStep() =
    PokeStep(taskId, planVersion, seq, Instant.fromEpochMilliseconds(plannedAt), PokeLevel.valueOf(level))

internal fun TaskEntity.toLocalState() =
    LocalTaskState(
        currentPlanVersion = planVersion,
        open = status in OPEN_STATUSES,
        moment = isMoment,
        notice = finishMode == FinishMode.NOTICE.value,
        snoozedUntil = snoozedUntil?.let(Instant::fromEpochMilliseconds),
    )

/** Keeps the alarms in AlarmManager equal to the stored plans (ADR 0005). */
@Singleton
class PokeAlarms
    @Inject
    constructor(
        private val tasks: TaskDao,
        private val steps: PokeStepDao,
        private val scheduler: AlarmScheduler,
        private val clock: Clock,
        private val notifications: PokeNotifications,
        private val armedPokes: ArmedPokes = ArmedPokes.None,
    ) : AlarmRearmer {
        /** The steps that should have an alarm now; also what the hourly check compares with AlarmManager (NUD-125). */
        suspend fun toArm(): List<PokeStep> {
            val all = steps.all()
            val taskList = tasks.all()
            val states = taskList.associate { it.id to it.toLocalState() }
            val fired = all.filter { it.firedAt != null }.map { it.toStep().key }.toSet()
            val now = clock.now()
            // A snoozed ring may be a step that is also due on its own (NUD-158): the snooze's time wins.
            return (PokeRules.stepsToArm(all.map { it.toStep() }, states, fired, now) + snoozedRings(taskList, all, now))
                .associateBy { it.key }
                .values
                .toList()
        }

        /**
         * The armed set changed: keep the direct boot copy equal to it and the hourly check running while it is not
         * empty (docs/ux/platform.md, Alarms and pokes).
         */
        suspend fun remember(toArm: List<PokeStep>? = null) {
            val armed = toArm ?: toArm()
            val moments =
                tasks
                    .all()
                    .filter { it.isMoment }
                    .map { it.id }
                    .toSet()
            armedPokes.armed(armed.map { LockedStep(it, moment = it.taskId in moments) })
        }

        override suspend fun rearm() {
            val all = steps.all()
            val states = tasks.all().associate { it.id to it.toLocalState() }
            val toArm = toArm()
            val armed = toArm.map { it.key }.toSet()
            all.map { it.toStep().key }.filterNot { it in armed }.forEach(scheduler::cancel)
            toArm.forEach(scheduler::schedule)
            remember(toArm)
            // A task closed on the other phone or on the server (cancelled, missed, connection removed): the poke that
            // rang for it goes too, not only its alarms (NUD-64). Asked of the system, because the sync that closed it
            // has already replaced the steps that rang.
            (notifications.showing() + notifications.headsUps())
                .filter { states[it]?.open != true }
                .forEach(notifications::cancel)
        }

        /**
         * Ertele rings the step that rang again when the snooze ends (PokeActions.snooze). Until the server's shifted
         * plan replaces the version, a re-arm keeps that ring instead of cancelling it as a step that already fired.
         */
        private fun snoozedRings(
            taskList: List<TaskEntity>,
            all: List<PokeStepEntity>,
            now: Instant,
        ): List<PokeStep> =
            taskList
                .filter { it.status in OPEN_STATUSES && (it.snoozedUntil ?: 0) > now.toEpochMilliseconds() }
                .mapNotNull { task ->
                    all
                        .filter { it.taskId == task.id && it.planVersion == task.planVersion && it.firedAt != null }
                        .maxByOrNull { it.seq }
                        ?.toStep()
                        ?.copy(plannedAt = Instant.fromEpochMilliseconds(task.snoozedUntil ?: return@mapNotNull null))
                }

        /**
         * What Ertele on [key] rings at the snooze's end (NUD-158): the step that rang. When a sync replaced its plan since
         * (the LLM plan follows the first one within seconds, an edit), the current plan's first step yet to ring takes
         * its place and counts as rung [at], so a re-arm keeps the snoozed ring and holds the steps before its end. Null
         * when the plan has no step left.
         */
        suspend fun snoozeRing(
            key: AlarmKey,
            task: TaskEntity,
            at: Instant,
        ): PokeStep? {
            step(key)?.let { return it }
            val next =
                steps
                    .forTask(task.id)
                    .filter { it.planVersion == task.planVersion && it.firedAt == null }
                    .minByOrNull { it.seq } ?: return null
            steps.markFired(next.taskId, next.planVersion, next.seq, at.toEpochMilliseconds())
            return next.toStep()
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
            // A step already stored keeps the time it first arrived (insertIgnoringExisting).
            val now = clock.now().toEpochMilliseconds()
            this.steps.replacePlan(taskId, version, steps.map { it.copy(receivedAt = it.receivedAt ?: now) })
        }

        /** Done, Snooze and Can't today stop the task's alarms at once, even offline (docs/domain.md, step 6). */
        suspend fun stopTask(taskId: String) {
            steps.forTask(taskId).forEach { scheduler.cancel(it.toStep().key) }
            remember()
        }

        /**
         * Ertele on [key] until [until] (NUD-152): the other steps of its plan that come before the snooze ends lose their
         * alarms, so the plan does not ring during the snooze while the server's shifted plan is on its way.
         */
        suspend fun holdForSnooze(
            key: AlarmKey,
            until: Instant,
        ) {
            steps
                .forTask(key.taskId)
                .map { it.toStep() }
                .filter { it.planVersion == key.planVersion && it.seq != key.seq && it.plannedAt < until }
                .forEach { scheduler.cancel(it.key) }
        }

        /** Signing out: no alarm of this person may ring on this phone any more. */
        suspend fun stopAll() {
            steps.all().forEach { scheduler.cancel(it.toStep().key) }
            armedPokes.cleared()
        }

        suspend fun step(key: AlarmKey): PokeStep? = steps.get(key.taskId, key.planVersion, key.seq)?.toStep()
    }
