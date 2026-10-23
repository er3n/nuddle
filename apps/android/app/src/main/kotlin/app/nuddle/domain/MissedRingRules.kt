package app.nuddle.domain

import kotlin.time.Instant

/**
 * Missed-ring detection on the phone (docs/ux/platform.md, Missed-ring detection; 2g, 2h). A loss is known from the
 * server (`poke_lost`) or found here after a force stop, which takes every alarm of the app with it.
 */
object MissedRingRules {
    /**
     * After a force stop at [stoppedAt]: the newest step that came due while the app was stopped and would have rung
     * (current plan, open task, not fired, outside quiet hours). Null when nothing came due. A step that was never
     * meant to ring here is no loss (NUD-162): one an Ertele held, or one in [arrivedTooLate] (it reached the phone
     * after its time).
     */
    fun lostWhileStopped(
        steps: List<PokeStep>,
        tasks: Map<String, LocalTaskState>,
        fired: Set<AlarmKey>,
        stoppedAt: Instant,
        now: Instant,
        quietHours: QuietHours,
        arrivedTooLate: Set<AlarmKey> = emptySet(),
    ): Instant? =
        steps
            .filter { step ->
                val task = tasks[step.taskId]
                task != null && task.open && step.planVersion == task.currentPlanVersion && step.key !in fired &&
                    step.plannedAt > stoppedAt && step.plannedAt <= now && !quietHours.contains(step.plannedAt) &&
                    step.key !in arrivedTooLate && !PokeRules.heldBySnooze(step, task, fired)
            }.maxOfOrNull { it.plannedAt }

    /**
     * The loss the bell warns about (2g): the newest one, until a poke rings on this phone after it or every brand
     * step was done after it (open-questions#38).
     */
    fun warning(
        lostAt: Instant?,
        rangAt: Instant?,
        fixedAt: Instant?,
    ): Instant? = lostAt?.takeIf { lost -> (rangAt == null || rangAt < lost) && (fixedAt == null || fixedAt < lost) }

    /** A brand step (2h) counts as done when the phone can tell it is, or when it was opened after the loss. */
    fun stepDone(
        checkPasses: Boolean?,
        openedAt: Instant?,
        lostAt: Instant?,
    ): Boolean = checkPasses ?: (openedAt != null && (lostAt == null || openedAt > lostAt))
}
