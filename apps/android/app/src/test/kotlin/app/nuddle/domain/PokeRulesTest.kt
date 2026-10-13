package app.nuddle.domain

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class PokeRulesTest {
    private val quiet = QuietHours.default(TimeZone.of("Europe/Istanbul"))
    private val now = Instant.parse("2026-10-12T08:15:00+03:00")
    private val open = LocalTaskState(currentPlanVersion = 2, open = true)

    private fun step(
        seq: Int = 0,
        version: Int = 2,
        level: PokeLevel = PokeLevel.SOUND,
        at: Instant = now,
        task: String = "t1",
    ) = PokeStep(task, version, seq, at, level)

    @Test
    fun `a current step of an open task rings at its level`() {
        assertEquals(FireDecision.Ring(PokeLevel.SOUND, false), PokeRules.decideFire(step(), open, quiet, now, true))
    }

    @Test
    fun `steps of an older plan version are ignored`() {
        assertEquals(
            FireDecision.Skip(SkipReason.STALE_PLAN),
            PokeRules.decideFire(step(version = 1), open, quiet, now, true),
        )
    }

    @Test
    fun `a closed or unknown task does not ring`() {
        assertEquals(
            FireDecision.Skip(SkipReason.TASK_CLOSED),
            PokeRules.decideFire(step(), open.copy(open = false), quiet, now, true),
        )
        assertEquals(FireDecision.Skip(SkipReason.UNKNOWN_TASK), PokeRules.decideFire(step(), null, quiet, now, true))
    }

    @Test
    fun `quiet hours are checked again right before ringing`() {
        val night = Instant.parse("2026-10-12T23:30:00+03:00")
        assertEquals(
            FireDecision.Skip(SkipReason.QUIET_HOURS),
            PokeRules.decideFire(step(at = night), open, quiet, night, true),
        )
    }

    @Test
    fun `full screen degrades to sound when the permission is missing`() {
        val full = step(level = PokeLevel.FULL_SCREEN)
        assertEquals(
            FireDecision.Ring(PokeLevel.FULL_SCREEN, false),
            PokeRules.decideFire(full, open, quiet, now, true),
        )
        assertEquals(FireDecision.Ring(PokeLevel.SOUND, true), PokeRules.decideFire(full, open, quiet, now, false))
    }

    @Test
    fun `only unfired steps of current plans of open tasks are armed, in time order, a slightly late one too`() {
        val later = step(seq = 2, at = now + 30.minutes)
        val sooner = step(seq = 1, at = now + 10.minutes)
        // The first step of a plan is planned at the moment the task is written and reaches the phone a bit later.
        val justPassed = step(seq = 4, at = now - 1.minutes)
        val past = step(seq = 0, at = now - 3.minutes)
        val fired = step(seq = 3, at = now + 40.minutes)
        val stale = step(seq = 0, version = 1, at = now + 5.minutes)
        val closed = step(task = "t2", at = now + 5.minutes)
        val tasks = mapOf("t1" to open, "t2" to open.copy(open = false))
        val armed =
            PokeRules.stepsToArm(
                listOf(later, past, fired, stale, closed, sooner, justPassed),
                tasks,
                setOf(fired.key),
                now,
            )
        assertEquals(listOf(justPassed, sooner, later), armed)
    }

    @Test
    fun `time left is rounded down to minutes under an hour and hours above`() {
        assertEquals(TimeLeft.Minutes(45), PokeRules.timeLeft(now, now + 45.minutes))
        assertEquals(TimeLeft.Hours(1), PokeRules.timeLeft(now, now + 61.minutes))
        assertEquals(TimeLeft.Minutes(0), PokeRules.timeLeft(now, now - 5.minutes))
    }
}
