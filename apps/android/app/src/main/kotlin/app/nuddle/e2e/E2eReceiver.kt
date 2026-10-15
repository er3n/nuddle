package app.nuddle.e2e

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import app.nuddle.BuildConfig
import app.nuddle.alarm.PokeAlarms
import app.nuddle.data.db.NuddleDatabase
import app.nuddle.data.photo.PhotoFiles
import app.nuddle.data.session.SessionStore
import app.nuddle.data.sync.Syncer
import app.nuddle.di.TestTime
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What the E2E orchestrator asks of the app (docs/testing.md, Fake time and Warm start; NUD-65). Declared only in the
 * `e2e` flavor's manifest, and it does nothing in a build without fake time. The orchestrator sends it with
 * `am broadcast -n`, which waits for the result: the code is 1 when it is done, the data says what it did.
 */
@AndroidEntryPoint
class E2eReceiver : BroadcastReceiver() {
    @Inject lateinit var alarms: PokeAlarms

    @Inject lateinit var database: NuddleDatabase

    @Inject lateinit var photos: PhotoFiles

    @Inject lateinit var sessions: SessionStore

    @Inject lateinit var syncer: Syncer

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (!BuildConfig.FAKE_TIME) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val data =
                try {
                    handle(context, intent)
                } catch (error: IllegalStateException) {
                    "error ${error.message}"
                }
            pending.setResult(Activity.RESULT_OK, data, null)
            pending.finish()
        }
    }

    private suspend fun handle(
        context: Context,
        intent: Intent,
    ): String =
        when (intent.action) {
            // Time moves on by `ms`; steps that came due ring at once through AlarmManager.
            ACTION_ADVANCE -> {
                val ms = intent.getLongExtra(EXTRA_MS, 0)
                TestTime.set(context, TestTime.offset(context) + ms)
                alarms.rearm()
                "advanced $ms"
            }

            // Between cases: no task on the phone, the clock back to the phone's, a full sync from the server.
            ACTION_RESET -> {
                alarms.stopAll()
                NotificationManagerCompat.from(context).cancelAll()
                database.clearAllTables()
                photos.deleteAll()
                // Everything since the first change: the connections and invites come back, the tasks are gone.
                sessions.saveSyncCursor("0")
                TestTime.set(context, 0)
                "synced ${syncer.sync()}"
            }

            else -> {
                "unknown ${intent.action}"
            }
        }

    companion object {
        const val ACTION_ADVANCE = "app.nuddle.e2e.ADVANCE"
        const val ACTION_RESET = "app.nuddle.e2e.RESET"
        const val EXTRA_MS = "ms"
    }
}
