package app.nuddle.ui.nav

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import app.nuddle.ui.components.CODE_LENGTH
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status

// Phone sign-in reads the code with the SMS Retriever API, no SMS permission (docs/ux/platform.md; NUD-98). The
// server ends the message with the app hash (SMS_RETRIEVER_HASH), so only an SMS meant for this app arrives here.

private val codePattern = Regex("""(?<!\d)\d{$CODE_LENGTH}(?!\d)""")

/** The first standalone six digit run of the message: "dürt kodun: 123456" then the hash. */
internal fun codeFromSms(message: String): String? = codePattern.find(message)?.value

/**
 * Listens for the code SMS while the code screen is shown. The retriever hears one message within five minutes,
 * so [listen] changes (a new code was sent) start it again.
 */
@Composable
internal fun SmsCodeEffect(
    listen: Any,
    onCode: (String) -> Unit,
) {
    val context = LocalContext.current
    val currentOnCode by rememberUpdatedState(onCode)
    DisposableEffect(context, listen) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    if (intent.action != SmsRetriever.SMS_RETRIEVED_ACTION) return
                    val status = IntentCompat.getParcelableExtra(intent, SmsRetriever.EXTRA_STATUS, Status::class.java)
                    if (status?.statusCode != CommonStatusCodes.SUCCESS) return
                    intent.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE)?.let(::codeFromSms)?.let(currentOnCode)
                }
            }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION),
            SmsRetriever.SEND_PERMISSION,
            null,
            ContextCompat.RECEIVER_EXPORTED,
        )
        // No Play services (an emulator image without them): the keyboard still offers the code.
        runCatching { SmsRetriever.getClient(context).startSmsRetriever() }
        onDispose { context.unregisterReceiver(receiver) }
    }
}
