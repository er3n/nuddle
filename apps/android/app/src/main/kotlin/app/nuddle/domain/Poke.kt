package app.nuddle.domain

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Device levels of a poke plan (docs/domain.md). `HUMAN` steps never reach the device. */
enum class PokeLevel { SILENT, SOUND, FULL_SCREEN }

/** Identifies one alarm: a step of one plan version of one task. */
data class AlarmKey(
    val taskId: String,
    val planVersion: Int,
    val seq: Int,
)

data class PokeStep(
    val taskId: String,
    val planVersion: Int,
    val seq: Int,
    val plannedAt: Instant,
    val level: PokeLevel,
) {
    val key: AlarmKey get() = AlarmKey(taskId, planVersion, seq)

    /** The number shown as "N. dürtme" (screens 3a, 3b). The server's `seq` is already 1-based. */
    val number: Int get() = seq
}

/** What the task looks like on the device when an alarm fires. */
data class LocalTaskState(
    val currentPlanVersion: Int,
    val open: Boolean,
    /** An `at` task: the sender asked for that time, so its pokes ring inside quiet hours too (open-questions#47). */
    val moment: Boolean = false,
)

sealed interface FireDecision {
    data class Ring(
        val level: PokeLevel,
        val permissionMissing: Boolean,
    ) : FireDecision

    data class Skip(
        val reason: SkipReason,
    ) : FireDecision
}

enum class SkipReason { UNKNOWN_TASK, STALE_PLAN, TASK_CLOSED, QUIET_HOURS }

/** Device execution rules (docs/domain.md, Device execution; ADR 0005). Pure: time and state are passed in. */
object PokeRules {
    /**
     * Right before ringing: ignore steps of an older plan version and of closed tasks, re-check quiet hours, and
     * degrade `FULL_SCREEN` to `SOUND` (reported as permission missing) when full screen is not allowed.
     */
    fun decideFire(
        step: PokeStep,
        task: LocalTaskState?,
        quietHours: QuietHours,
        now: Instant,
        canUseFullScreen: Boolean,
    ): FireDecision =
        when {
            task == null -> {
                FireDecision.Skip(SkipReason.UNKNOWN_TASK)
            }

            step.planVersion != task.currentPlanVersion -> {
                FireDecision.Skip(SkipReason.STALE_PLAN)
            }

            !task.open -> {
                FireDecision.Skip(SkipReason.TASK_CLOSED)
            }

            quietHours.contains(now) && !task.moment -> {
                FireDecision.Skip(SkipReason.QUIET_HOURS)
            }

            step.level == PokeLevel.FULL_SCREEN && !canUseFullScreen -> {
                FireDecision.Ring(
                    PokeLevel.SOUND,
                    permissionMissing = true,
                )
            }

            else -> {
                FireDecision.Ring(step.level, permissionMissing = false)
            }
        }

    /**
     * Steps that need an alarm: current plan version, open task, not fired yet, and not more than [LATE_GRACE] in
     * the past. Used after a sync and on boot, package replace, time and time zone changes. A step that arrives a
     * little late (the first step of a plan is planned at "now") rings at once; an older one is not rung late, and
     * the server's lost poke check covers it (docs/domain.md, Device execution).
     */
    fun stepsToArm(
        steps: List<PokeStep>,
        tasks: Map<String, LocalTaskState>,
        fired: Set<AlarmKey>,
        now: Instant,
    ): List<PokeStep> =
        steps
            .filter { step ->
                val task = tasks[step.taskId]
                task != null && task.open && step.planVersion == task.currentPlanVersion && step.key !in fired &&
                    step.plannedAt > now - LATE_GRACE
            }.sortedBy { it.plannedAt }

    /** Rounded time to the deadline for the poke text ("son zamana 45 dk", "1 saat"). Never negative. */
    fun timeLeft(
        now: Instant,
        deadline: Instant,
    ): TimeLeft {
        val left = (deadline - now).coerceAtLeast(Duration.ZERO)
        return if (left.inWholeMinutes < MINUTES_PER_HOUR) {
            TimeLeft.Minutes(left.inWholeMinutes.toInt())
        } else {
            TimeLeft.Hours(left.inWholeHours.toInt())
        }
    }

    private const val MINUTES_PER_HOUR = 60

    /** How late a step may still ring; under the server's 5 minute lost poke grace (docs/domain.md). */
    val LATE_GRACE = 2.minutes
}

sealed interface TimeLeft {
    data class Minutes(
        val value: Int,
    ) : TimeLeft

    data class Hours(
        val value: Int,
    ) : TimeLeft
}
