package app.nuddle.push

import app.nuddle.data.outbox.WorkManagerScheduling
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FCM data messages are only hints ("sync now", "plan changed", "task cancelled"); every one of them triggers a
 * sync, which is the source of truth (docs/architecture.md, Sync model).
 */
@AndroidEntryPoint
class NuddleMessagingService : FirebaseMessagingService() {
    @Inject lateinit var work: WorkManagerScheduling

    @Inject lateinit var registrar: DeviceRegistrar

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        work.syncSoon()
    }

    // Reason: registration tokens are deprecated in favor of installation IDs; NUD-26 keeps tokens for now.
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        scope.launch { registrar.register(token) }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
