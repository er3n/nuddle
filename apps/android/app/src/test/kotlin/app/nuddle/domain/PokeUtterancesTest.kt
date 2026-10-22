package app.nuddle.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** NUD-124, docs/glossary.md (Spoken poke): "Sibel diyor ki: ilacını iç", the title alone for a task to self. */
class PokeUtterancesTest {
    @Test
    fun `a task from someone else names the sender, on the sound and the full screen step`() {
        val expected = PokeUtterance.FromSender("Sibel", "ilacını iç")
        assertEquals(expected, PokeUtterances.of(PokeLevel.SOUND, "ilacını iç", toSelf = false, senderName = "Sibel"))
        assertEquals(expected, PokeUtterances.of(PokeLevel.FULL_SCREEN, "ilacını iç", toSelf = false, senderName = "Sibel"))
    }

    @Test
    fun `a task to self says only the task`() {
        assertEquals(
            PokeUtterance.TaskOnly("Para çek"),
            PokeUtterances.of(PokeLevel.SOUND, "Para çek", toSelf = true, senderName = "Eren"),
        )
    }

    @Test
    fun `a sender whose name is not on the phone says only the task`() {
        assertEquals(PokeUtterance.TaskOnly("Çöpü at"), PokeUtterances.of(PokeLevel.SOUND, "Çöpü at", toSelf = false, senderName = null))
        assertEquals(PokeUtterance.TaskOnly("Çöpü at"), PokeUtterances.of(PokeLevel.SOUND, "Çöpü at", toSelf = false, senderName = "  "))
    }

    @Test
    fun `names and titles are read as written, without the spaces around them`() {
        assertEquals(
            PokeUtterance.FromSender("Sibel", "Temizlikçi için para çek"),
            PokeUtterances.of(PokeLevel.SOUND, "  Temizlikçi için para çek ", toSelf = false, senderName = " Sibel "),
        )
    }

    @Test
    fun `a silent step or an empty title says nothing`() {
        assertNull(PokeUtterances.of(PokeLevel.SILENT, "ilacını iç", toSelf = false, senderName = "Sibel"))
        assertNull(PokeUtterances.of(PokeLevel.SOUND, " ", toSelf = false, senderName = "Sibel"))
    }
}
