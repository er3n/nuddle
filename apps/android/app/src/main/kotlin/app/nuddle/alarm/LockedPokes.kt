package app.nuddle.alarm

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.nuddle.MainActivity
import app.nuddle.R
import app.nuddle.domain.AlarmKey
import app.nuddle.domain.AlarmScheduler
import app.nuddle.domain.DirectBootRules
import app.nuddle.domain.FireDecision
import app.nuddle.domain.LockedFire
import app.nuddle.domain.PokeLevel
import app.nuddle.domain.PokeStep
import app.nuddle.domain.QuietHours
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.datetime.TimeZone
import javax.inject.Inject
import kotlin.time.Clock

/** The poke notification before the first unlock: the task cannot be read yet, so it only says there is one. */
fun interface LockedPokeNotification {
    fun show(
        step: PokeStep,
        level: PokeLevel,
    )
}

/**
 * Device execution after a reboot, before the first unlock (docs/ux/platform.md, Direct boot; NUD-125). Works only from
 * device-protected storage: Room, DataStore and WorkManager cannot be opened yet.
 */
class LockedPokeFiring
    @Inject
    constructor(
        private val store: DirectBootStore,
        private val scheduler: AlarmScheduler,
        private val notification: LockedPokeNotification,
        private val clock: Clock,
        private val timeZone: TimeZone,
    ) {
        /** `LOCKED_BOOT_COMPLETED`: the stored pokes get their alarms again. */
        suspend fun rearm(): Int {
            val fired = store.fires().map { it.key }.toSet()
            val toArm = DirectBootRules.stepsToArm(store.plan(), fired, clock.now())
            toArm.forEach(scheduler::schedule)
            return toArm.size
        }

        suspend fun fire(key: AlarmKey): FireDecision? {
            val plan = store.plan()
            val locked = plan.steps.firstOrNull { it.step.key == key } ?: return null
            if (store.fires().any { it.key == key }) return null
            val now = clock.now()
            val decision = DirectBootRules.decideFire(locked, plan.quietHours ?: QuietHours.default(timeZone), now)
            if (decision is FireDecision.Ring) {
                notification.show(locked.step, decision.level)
                store.recordFire(LockedFire(key, now, decision.level))
            }
            return decision
        }
    }

/**
 * Same channel, id and group as the full poke (AndroidPokeNotifications), so a sync after the unlock can take it away
 * and the handover can replace it. No buttons: Bitti and the others need the task from Room. The volume is not raised:
 * the level before is kept with the app's other preferences, which cannot be read yet.
 */
class AndroidLockedPokeNotification
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : LockedPokeNotification {
        override fun show(
            step: PokeStep,
            level: PokeLevel,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val silent = level == PokeLevel.SILENT
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val notification =
                NotificationCompat
                    .Builder(context, if (silent) NotificationChannels.POKE_SILENT else NotificationChannels.POKE_ALARM)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setColor(ContextCompat.getColor(context, R.color.poke))
                    .setContentTitle(context.getString(R.string.poke_locked_title))
                    .setContentText(context.getString(R.string.poke_locked_text))
                    .setSubText(context.getString(R.string.poke_count, step.number))
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setPriority(if (silent) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(open)
                    .setGroup(NotificationIds.pokeGroup(step.taskId))
                    .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_ALL)
                    .build()
            // Rings until silenced, like the full poke (docs/domain.md, Poke levels).
            if (!silent) notification.flags = notification.flags or Notification.FLAG_INSISTENT
            NotificationManagerCompat.from(context).notify(NotificationIds.poke(step.taskId), notification)
        }
    }
