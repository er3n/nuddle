package app.nuddle.alarm

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.nuddle.MainActivity
import app.nuddle.R
import app.nuddle.api.model.FinishMode
import app.nuddle.data.avatar.AvatarFiles
import app.nuddle.data.db.TaskEntity
import app.nuddle.data.db.isMoment
import app.nuddle.data.photo.circled
import app.nuddle.domain.PokeLevel
import app.nuddle.domain.PokeRules
import app.nuddle.domain.PokeStep
import app.nuddle.domain.Snooze
import app.nuddle.domain.SnoozeRules
import app.nuddle.domain.TimeLeft
import app.nuddle.ui.screens.hourMinute
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.datetime.TimeZone
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
        private val timeZone: TimeZone,
        private val volume: AlarmVolume,
        private val avatars: AvatarFiles,
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
                    .setLargeIcon(senderPicture(task))
                    .setContentText(
                        if (task.isMoment) {
                            context.getString(R.string.poke_moment_now)
                        } else {
                            context.getString(R.string.poke_time_left_sentence, context.timeLeftText(timeLeft))
                        },
                    ).setSubText(context.getString(R.string.poke_count, step.number))
                    .setCategory(if (fullScreen) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
                    .setPriority(if (silent) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(open)
                    .setGroup(NotificationIds.pokeGroup(task.id))
                    .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_ALL)
                    .addPokeActions(
                        step,
                        SnoozeRules.next(task.snoozesUsed, clock.now(), Instant.fromEpochMilliseconds(task.deadline)),
                        photo = task.finishMode == FinishMode.PHOTO.value,
                    )
            val manager = NotificationManagerCompat.from(context)
            if (fullScreen) {
                builder.setFullScreenIntent(fullScreenIntent(step), true).setOngoing(true)
                // SystemUI launches a full screen intent only for a newly posted notification, never for an update of
                // the earlier step's notification with the same id (found by E2E smoke 1), so that one goes first.
                manager.cancel(notificationId(task.id))
            }
            // The poke takes over from the note that the task arrived ("Zamanı gelince seni dürterim", 4b).
            manager.cancel(NotificationIds.note(task.id))
            if (!silent) volume.raise()
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
            if (showing().isEmpty()) volume.restore()
        }

        // Each poke is in a group of its own task (NotificationIds.pokeGroup), which names the task.
        override fun showing(): Set<String> =
            NotificationManagerCompat
                .from(context)
                .activeNotifications
                .mapNotNull { it.notification.group?.let(NotificationIds::taskOfPokeGroup) }
                .toSet()

        /** The sender's cached picture as a circle (components.md 7, NUD-80); none for a task to self or without one. */
        private fun senderPicture(task: TaskEntity) =
            avatars
                .of(task.senderId)
                .takeIf { task.senderId != task.recipientId && it.exists() }
                ?.let { BitmapFactory.decodeFile(it.path) }
                ?.circled()

        /**
         * At most three actions (docs/ux/components.md, 7): Bitti, Ertele, Bugün olmaz. Ertele shows the length of the
         * snooze it gives and "son hakkın" on the last one; it is left out when none is left (NUD-36).
         */
        private fun NotificationCompat.Builder.addPokeActions(
            step: PokeStep,
            snooze: Snooze?,
            photo: Boolean,
        ): NotificationCompat.Builder {
            // A photo task ends with the photo: Bitti opens the camera (4d) instead of closing the task.
            val done =
                if (photo) {
                    PendingIntent.getActivity(
                        context,
                        step.key.hashCode(),
                        MainActivity.takePhoto(context, step.key.taskId),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                } else {
                    action(PokeIntents.ACTION_DONE, step)
                }
            addAction(0, context.getString(R.string.poke_done), done)
            if (snooze != null) {
                val label = if (snooze.last) R.string.poke_snooze_last else R.string.poke_snooze
                addAction(0, context.getString(label, snooze.until.hourMinute(timeZone)), action(PokeIntents.ACTION_SNOOZE, step))
            }
            return addAction(0, context.getString(R.string.poke_cant_today_short), action(PokeIntents.ACTION_CANT_TODAY, step))
        }

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

        private fun notificationId(taskId: String) = NotificationIds.poke(taskId)
    }
