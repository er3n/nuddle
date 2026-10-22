package app.nuddle.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.nuddle.domain.Diagnostics
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Before the first unlock Crashlytics cannot write its files; the log still gets the line. */
private const val TAG = "Nuddle"

/** Runs suspend work from a receiver without blocking the main thread; the system keeps us alive until it ends. */
private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/**
 * An alarm for one poke step fired. Direct boot aware: before the first unlock after a reboot it rings from the
 * device-protected copy (docs/ux/platform.md, Direct boot). Everything that opens Room, DataStore or WorkManager is
 * behind a [Lazy] so it is not even built then.
 */
@AndroidEntryPoint
class PokeAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var firing: Lazy<PokeFiring>

    @Inject lateinit var lockedFiring: Lazy<LockedPokeFiring>

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val key = PokeIntents.key(intent) ?: return
        if (!context.userUnlocked()) {
            runAsync {
                val decision = lockedFiring.get().fire(key)
                Log.i(TAG, "locked poke ${key.taskId} v${key.planVersion} #${key.seq} $decision")
            }
            return
        }
        runAsync { firing.get().fire(key) }
    }
}

/** Notification actions: Bitti, Ertele, Bugün olmaz (3a), and Bitti on "Zaten yaptın mı?" (NUD-129). */
@AndroidEntryPoint
class PokeActionReceiver : BroadcastReceiver() {
    @Inject lateinit var actions: PokeActions

    @Inject lateinit var diagnostics: Diagnostics

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val key = PokeIntents.key(intent) ?: return
        diagnostics.log("notification ${intent.action?.substringAfterLast('.')} ${key.taskId}")
        runAsync {
            when (intent.action) {
                PokeIntents.ACTION_DONE -> actions.done(key)
                PokeIntents.ACTION_SNOOZE -> actions.snooze(key)
                PokeIntents.ACTION_CANT_TODAY -> actions.cantToday(key)
                PokeIntents.ACTION_HEADS_UP_DONE -> actions.doneFromHeadsUp(key)
            }
        }
    }
}

/** "Zaten yaptın mı?" (NUD-129): the check alarm inside the window, and the unlock heard while the process lives. */
@AndroidEntryPoint
class HeadsUpReceiver : BroadcastReceiver() {
    @Inject lateinit var headsUp: HeadsUp

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.action) {
            PokeIntents.ACTION_HEADS_UP_CHECK -> {
                val inUse = context.phoneInUse()
                runAsync { headsUp.check(inUse) }
            }

            PokeIntents.ACTION_HEADS_UP_UNLOCKED -> {
                runAsync { headsUp.unlocked() }
            }
        }
    }
}

/**
 * Boot, app update, time and time zone changes: set every pending alarm again from Room (ADR 0005). Before the first
 * unlock after a reboot (`LOCKED_BOOT_COMPLETED`) from the device-protected copy instead; after it, the pokes that rang
 * meanwhile are handed over first (docs/ux/platform.md, Direct boot; NUD-125).
 */
@AndroidEntryPoint
class RearmReceiver : BroadcastReceiver() {
    @Inject lateinit var alarms: Lazy<PokeAlarms>

    @Inject lateinit var firing: Lazy<PokeFiring>

    @Inject lateinit var lockedFiring: Lazy<LockedPokeFiring>

    @Inject lateinit var diagnostics: Lazy<Diagnostics>

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action !in REARM_ACTIONS) return
        val action = intent.action?.substringAfterLast('.')
        if (!context.userUnlocked()) {
            runAsync { Log.i(TAG, "locked rearm $action ${lockedFiring.get().rearm()}") }
            return
        }
        diagnostics.get().log("rearm $action")
        runAsync {
            firing.get().takeOverLockedFires()
            alarms.get().rearm()
        }
    }

    private companion object {
        val REARM_ACTIONS =
            setOf(
                Intent.ACTION_LOCKED_BOOT_COMPLETED,
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
            )
    }
}
