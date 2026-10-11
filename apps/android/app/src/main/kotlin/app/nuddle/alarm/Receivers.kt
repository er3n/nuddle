package app.nuddle.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

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

/** An alarm for one poke step fired. */
@AndroidEntryPoint
class PokeAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var firing: PokeFiring

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val key = PokeIntents.key(intent) ?: return
        runAsync { firing.fire(key) }
    }
}

/** Notification actions: Bitti, Ertele, Bugün olmaz (3a). */
@AndroidEntryPoint
class PokeActionReceiver : BroadcastReceiver() {
    @Inject lateinit var actions: PokeActions

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val key = PokeIntents.key(intent) ?: return
        runAsync {
            when (intent.action) {
                PokeIntents.ACTION_DONE -> actions.done(key)
                PokeIntents.ACTION_SNOOZE -> actions.snooze(key)
                PokeIntents.ACTION_CANT_TODAY -> actions.cantToday(key)
            }
        }
    }
}

/** Boot, app update, time and time zone changes: set every pending alarm again from Room (ADR 0005). */
@AndroidEntryPoint
class RearmReceiver : BroadcastReceiver() {
    @Inject lateinit var alarms: PokeAlarms

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action !in REARM_ACTIONS) return
        runAsync { alarms.rearm() }
    }

    private companion object {
        val REARM_ACTIONS =
            setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
            )
    }
}
