package app.nuddle.alarm

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import app.nuddle.data.db.TaskDao
import app.nuddle.data.session.HeadsUpStore
import app.nuddle.data.session.SessionStore
import app.nuddle.domain.Diagnostics
import app.nuddle.domain.HeadsUpRules
import app.nuddle.domain.PokeLevel
import app.nuddle.domain.PokeStep
import app.nuddle.domain.QuietHours
import app.nuddle.domain.headsUpKey
import kotlinx.datetime.TimeZone
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * How the phone notices that the person is using it before a loud step (docs/ux/platform.md, Already done?; NUD-129):
 * a check alarm inside the window, and the unlock itself while the app's process is alive.
 */
interface HeadsUpChecks {
    /** Sets the one check alarm for [at], or cancels it when null. */
    fun checkAt(at: Instant?)

    /** Listens for the unlock (`ACTION_USER_PRESENT`) while [active], and not at all otherwise. */
    fun watchUnlocks(active: Boolean)

    /** Does nothing: the default where tests build [ArmedPokesKeeper] without caring. */
    object None : HeadsUpChecks {
        override fun checkAt(at: Instant?) = Unit

        override fun watchUnlocks(active: Boolean) = Unit
    }
}

/** The armed set changed: the next check and the unlock listener follow it. */
fun HeadsUpChecks.follow(
    armed: List<PokeStep>,
    now: Instant,
) {
    checkAt(HeadsUpRules.nextCheck(armed, now))
    watchUnlocks(armed.any { it.level != PokeLevel.SILENT })
}

/**
 * Shows "Zaten yaptın mı?" for the tasks [HeadsUpRules.due] names, once per plan version, and keeps the next check
 * set. Never touches an alarm: the step rings as planned whether the heads-up is answered or not.
 */
class HeadsUp
    @Inject
    constructor(
        private val alarms: PokeAlarms,
        private val tasks: TaskDao,
        private val sessions: SessionStore,
        private val notifications: PokeNotifications,
        private val store: HeadsUpStore,
        private val checks: HeadsUpChecks,
        private val clock: Clock,
        private val timeZone: TimeZone,
        private val diagnostics: Diagnostics = Diagnostics.None,
    ) {
        /** The screen was unlocked. */
        suspend fun unlocked() {
            show(alarms.toArm())
        }

        /** The check alarm fired; [inUse] is whether the screen is on and unlocked right now. */
        suspend fun check(inUse: Boolean) {
            val armed = alarms.toArm()
            if (inUse) show(armed)
            checks.follow(armed, clock.now())
        }

        /** The app started: nothing else may have set the checks since the process died. */
        suspend fun start() {
            checks.follow(alarms.toArm(), clock.now())
        }

        private suspend fun show(armed: List<PokeStep>) {
            val now = clock.now()
            val taskList = tasks.all()
            val byId = taskList.associateBy { it.id }
            val due =
                HeadsUpRules.due(
                    armed = armed,
                    tasks = taskList.associate { it.id to it.toLocalState() },
                    shown = store.shown(),
                    showing = notifications.showing(),
                    quietHours = sessions.quietHours() ?: QuietHours.default(timeZone),
                    now = now,
                )
            val open = taskList.filter { it.toLocalState().open }.map { it.id }.toSet()
            due.forEach { step ->
                val task = byId[step.taskId] ?: return@forEach
                diagnostics.log("heads_up ${step.taskId} v${step.planVersion} #${step.seq}")
                notifications.showHeadsUp(task, step)
                store.markShown(step.headsUpKey, keep = open)
            }
        }
    }

/** Whether the person is using the phone now: the screen is on and not locked. */
internal fun Context.phoneInUse(): Boolean =
    getSystemService(PowerManager::class.java).isInteractive &&
        !getSystemService(KeyguardManager::class.java).isKeyguardLocked

/**
 * The check alarm is inexact (`setAndAllowWhileIdle`): a heads-up may come a minute late, and in Doze, when nobody uses
 * the phone, it waits until the screen comes on, which is the moment that counts. The unlock is heard only through a
 * receiver registered at runtime: Android has not delivered `ACTION_USER_PRESENT` to manifest receivers since 8.0.
 */
class AndroidHeadsUpChecks(
    private val context: Context,
    private val alarmManager: AlarmManager,
    /** From the app's clock to the phone's; only the `e2e` flavor's fake time moves it (TestTime). */
    private val onPhoneClock: (Instant) -> Instant = { it },
) : HeadsUpChecks {
    override fun checkAt(at: Instant?) {
        val operation = checkIntent()
        if (at == null) {
            alarmManager.cancel(operation)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, onPhoneClock(at).toEpochMilliseconds(), operation)
        }
    }

    override fun watchUnlocks(active: Boolean) {
        synchronized(this) {
            val current = unlocks
            if (active && current == null) {
                val receiver = UnlockReceiver()
                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    IntentFilter(Intent.ACTION_USER_PRESENT),
                    // A system broadcast reaches a receiver that is not exported.
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
                unlocks = receiver
            } else if (!active && current != null) {
                context.unregisterReceiver(current)
                unlocks = null
            }
        }
    }

    private var unlocks: BroadcastReceiver? = null

    private fun checkIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            PokeIntents.headsUp(context, PokeIntents.ACTION_HEADS_UP_CHECK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Hands the unlock to [HeadsUpReceiver], where Hilt can inject. */
    private class UnlockReceiver : BroadcastReceiver() {
        override fun onReceive(
            context: Context,
            intent: Intent,
        ) {
            context.sendBroadcast(PokeIntents.headsUp(context, PokeIntents.ACTION_HEADS_UP_UNLOCKED))
        }
    }
}
