package app.nuddle

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.nuddle.alarm.MissedRingCheck
import app.nuddle.alarm.NotificationChannels
import app.nuddle.data.session.SessionStore
import app.nuddle.di.ApplicationScope
import app.nuddle.domain.Diagnostics
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

    @Inject lateinit var missedRingCheck: MissedRingCheck

    @Inject lateinit var diagnostics: Diagnostics

    @Inject lateinit var sessions: SessionStore

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
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
        NotificationChannels.create(this)
        scope.launch { missedRingCheck.onLaunch() }
    }
}
