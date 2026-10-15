package app.nuddle.push

import android.annotation.SuppressLint
import android.util.Log
import app.nuddle.data.outbox.WorkManagerScheduling
import app.nuddle.data.sync.Syncer
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
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

    @Inject lateinit var syncer: Syncer

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Syncs at once, inside the few seconds a high priority message keeps the process awake: queued work can wait
     * minutes on some phones (NUD-59). It blocks this call (FCM runs it on a background thread) because the service is
     * destroyed as soon as it returns, which cancelled a launched sync and left a closed app with no task and no
     * alarms (NUD-62). A sync that fails or runs out of time is queued.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        // Hints carry no personal data (an event name and a task id); E2E runs read this line from logcat.
        Log.i(TAG, "push ${message.data["event"]} ${message.data["taskId"]} priority ${message.priority}")
        val synced = runBlocking { syncOnPush(syncer, PUSH_SYNC_MS) { work.syncSoon() } }
        Log.i(TAG, if (synced) "synced on push" else "sync on push failed or slow, queued")
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

private const val TAG = "NuddlePush"

/** Within the ~10 s a high priority FCM message keeps the process alive, with room to spare. */
private const val PUSH_SYNC_MS = 8_000L

/**
 * Syncs within [timeoutMs]; a failed or too slow sync goes to [queue] so it still happens (NUD-62). Returns whether it
 * synced in time.
 */
internal suspend fun syncOnPush(
    syncer: Syncer,
    timeoutMs: Long,
    queue: () -> Unit,
): Boolean {
    val synced = withTimeoutOrNull(timeoutMs) { syncer.sync() } ?: false
    if (!synced) queue()
    return synced
}
