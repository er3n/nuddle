package app.nuddle.data

import app.nuddle.domain.Diagnostics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Meta's WhatsApp OTP handshake (ADR 0029, docs/ux/platform.md): sent before a code is asked for, so WhatsApp hands
 * the code to the app (zero-tap) or opens it with the code (one-tap). Null when WhatsApp is not installed or the
 * handshake could not go out; otherwise the handshake id that the code from WhatsApp must carry back.
 */
fun interface WhatsAppHandshake {
    fun send(): String?
}

/** What the code screen needs from WhatsApp: the codes it handed over, checked against the handshake. */
interface WhatsAppOtp {
    /** Sends the handshake when WhatsApp is installed. Called right before every `auth/otp`. */
    fun handshake()

    /** The code WhatsApp handed over and nobody took yet. */
    val codes: Flow<String>

    /** The code screen filled [code] in; it does not come again. */
    fun take(code: String)
}

private val sixDigits = Regex("""\d{6}""")

/**
 * Keeps the id of the last handshake and lets through only a code that carries it. The receiver for
 * `com.whatsapp.otp.OTP_RETRIEVED` is exported, so any app could send it; only WhatsApp got the handshake id (the
 * handshake intent is addressed to the WhatsApp packages), which is the check Meta's SDK 1.0 makes in place of the
 * PendingIntent check. Breadcrumbs (ADR 0023) say what happened, never the code or the id.
 */
@Singleton
class WhatsAppCodes
    @Inject
    constructor(
        private val handshakes: WhatsAppHandshake,
        private val diagnostics: Diagnostics,
    ) : WhatsAppOtp {
        @Volatile private var expected: String? = null
        private val pending = MutableStateFlow<String?>(null)

        override val codes: Flow<String> = pending.filterNotNull()

        override fun handshake() {
            pending.value = null
            val id = runCatching { handshakes.send() }.onFailure(diagnostics::record).getOrNull()
            expected = id
            diagnostics.log(if (id != null) "whatsapp_otp handshake" else "whatsapp_otp no handshake")
        }

        /** A `com.whatsapp.otp.OTP_RETRIEVED` intent arrived with these extras. True when its code was taken in. */
        fun receive(
            handshakeId: String?,
            code: String?,
        ): Boolean {
            val reason =
                when {
                    handshakeId.isNullOrBlank() -> "missing"
                    !isUuid(handshakeId.trim()) -> "invalid"
                    expected == null || handshakeId.trim() != expected -> "mismatch"
                    code == null || !sixDigits.matches(code.trim()) -> "no code"
                    else -> null
                }
            if (reason != null) {
                diagnostics.log("whatsapp_otp rejected $reason")
                return false
            }
            pending.value = checkNotNull(code).trim()
            diagnostics.log("whatsapp_otp code")
            return true
        }

        override fun take(code: String) {
            pending.compareAndSet(code, null)
        }

        private fun isUuid(value: String): Boolean = runCatching { UUID.fromString(value) }.isSuccess
    }
