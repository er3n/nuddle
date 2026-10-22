package app.nuddle.domain

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** "Zaten yaptın mı?" before a loud step (docs/domain.md, Already done?; NUD-129). */
class HeadsUpRulesTest {
    private val quiet = QuietHours.default(TimeZone.of("Europe/Istanbul"))
    private val now = Instant.parse("2026-10-12T08:00:00+03:00")
    private val open = LocalTaskState(currentPlanVersion = 2, open = true)

    private fun step(
        inMinutes: Int,
        level: PokeLevel = PokeLevel.SOUND,
        seq: Int = 1,
        task: String = "t1",
        version: Int = 2,
    ) = PokeStep(task, version, seq, now + inMinutes.minutes, level)

    private fun due(
        armed: List<PokeStep>,
        tasks: Map<String, LocalTaskState> = mapOf("t1" to open),
        shown: Set<HeadsUpKey> = emptySet(),
        showing: Set<String> = emptySet(),
        at: Instant = now,
    ) = HeadsUpRules.due(armed, tasks, shown, showing, quiet, at)

    @Test
    fun `a sound or full screen step within 10 minutes gets a heads-up`() {
        assertEquals(listOf(step(9)), due(listOf(step(9), step(20, PokeLevel.FULL_SCREEN, seq = 2))))
        assertEquals(listOf(step(10, PokeLevel.FULL_SCREEN)), due(listOf(step(10, PokeLevel.FULL_SCREEN))))
    }

    @Test
    fun `outside the window there is none`() {
        assertEquals(emptyList(), due(listOf(step(11))))
        // A minute before the step it rings in a moment anyway.
        assertEquals(emptyList(), due(listOf(step(0))))
        assertEquals(listOf(step(1)), due(listOf(step(1))))
    }

    @Test
    fun `only before a loud step, and only when it is the task's next one`() {
        assertEquals(emptyList(), due(listOf(step(5, PokeLevel.SILENT))))
        // The silent step comes first and has Bitti itself.
        assertEquals(emptyList(), due(listOf(step(3, PokeLevel.SILENT), step(8, seq = 2))))
    }

    @Test
    fun `at most once per task and plan version`() {
        val shown = setOf(HeadsUpKey("t1", 2))
        assertEquals(emptyList(), due(listOf(step(5)), shown = shown))
        // A new plan version may ask once more.
        val v3 = step(5, version = 3)
        assertEquals(listOf(v3), due(listOf(v3), tasks = mapOf("t1" to open.copy(currentPlanVersion = 3)), shown = shown))
    }

    @Test
    fun `not for a closed task, an old plan version, a one-time message or while a poke is up`() {
        assertEquals(emptyList(), due(listOf(step(5)), tasks = mapOf("t1" to open.copy(open = false))))
        assertEquals(emptyList(), due(listOf(step(5)), tasks = emptyMap()))
        assertEquals(emptyList(), due(listOf(step(5, version = 1))))
        assertEquals(emptyList(), due(listOf(step(5)), tasks = mapOf("t1" to open.copy(notice = true))))
        assertEquals(emptyList(), due(listOf(step(5)), showing = setOf("t1")))
    }

    @Test
    fun `quiet hours hold it back, except in an at task's own window`() {
        val night = Instant.parse("2026-10-12T23:30:00+03:00")
        val atNight = PokeStep("t1", 2, 1, night + 5.minutes, PokeLevel.SOUND)
        assertEquals(emptyList(), due(listOf(atNight), at = night))
        assertEquals(listOf(atNight), due(listOf(atNight), tasks = mapOf("t1" to open.copy(moment = true)), at = night))
    }

    @Test
    fun `one per task, several tasks at once`() {
        val other = step(7, task = "t2")
        val result = due(listOf(step(9), other), tasks = mapOf("t1" to open, "t2" to open))
        assertEquals(listOf(other, step(9)), result)
    }

    @Test
    fun `the phone looks every 2 minutes from 10 to 1 minute before each loud step`() {
        val armed = listOf(step(30), step(5, PokeLevel.SILENT, seq = 0))
        assertEquals(now + 20.minutes, HeadsUpRules.nextCheck(armed, now))
        assertEquals(now + 22.minutes, HeadsUpRules.nextCheck(armed, now + 20.minutes))
        assertEquals(now + 28.minutes, HeadsUpRules.nextCheck(armed, now + 26.minutes))
        // After the last look nothing is left before the step.
        assertNull(HeadsUpRules.nextCheck(armed, now + 28.minutes))
    }

    @Test
    fun `no loud step, no check`() {
        assertNull(HeadsUpRules.nextCheck(listOf(step(5, PokeLevel.SILENT)), now))
        assertNull(HeadsUpRules.nextCheck(emptyList(), now))
    }

    @Test
    fun `a step already inside the window is looked at at once on the next two minute mark`() {
        val next = HeadsUpRules.nextCheck(listOf(step(7)), now)
        assertTrue(next != null && next > now && next - now <= HeadsUpRules.CHECK_EVERY, "$next")
    }
}
