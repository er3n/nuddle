package app.nuddle.domain

import kotlin.time.Instant

/**
 * A step armed on this phone, as kept in device-protected storage for direct boot (docs/ux/platform.md, Alarms and
 * pokes; NUD-125). Only what the alarm path needs before the first unlock: no title, detail, name or phone number.
 */
data class LockedStep(
    val step: PokeStep,
    /** An `at` task rings inside quiet hours too (open-questions#47). */
    val moment: Boolean,
)

/** Everything the phone can read about its pokes after a reboot, before the first unlock. */
data class LockedPlan(
    val steps: List<LockedStep> = emptyList(),
    /** Null until the first sync brought the person's own; the default window applies then. */
    val quietHours: QuietHours? = null,
)

/** A poke that rang before the first unlock; handed over to Room and the outbox after it. */
data class LockedFire(
    val key: AlarmKey,
    val firedAt: Instant,
    val level: PokeLevel,
)

/** Device execution before the first unlock (docs/ux/platform.md, Direct boot). Pure: time and state are passed in. */
object DirectBootRules {
    /**
     * The stored steps that need an alarm again after a reboot: not rung yet before the unlock and not more than
     * [PokeRules.LATE_GRACE] in the past, like a re-arm from Room.
     */
    fun stepsToArm(
        plan: LockedPlan,
        fired: Set<AlarmKey>,
        now: Instant,
    ): List<PokeStep> {
        val steps = plan.steps.map { it.step }
        val open = steps.associate { it.taskId to LocalTaskState(currentPlanVersion = it.planVersion, open = true) }
        return PokeRules.stepsToArm(steps, open, fired, now)
    }

    /**
     * Ring a stored step before the first unlock: quiet hours are checked as usual. The full screen poke needs the task
     * from Room, so `FULL_SCREEN` rings as `SOUND`; that is the lock, not a missing permission.
     */
    fun decideFire(
        locked: LockedStep,
        quietHours: QuietHours,
        now: Instant,
    ): FireDecision {
        val step = locked.step
        val task = LocalTaskState(currentPlanVersion = step.planVersion, open = true, moment = locked.moment)
        return when (val decision = PokeRules.decideFire(step, task, quietHours, now, canUseFullScreen = false)) {
            is FireDecision.Ring -> FireDecision.Ring(decision.level, permissionMissing = false)
            is FireDecision.Skip -> decision
        }
    }
}
