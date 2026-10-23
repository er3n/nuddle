package app.nuddle.data

import android.content.Intent
import app.nuddle.testing.RecordingDiagnostics
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * WhatsApp sign-in codes (ADR 0029, docs/ux/platform.md): only a code carrying the id of our last handshake gets in,
 * and the breadcrumbs never hold the code or the id (ADR 0023). Robolectric for the Intent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WhatsAppCodesTest {
    private val handshakeId = "6f1c2b9e-3a4d-4c5e-8f70-1a2b3c4d5e6f"
    private var nextHandshake: String? = handshakeId
    private val diagnostics = RecordingDiagnostics()
    private val codes = WhatsAppCodes({ nextHandshake }, diagnostics)

    private fun fromWhatsApp(
        requestId: String?,
        code: String?,
        action: String = WhatsAppIntents.ACTION_OTP_RETRIEVED,
    ): Intent =
        Intent(action).apply {
            requestId?.let { putExtra("request_id", it) }
            code?.let { putExtra("code", it) }
        }

    @Test
    fun `a code with the handshake id is taken in and handed to the code screen`() =
        runTest {
            codes.handshake()
            assertTrue(WhatsAppIntents.deliver(fromWhatsApp(handshakeId, "482913"), codes))
            assertEquals("482913", codes.codes.first())
        }

    @Test
    fun `a code with another id, no id or a malformed id is refused`() {
        codes.handshake()
        assertFalse(WhatsAppIntents.deliver(fromWhatsApp("0e6f7a8b-1c2d-4e3f-9a0b-c1d2e3f4a5b6", "482913"), codes))
        assertFalse(WhatsAppIntents.deliver(fromWhatsApp(null, "482913"), codes))
        assertFalse(WhatsAppIntents.deliver(fromWhatsApp("not-a-uuid", "482913"), codes))
        assertEquals(
            listOf("whatsapp_otp rejected mismatch", "whatsapp_otp rejected missing", "whatsapp_otp rejected invalid"),
            diagnostics.lines.drop(1),
        )
    }

    @Test
    fun `nothing is taken without a handshake, as when WhatsApp is not installed`() {
        nextHandshake = null
        codes.handshake()
        assertFalse(WhatsAppIntents.deliver(fromWhatsApp(handshakeId, "482913"), codes))
        assertEquals(listOf("whatsapp_otp no handshake", "whatsapp_otp rejected mismatch"), diagnostics.lines)
    }

    @Test
    fun `a new handshake drops a code nobody took and the id before it`() {
        codes.handshake()
        assertTrue(WhatsAppIntents.deliver(fromWhatsApp(handshakeId, "482913"), codes))
        nextHandshake = "0e6f7a8b-1c2d-4e3f-9a0b-c1d2e3f4a5b6"
        codes.handshake()
        assertFalse(WhatsAppIntents.deliver(fromWhatsApp(handshakeId, "111111"), codes))
    }

    @Test
    fun `only six digits count as a code`() {
        codes.handshake()
        assertFalse(WhatsAppIntents.deliver(fromWhatsApp(handshakeId, "48291"), codes))
        assertFalse(WhatsAppIntents.deliver(fromWhatsApp(handshakeId, "48291a"), codes))
        assertFalse(WhatsAppIntents.deliver(fromWhatsApp(handshakeId, null), codes))
        assertTrue(WhatsAppIntents.deliver(fromWhatsApp(" $handshakeId ", " 482913 "), codes))
    }

    @Test
    fun `an intent that is not WhatsApp's is ignored`() {
        codes.handshake()
        assertFalse(WhatsAppIntents.deliver(fromWhatsApp(handshakeId, "482913", Intent.ACTION_SEND), codes))
        assertFalse(WhatsAppIntents.deliver(null, codes))
    }

    @Test
    fun `a handshake that throws is recorded and lets nothing in`() {
        val failing = WhatsAppCodes({ error("no package manager") }, diagnostics)
        failing.handshake()
        assertEquals(1, diagnostics.errors.size)
        assertFalse(WhatsAppIntents.deliver(fromWhatsApp(handshakeId, "482913"), failing))
    }

    @Test
    fun `breadcrumbs never carry the code or the handshake id (ADR 0023)`() {
        codes.handshake()
        WhatsAppIntents.deliver(fromWhatsApp(handshakeId, "482913"), codes)
        WhatsAppIntents.deliver(fromWhatsApp("0e6f7a8b-1c2d-4e3f-9a0b-c1d2e3f4a5b6", "123456"), codes)
        assertEquals(listOf("whatsapp_otp handshake", "whatsapp_otp code", "whatsapp_otp rejected mismatch"), diagnostics.lines)
    }

    @Test
    fun `a code taken by the code screen does not come again`() =
        runTest {
            codes.handshake()
            WhatsAppIntents.deliver(fromWhatsApp(handshakeId, "482913"), codes)
            codes.take("482913")
            WhatsAppIntents.deliver(fromWhatsApp(handshakeId, "654321"), codes)
            assertEquals("654321", codes.codes.first())
        }
}
