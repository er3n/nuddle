package app.nuddle

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.nuddle.alarm.HeadsUp
import app.nuddle.alarm.MissedRingCheck
import app.nuddle.alarm.NotificationChannels
import app.nuddle.alarm.userUnlocked
import app.nuddle.data.session.SessionStore
import app.nuddle.di.ApplicationScope
import app.nuddle.domain.Diagnostics
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class NuddleApp :
    Application(),
    Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var missedRingCheck: Lazy<MissedRingCheck>

    @Inject lateinit var headsUp: Lazy<HeadsUp>

    @Inject lateinit var diagnostics: Diagnostics

    @Inject lateinit var sessions: SessionStore

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.create(this)
        // After a reboot the app may start before the first unlock, for a poke (docs/ux/platform.md, Direct boot). The
        // session, Room and Crashlytics' files cannot be read then, so the rest waits for the unlock.
        if (userUnlocked()) {
            onUnlocked()
        } else {
            ContextCompat.registerReceiver(
                this,
                object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context,
                        intent: Intent,
                    ) {
                        unregisterReceiver(this)
                        onUnlocked()
                    }
                },
                IntentFilter(Intent.ACTION_USER_UNLOCKED),
                // A system broadcast reaches a receiver that is not exported.
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
    }

    private fun onUnlocked() {
        diagnostics.setKey(Diagnostics.KEY_VERSION, "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        diagnostics.setKey(Diagnostics.KEY_FLAVOR, BuildConfig.FLAVOR)
        diagnostics.log("app start ${BuildConfig.VERSION_NAME} ${BuildConfig.FLAVOR}")
        // Crash reports carry the opaque person id, never the phone number (ADR 0023, privacy notice).
        scope.launch {
            sessions.session
                .map { it?.personId }
                .distinctUntilChanged()
                .collect(diagnostics::setPerson)
        }
        scope.launch { missedRingCheck.get().onLaunch() }
        // The unlock listener for "Zaten yaptın mı?" lives only as long as the process (NUD-129).
        scope.launch { headsUp.get().start() }
    }
}
