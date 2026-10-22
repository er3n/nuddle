package app.nuddle.domain

/**
 * Which line of a level's poke titles a step shows (NUD-130, docs/glossary.md, Poke titles). The Android side keeps
 * the lines in one string array per level and asks for an index into it.
 *
 * Pure: the same task, plan version and step give the same line, so showing a poke again keeps its line. Within one
 * plan two pokes in a row differ in `seq` by one, so they land on neighbouring lines and never share one, also when a
 * full screen step rings as sound. A new plan shifts the start (open-questions #60).
 */
object PokeTitles {
    /** The smallest list that can rotate without repeating in a row. */
    const val MIN_TITLES = 2

    fun index(
        step: PokeStep,
        count: Int,
    ): Int {
        require(count >= MIN_TITLES) { "a level needs at least $MIN_TITLES poke titles, has $count" }
        // TODO(open-questions#60): a new plan restarts `seq`, so its first poke can repeat the old plan's last line.
        // String.hashCode is fixed by the JVM spec, so a task keeps its start across processes and reboots.
        return Math.floorMod(step.taskId.hashCode() + step.planVersion + step.seq, count)
    }
}
