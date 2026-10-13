package app.nuddle.push

import android.annotation.SuppressLint
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
 *
 * Reason for the suppression: that lint check predates FCM's installation ID API and asks for the deprecated
 * onNewToken(); onRegistered() is its replacement (ADR 0015).
 */
@SuppressLint("MissingFirebaseInstanceTokenRefresh")
@AndroidEntryPoint
class NuddleMessagingService : FirebaseMessagingService() {
    @Inject lateinit var work: WorkManagerScheduling

    @Inject lateinit var registrar: DeviceRegistrar

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        work.syncSoon()
    }

    /** FCM can reach this install by [fid] now (ADR 0015). */
    override fun onRegistered(fid: String) {
        scope.launch { registrar.register(fid) }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
