package app.nuddle.domain

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** A heads-up is shown at most once per task and plan version (docs/domain.md, Already done?). */
data class HeadsUpKey(
    val taskId: String,
    val planVersion: Int,
)

val PokeStep.headsUpKey: HeadsUpKey get() = HeadsUpKey(taskId, planVersion)

/**
 * The quiet "Zaten yaptın mı?" before a loud step when the person is already using the phone (NUD-129, docs/domain.md,
 * Already done?). It never replaces a step: the step rings as planned whatever happens to the heads-up. Pure: time and
 * state are passed in.
 */
object HeadsUpRules {
    /** How long before a `SOUND` or `FULL_SCREEN` step the phone looks for the person (TODO(open-questions#59)). */
    val WINDOW = 10.minutes

    /** How often the phone looks inside the window when it does not hear the unlock itself. */
    val CHECK_EVERY = 2.minutes

    /** Closer than this to the step a heads-up is pointless: the step rings in a moment. */
    val MIN_AHEAD = 1.minutes

    /**
     * The steps to show a heads-up for now, one per task: the task's next armed step is `SOUND` or `FULL_SCREEN` and
     * comes within [WINDOW] (and not closer than [MIN_AHEAD]), the task is open on this plan version and is not a
     * one-time message, no heads-up was shown for this version yet, no poke of the task is up already (it has Bitti),
     * and quiet hours do not hold it back (an `at` task's own window is exempt, like its pokes).
     */
    fun due(
        armed: List<PokeStep>,
        tasks: Map<String, LocalTaskState>,
        shown: Set<HeadsUpKey>,
        showing: Set<String>,
        quietHours: QuietHours,
        now: Instant,
    ): List<PokeStep> =
        armed
            .groupBy { it.taskId }
            .mapNotNull { (taskId, steps) ->
                val next = steps.minBy { it.plannedAt }
                next.takeIf { wanted(it, tasks[taskId], shown, showing, quietHours, now) }
            }.sortedBy { it.plannedAt }

    /**
     * When the phone should next look whether the person is using it: every [CHECK_EVERY] from [WINDOW] before each
     * armed loud step until [MIN_AHEAD] before it. Null when no loud step is armed.
     */
    fun nextCheck(
        armed: List<PokeStep>,
        now: Instant,
    ): Instant? =
        armed
            .filter { it.loud }
            .flatMap { step ->
                generateSequence(step.plannedAt - WINDOW) { it + CHECK_EVERY }
                    .takeWhile { step.plannedAt - it >= MIN_AHEAD }
                    .toList()
            }.filter { it > now }
            .minOrNull()

    private fun wanted(
        next: PokeStep,
        task: LocalTaskState?,
        shown: Set<HeadsUpKey>,
        showing: Set<String>,
        quietHours: QuietHours,
        now: Instant,
    ): Boolean =
        when {
            task == null || !task.open || task.notice -> false
            next.planVersion != task.currentPlanVersion || !next.loud -> false
            !inWindow(next, now) -> false
            next.headsUpKey in shown || next.taskId in showing -> false
            else -> task.moment || !quietHours.contains(now)
        }

    private val PokeStep.loud: Boolean get() = level == PokeLevel.SOUND || level == PokeLevel.FULL_SCREEN

    private fun inWindow(
        step: PokeStep,
        now: Instant,
    ): Boolean {
        val ahead = step.plannedAt - now
        return ahead >= MIN_AHEAD && ahead <= WINDOW
    }
}
