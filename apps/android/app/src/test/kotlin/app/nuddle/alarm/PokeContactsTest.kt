package app.nuddle.alarm

import app.nuddle.testing.Cast
import app.nuddle.testing.aTask
import app.nuddle.testing.sibelConnection
import app.nuddle.ui.screens.poke.PokeContactUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Ara or Mesaj at on the full screen poke (docs/domain.md, Call or message from the poke; NUD-128). */
class PokeContactsTest {
    private val callSibel = aTask().copy(contactPersonId = Cast.SIBEL_ID, contactAction = "call")

    @Test
    fun `a task about calling a connection dials the number the phone has for them`() {
        assertEquals(PokeContactUi(Cast.SIBEL_PHONE, message = false), pokeContact(callSibel, sibelConnection()))
        assertEquals(
            PokeContactUi(Cast.SIBEL_PHONE, message = true),
            pokeContact(callSibel.copy(contactAction = "message"), sibelConnection()),
        )
    }

    @Test
    fun `no button without a contact, an action this app does not know, or another person's connection`() {
        assertNull(pokeContact(aTask(), sibelConnection()))
        assertNull(pokeContact(callSibel.copy(contactAction = "video"), sibelConnection()))
        assertNull(pokeContact(callSibel.copy(contactPersonId = Cast.EREN_ID), sibelConnection()))
    }

    @Test
    fun `no button once the connection is gone or has no number`() {
        assertNull(pokeContact(callSibel, null))
        assertNull(pokeContact(callSibel, sibelConnection(phone = null)))
        assertNull(pokeContact(callSibel, sibelConnection().copy(status = "removed")))
    }
}
