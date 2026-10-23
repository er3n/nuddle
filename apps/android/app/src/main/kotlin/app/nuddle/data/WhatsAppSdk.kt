package app.nuddle.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.whatsapp.otp.android.sdk.WhatsAppOtpHandler
import com.whatsapp.otp.android.sdk.WhatsAppOtpIncomingIntentHandler
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Meta's OTP SDK (`com.whatsapp.otp:whatsapp-otp-android-sdk`, ADR 0029): the handshake to WhatsApp and WhatsApp Business. */
class SdkWhatsAppHandshake
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : WhatsAppHandshake {
        private val handler = WhatsAppOtpHandler()

        override fun send(): String? =
            if (handler.isWhatsAppInstalled(context)) handler.sendOtpIntentToWhatsApp(context).toString() else null
    }

/** The intent WhatsApp sends with the code, to [WhatsAppOtpReceiver] (zero-tap) or to MainActivity (one-tap). */
object WhatsAppIntents {
    const val ACTION_OTP_RETRIEVED = "com.whatsapp.otp.OTP_RETRIEVED"

    /** The extra Meta's documentation names for the code (the SDK keeps its own constant private). */
    private const val EXTRA_CODE = "code"

    /** Hands a WhatsApp intent to [codes]; anything else is ignored. True when its code was taken in. */
    fun deliver(
        intent: Intent?,
        codes: WhatsAppCodes,
    ): Boolean {
        if (intent?.action != ACTION_OTP_RETRIEVED) return false
        val handshakeId = WhatsAppOtpIncomingIntentHandler().getHandshakeIdFromWhatsAppIntent(intent)
        return codes.receive(handshakeId, intent.getStringExtra(EXTRA_CODE))
    }
}

/** Zero-tap: WhatsApp broadcasts the code here while the code screen waits (docs/ux/platform.md). */
@AndroidEntryPoint
class WhatsAppOtpReceiver : BroadcastReceiver() {
    @Inject lateinit var codes: WhatsAppCodes

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        WhatsAppIntents.deliver(intent, codes)
    }
}
