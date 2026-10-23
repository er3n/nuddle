package app.nuddle.domain

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Missed-ring detection on the phone (docs/ux/platform.md, Missed-ring detection; open-questions#38). */
class MissedRingRulesTest {
    private val quiet = QuietHours.default(TimeZone.of("Europe/Istanbul"))
    private val open = mapOf("t1" to LocalTaskState(currentPlanVersion = 2, open = true))

    private fun at(time: String) = Instant.parse("2026-10-12T$time:00+03:00")

    private fun step(
        seq: Int,
        time: String,
        version: Int = 2,
        taskId: String = "t1",
    ) = PokeStep(taskId, version, seq, at(time), PokeLevel.SOUND)

    private fun lost(
        steps: List<PokeStep>,
        tasks: Map<String, LocalTaskState> = open,
        fired: Set<AlarmKey> = emptySet(),
        stoppedAt: String = "08:10",
        now: String = "09:30",
    ) = MissedRingRules.lostWhileStopped(steps, tasks, fired, at(stoppedAt), at(now), quiet)

    @Test
    fun `the newest step due while the app was stopped did not ring`() {
        assertEquals(at("08:45"), lost(listOf(step(1, "08:00"), step(2, "08:30"), step(3, "08:45"), step(4, "10:00"))))
    }

    @Test
    fun `steps before the stop, fired, stale, of closed tasks or in quiet hours were not lost`() {
        assertNull(lost(listOf(step(1, "08:00"))))
        assertNull(lost(listOf(step(2, "08:30")), fired = setOf(AlarmKey("t1", 2, 2))))
        assertNull(lost(listOf(step(2, "08:30", version = 1))))
        assertNull(lost(listOf(step(2, "08:30")), tasks = mapOf("t1" to LocalTaskState(2, open = false))))
        assertNull(lost(listOf(step(2, "08:30", taskId = "gone"))))
        assertNull(lost(listOf(step(2, "23:30")), stoppedAt = "23:00", now = "23:50"))
    }

    @Test
    fun `a step an Ertele held or one that reached the phone after its time was never meant to ring here (NUD-162)`() {
        // Step 1 rang at 08:20; Ertele until 08:45 held step 2.
        val snoozed = mapOf("t1" to LocalTaskState(2, open = true, snoozedUntil = at("08:45")))
        assertNull(lost(listOf(step(1, "08:20"), step(2, "08:30")), tasks = snoozed, fired = setOf(AlarmKey("t1", 2, 1))))

        val late = setOf(AlarmKey("t1", 2, 2))
        assertNull(MissedRingRules.lostWhileStopped(listOf(step(2, "08:30")), open, emptySet(), at("08:10"), at("09:30"), quiet, late))
        assertEquals(at("08:30"), lost(listOf(step(2, "08:30"))))
    }

    @Test
    fun `the bell warns until a poke rings again or every step was done after the loss`() {
        assertEquals(at("08:30"), MissedRingRules.warning(at("08:30"), rangAt = null, fixedAt = null))
        assertEquals(at("08:30"), MissedRingRules.warning(at("08:30"), rangAt = at("08:00"), fixedAt = at("07:00")))
        assertNull(MissedRingRules.warning(at("08:30"), rangAt = at("09:00"), fixedAt = null))
        assertNull(MissedRingRules.warning(at("08:30"), rangAt = null, fixedAt = at("08:40")))
        assertNull(MissedRingRules.warning(null, rangAt = null, fixedAt = null))
    }

    @Test
    fun `a step is done when the phone says so, or once opened after the loss`() {
        assertTrue(MissedRingRules.stepDone(checkPasses = true, openedAt = null, lostAt = at("08:30")))
        assertFalse(MissedRingRules.stepDone(checkPasses = false, openedAt = at("09:00"), lostAt = at("08:30")))
        assertTrue(MissedRingRules.stepDone(checkPasses = null, openedAt = at("09:00"), lostAt = at("08:30")))
        assertFalse(MissedRingRules.stepDone(checkPasses = null, openedAt = at("08:00"), lostAt = at("08:30")))
        assertFalse(MissedRingRules.stepDone(checkPasses = null, openedAt = null, lostAt = null))
    }
}
