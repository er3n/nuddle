package app.nuddle.alarm

import android.Manifest
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
import app.nuddle.data.db.TaskEntity
import app.nuddle.domain.PokeLevel
import app.nuddle.domain.PokeRules
import app.nuddle.domain.PokeStep
import app.nuddle.domain.TimeLeft
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Instant

internal fun Context.timeLeftText(timeLeft: TimeLeft): String =
    when (timeLeft) {
        is TimeLeft.Minutes -> getString(R.string.duration_minutes, timeLeft.value)
        is TimeLeft.Hours -> resources.getQuantityString(R.plurals.duration_hours, timeLeft.value, timeLeft.value)
    }

/**
 * The poke notification (screen 3a, docs/ux/components.md, 7): title is the task, text is the time to the deadline,
 * at most three actions. `SILENT` goes to `poke_silent`, `SOUND` and `FULL_SCREEN` to `poke_alarm`; `FULL_SCREEN`
 * also opens [PokeActivity] over the lock screen. The sender's avatar as large icon arrives with tasks for others.
 */
class AndroidPokeNotifications
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val clock: Clock,
    ) : PokeNotifications {
        override fun show(
            task: TaskEntity,
            step: PokeStep,
            level: PokeLevel,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val silent = level == PokeLevel.SILENT
            val fullScreen = level == PokeLevel.FULL_SCREEN
            val channel = if (silent) NotificationChannels.POKE_SILENT else NotificationChannels.POKE_ALARM
            val title = task.detail?.let { context.getString(R.string.separator_dot, task.title, it) } ?: task.title
            val timeLeft = PokeRules.timeLeft(clock.now(), Instant.fromEpochMilliseconds(task.deadline))
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val builder =
                NotificationCompat
                    .Builder(context, channel)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setColor(ContextCompat.getColor(context, R.color.poke))
                    .setContentTitle(title)
                    .setContentText(context.getString(R.string.poke_time_left_sentence, context.timeLeftText(timeLeft)))
                    .setSubText(context.getString(R.string.poke_count, step.number))
                    .setCategory(if (fullScreen) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
                    .setPriority(if (silent) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(open)
                    .addPokeActions(step)
            val manager = NotificationManagerCompat.from(context)
            if (fullScreen) {
                builder.setFullScreenIntent(fullScreenIntent(step), true).setOngoing(true)
                // SystemUI launches a full screen intent only for a newly posted notification, never for an update of
                // the earlier step's notification with the same id (found by E2E smoke 1), so that one goes first.
                manager.cancel(notificationId(task.id))
            }
            manager.notify(notificationId(task.id), builder.build())
        }

        private fun fullScreenIntent(step: PokeStep): PendingIntent =
            PendingIntent.getActivity(
                context,
                step.key.hashCode(),
                PokeIntents.fullScreen(context, step.key),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        override fun cancel(taskId: String) {
            NotificationManagerCompat.from(context).cancel(notificationId(taskId))
        }

        /** At most three actions (docs/ux/components.md, 7): Bitti, Ertele, Bugün olmaz. */
        private fun NotificationCompat.Builder.addPokeActions(step: PokeStep): NotificationCompat.Builder =
            addAction(0, context.getString(R.string.poke_done), action(PokeIntents.ACTION_DONE, step))
                .addAction(0, context.getString(R.string.poke_snooze, SNOOZE_MINUTES), action(PokeIntents.ACTION_SNOOZE, step))
                .addAction(0, context.getString(R.string.poke_cant_today_short), action(PokeIntents.ACTION_CANT_TODAY, step))

        private fun action(
            action: String,
            step: PokeStep,
        ): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                step.key.hashCode(),
                PokeIntents.action(context, action, step.key),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun notificationId(taskId: String) = taskId.hashCode()

        private companion object {
            // TODO(open-questions#1): the second snooze is 10 minutes and says "son hakkın".
            const val SNOOZE_MINUTES = 15
        }
    }
