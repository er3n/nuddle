package app.nuddle.domain

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Device execution before the first unlock after a reboot (docs/ux/platform.md, Direct boot; NUD-125). */
class DirectBootRulesTest {
    private val istanbul = TimeZone.of("Europe/Istanbul")
    private val quiet = QuietHours.default(istanbul)
    private val now = Instant.parse("2026-10-12T08:00:00+03:00")

    private fun step(
        seq: Int,
        at: Instant,
        level: PokeLevel = PokeLevel.SOUND,
    ) = PokeStep("task-1", 2, seq, at, level)

    @Test
    fun `stored steps are armed again unless they rang before the unlock or are too late`() {
        val plan =
            LockedPlan(
                steps =
                    listOf(
                        LockedStep(step(1, now - 10.minutes), moment = false),
                        LockedStep(step(2, now - 1.minutes), moment = false),
                        LockedStep(step(3, now + 5.minutes), moment = false),
                        LockedStep(step(4, now + 30.minutes), moment = false),
                    ),
            )
        val armed = DirectBootRules.stepsToArm(plan, fired = setOf(AlarmKey("task-1", 2, 3)), now = now)
        assertEquals(listOf(2, 4), armed.map { it.seq })
    }

    @Test
    fun `a full screen step rings as sound before the unlock, not as a missing permission`() {
        val decision = DirectBootRules.decideFire(LockedStep(step(1, now, PokeLevel.FULL_SCREEN), moment = false), quiet, now)
        assertEquals(FireDecision.Ring(PokeLevel.SOUND, permissionMissing = false), decision)
    }

    @Test
    fun `quiet hours still hold before the unlock, except for an at task`() {
        val night = Instant.parse("2026-10-12T02:00:00+03:00")
        assertEquals(
            FireDecision.Skip(SkipReason.QUIET_HOURS),
            DirectBootRules.decideFire(LockedStep(step(1, night), moment = false), quiet, night),
        )
        assertEquals(
            FireDecision.Ring(PokeLevel.SOUND, permissionMissing = false),
            DirectBootRules.decideFire(LockedStep(step(1, night), moment = true), quiet, night),
        )
    }

    @Test
    fun `a silent step stays silent`() {
        val decision = DirectBootRules.decideFire(LockedStep(step(1, now, PokeLevel.SILENT), moment = false), quiet, now)
        assertEquals(FireDecision.Ring(PokeLevel.SILENT, permissionMissing = false), decision)
    }
}
