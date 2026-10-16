package app.nuddle.ui.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The code SMS as the server writes it (apps/web/src/infra/otp/twilio.ts, otpMessage). */
class SmsCodeTest {
    @Test
    fun `the code is read from the message before the app hash`() {
        assertEquals("481927", codeFromSms("dürt kodun: 481927\n\nFA+9qCX9VSu"))
    }

    @Test
    fun `digits in the hash or a longer number are not a code`() {
        assertEquals("481927", codeFromSms("dürt kodun: 481927\n\n1234567abcd"))
        assertNull(codeFromSms("dürt kodun: 4819270"))
        assertNull(codeFromSms("dürt kodun: 4819"))
    }
}
