package app.nuddle.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.nuddle.MainActivity
import app.nuddle.domain.AlarmKey
import app.nuddle.domain.AlarmScheduler
import app.nuddle.domain.PokeStep
import kotlin.time.Instant

private const val WINDOW_MS = 10 * 60 * 1000L

/**
 * One `setAlarmClock` per step: exact and not deferred by Doze (docs/ux/platform.md). Without the exact alarm
 * permission it falls back to a 10 minute `setWindow`; reachability drops and the bell warns (setup, slice 1).
 */
class AlarmManagerScheduler(
    private val context: Context,
    private val alarmManager: AlarmManager,
    /** From the app's clock to the phone's; only the `e2e` flavor's fake time moves it (TestTime). */
    private val onPhoneClock: (Instant) -> Instant = { it },
) : AlarmScheduler {
    override fun schedule(step: PokeStep) {
        val operation = firePendingIntent(context, step.key, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val at = onPhoneClock(step.plannedAt).toEpochMilliseconds()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            val show =
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                )
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), operation)
        } else {
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MS, operation)
        }
    }

    override fun cancel(key: AlarmKey) {
        // The PendingIntent goes too, so that its existence says whether the alarm is set (isScheduled).
        firePendingIntent(context, key, PendingIntent.FLAG_NO_CREATE)?.let { operation ->
            alarmManager.cancel(operation)
            operation.cancel()
        }
    }

    /**
     * AlarmManager cannot be asked which alarms are set; the step's PendingIntent can. It exists from [schedule] until
     * [cancel], and a force stop or a reboot takes it away together with the alarm.
     */
    override fun isScheduled(key: AlarmKey): Boolean = firePendingIntent(context, key, PendingIntent.FLAG_NO_CREATE) != null
}

internal fun firePendingIntent(
    context: Context,
    key: AlarmKey,
    flags: Int,
): PendingIntent? =
    PendingIntent.getBroadcast(
        context,
        key.hashCode(),
        PokeIntents.fire(context, key),
        flags or PendingIntent.FLAG_IMMUTABLE,
    )
