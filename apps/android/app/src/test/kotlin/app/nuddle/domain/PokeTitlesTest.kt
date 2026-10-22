package app.nuddle.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/** NUD-130, docs/glossary.md (Poke titles): one line per poke, kept on a re-show, never the same twice in a row. */
class PokeTitlesTest {
    private val at = Instant.parse("2026-10-12T08:00:00+03:00")

    private fun step(
        seq: Int,
        planVersion: Int = 1,
        taskId: String = "task-1",
        level: PokeLevel = PokeLevel.SOUND,
    ) = PokeStep(taskId, planVersion, seq, at, level)

    @Test
    fun `the same step always gets the same line, so a re-show keeps it`() {
        repeat(3) { assertEquals(PokeTitles.index(step(seq = 2), 4), PokeTitles.index(step(seq = 2), 4)) }
        assertEquals(PokeTitles.index(step(seq = 3), 4), PokeTitles.index(step(seq = 3, level = PokeLevel.FULL_SCREEN), 4))
    }

    @Test
    fun `two pokes in a row of one plan never share a line, for any list size and any task`() {
        listOf("task-1", "a", "", "00000000-0000-0000-0000-000000000001").forEach { taskId ->
            (2..5).forEach { count ->
                (1 until 12).forEach { seq ->
                    assertNotEquals(
                        PokeTitles.index(step(seq, taskId = taskId), count),
                        PokeTitles.index(step(seq + 1, taskId = taskId), count),
                        "task $taskId, $count lines, seq $seq",
                    )
                }
            }
        }
    }

    @Test
    fun `the index is always inside the list`() {
        listOf("task-1", "zz", "Ω").forEach { taskId ->
            (1..20).forEach { seq ->
                val index = PokeTitles.index(step(seq, planVersion = Int.MAX_VALUE, taskId = taskId), 4)
                assertTrue(index in 0 until 4, "index $index")
            }
        }
    }

    @Test
    fun `a pass through one level uses every line before one comes back`() {
        val lines = (1..4).map { PokeTitles.index(step(it), 4) }
        assertEquals(setOf(0, 1, 2, 3), lines.toSet())
    }

    @Test
    fun `different tasks do not all open with the same line`() {
        val first = (1..20).map { PokeTitles.index(step(seq = 1, taskId = "task-$it"), 4) }.toSet()
        assertTrue(first.size > 1, "first lines $first")
    }

    @Test
    fun `a list needs at least two lines to rotate`() {
        assertFailsWith<IllegalArgumentException> { PokeTitles.index(step(seq = 1), 1) }
    }
}
