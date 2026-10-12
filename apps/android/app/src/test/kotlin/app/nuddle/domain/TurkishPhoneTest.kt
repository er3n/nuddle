package app.nuddle.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TurkishPhoneTest {
    @Test
    fun `sanitize keeps ten digits and drops a typed trunk zero`() {
        assertEquals("5320000002", TurkishPhone.sanitize("0532 000 00 02"))
        assertEquals("5320000002", TurkishPhone.sanitize("532-000-00-02999"))
    }

    @Test
    fun `only ten digits starting with 5 are mobile numbers`() {
        assertTrue(TurkishPhone.isValidMobile("5320000002"))
        assertFalse(TurkishPhone.isValidMobile("2120000002"))
        assertFalse(TurkishPhone.isValidMobile("532000000"))
        assertNull(TurkishPhone.toE164("53200"))
        assertEquals("+905320000002", TurkishPhone.toE164("5320000002"))
    }

    @Test
    fun `numbers are grouped as on the design`() {
        assertEquals("532 123 45 67", TurkishPhone.group("5321234567"))
        assertEquals("532 12", TurkishPhone.group("53212"))
        assertEquals("+90 532 123 45 67", TurkishPhone.display("+905321234567"))
    }

    @Test
    fun `numbers as contacts store them`() {
        for (raw in listOf("+90 532 123 45 67", "0090 532 123 4567", "0532 123 45 67", "532-123-4567", "(0532) 1234567")) {
            assertEquals("+905321234567", TurkishPhone.fromContact(raw), raw)
        }
        assertEquals(null, TurkishPhone.fromContact("+44 20 7946 0958"))
        assertEquals(null, TurkishPhone.fromContact("0212 123 45 67"))
    }
}
