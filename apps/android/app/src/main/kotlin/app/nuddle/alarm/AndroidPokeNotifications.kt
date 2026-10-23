package app.nuddle.alarm

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import app.nuddle.data.example.ExamplePictures
import app.nuddle.data.photo.circled
import app.nuddle.domain.PokeLevel
import app.nuddle.domain.PokeRules
import app.nuddle.domain.PokeStep
import app.nuddle.domain.PokeTitles
import app.nuddle.domain.Snooze
import app.nuddle.domain.SnoozeRules
import app.nuddle.domain.TimeLeft
import app.nuddle.ui.screens.hourMinute
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.datetime.TimeZone
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Instant

/** The sender's example photo the phone keeps for the task (NUD-138); none when it did not arrive yet. */
private fun ExamplePictures.bitmapOf(taskId: String): Bitmap? =
    runCatching { current(taskId) }.getOrNull()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }

/** Opens the full screen poke (3b) for [step]. */
private fun Context.fullScreenIntent(step: PokeStep): PendingIntent =
    PendingIntent.getActivity(
        this,
        step.key.hashCode(),
        PokeIntents.fullScreen(this, step.key),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

/**
 * A tap on the poke: a full screen poke opens 3b, as on an alarm clock, since a phone in use shows only the
 * notification (NUD-156); any other opens the app.
 */
private fun Context.pokeContentIntent(
    step: PokeStep,
    level: PokeLevel,
): PendingIntent =
    if (level == PokeLevel.FULL_SCREEN) {
        fullScreenIntent(step)
    } else {
        PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
    }

/** The sender's cached picture as a circle (components.md 7, NUD-80); none for a task to self or without one. */
private fun AvatarFiles.senderPicture(task: TaskEntity) =
    of(task.senderId)
        .takeIf { task.senderId != task.recipientId && it.exists() }
        ?.let { BitmapFactory.decodeFile(it.path) }
        ?.circled()

internal fun Context.timeLeftText(timeLeft: TimeLeft): String =
    when (timeLeft) {
        is TimeLeft.Minutes -> getString(R.string.duration_minutes, timeLeft.value)
        is TimeLeft.Hours -> resources.getQuantityString(R.plurals.duration_hours, timeLeft.value, timeLeft.value)
    }

/** The poke title for [step] ringing at [level] (NUD-130, glossary.md, Poke titles): the same on 3a and 3b. */
internal fun Context.pokeTitle(
    step: PokeStep,
    level: PokeLevel,
): String {
    val titles =
        resources.getStringArray(
            when (level) {
                PokeLevel.SILENT -> R.array.poke_titles_silent
                PokeLevel.SOUND -> R.array.poke_titles_sound
                PokeLevel.FULL_SCREEN -> R.array.poke_titles_full_screen
            },
        )
    return titles[PokeTitles.index(step, titles.size)]
}

/**
 * The poke notification (screen 3a, docs/ux/components.md, 7): title is the poke title (NUD-130), text is the task,
 * the expanded text adds the time to the deadline; a one-time message keeps the message as its title. At most three
 * actions. `SILENT` goes to `poke_silent`, `SOUND` and `FULL_SCREEN` to `poke_alarm`; `FULL_SCREEN`
 * also opens [PokeActivity] over the lock screen. The sender's avatar as large icon arrives with tasks for others.
 * `SOUND` and `FULL_SCREEN` also say the task aloud through [PokeSpeech] (NUD-124).
 */
class AndroidPokeNotifications
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val clock: Clock,
        private val timeZone: TimeZone,
        private val volume: AlarmVolume,
        private val avatars: AvatarFiles,
        private val speech: PokeSpeech = PokeSpeech.None,
        private val examples: ExamplePictures = ExamplePictures.None,
    ) : PokeNotifications {
        override fun show(
            task: TaskEntity,
            step: PokeStep,
            level: PokeLevel,
        ) = post(task, step, level, quiet = false)

        /**
         * A volume key on 3b (NUD-155): the poke stays with its title and answers, but on `poke_silent`, without the
         * full screen intent and the voice. The notification goes first, since only taking it away stops a tone that
         * rings; the next step rings as planned.
         */
        override fun silence(
            task: TaskEntity,
            step: PokeStep,
            level: PokeLevel,
        ) {
            NotificationManagerCompat.from(context).cancel(notificationId(task.id))
            speech.stop(task.id)
            if ((showing() - task.id).isEmpty()) volume.restore()
            post(task, step, level, quiet = true)
        }

        private fun post(
            task: TaskEntity,
            step: PokeStep,
            level: PokeLevel,
            quiet: Boolean,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val silent = level == PokeLevel.SILENT || quiet
            val fullScreen = level == PokeLevel.FULL_SCREEN && !quiet
            val channel = if (silent) NotificationChannels.POKE_SILENT else NotificationChannels.POKE_ALARM
            val notice = task.finishMode == FinishMode.NOTICE.value
            // The sender's example photo takes the large icon's place and expands as a big picture (NUD-138).
            val example = if (notice) null else examples.bitmapOf(task.id)
            val builder =
                NotificationCompat
                    .Builder(context, channel)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setColor(ContextCompat.getColor(context, R.color.poke))
                    .setLargeIcon(example ?: avatars.senderPicture(task))
                    .setPokeContent(task, step, level, notice, example)
                    .setSubText(context.getString(R.string.poke_count, step.number))
                    .setCategory(if (fullScreen) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
                    .setPriority(if (silent) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(context.pokeContentIntent(step, level))
                    // Swiped away: the voice stops and the alarm volume comes back at once (NUD-154).
                    .setDeleteIntent(action(PokeIntents.ACTION_DISMISSED, step))
                    .setGroup(NotificationIds.pokeGroup(task.id))
                    .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_ALL)
                    .addPokeActions(
                        step,
                        SnoozeRules.next(task.snoozesUsed, clock.now(), Instant.fromEpochMilliseconds(task.deadline)),
                        photo = task.finishMode == FinishMode.PHOTO.value,
                        notice = notice,
                    )
            val manager = NotificationManagerCompat.from(context)
            if (fullScreen) {
                builder.setFullScreenIntent(context.fullScreenIntent(step), true).setOngoing(true)
                // SystemUI launches a full screen intent only for a newly posted notification, never for an update of
                // the earlier step's notification with the same id (found by E2E smoke 1), so that one goes first.
                manager.cancel(notificationId(task.id))
            }
            // The poke takes over from the note that the task arrived ("Zamanı gelince seni dürterim", 4b), and from
            // "Zaten yaptın mı?" before it (NUD-129).
            manager.cancel(NotificationIds.note(task.id))
            manager.cancel(NotificationIds.headsUp(task.id))
            if (!silent) volume.raise()
            val notification = builder.build()
            // SOUND and FULL_SCREEN ring until silenced (docs/domain.md, Poke levels): without the flag the channel plays
            // the alarm tone once and stops, which a tester heard as one soft ring and no more.
            if (!silent) notification.flags = notification.flags or Notification.FLAG_INSISTENT
            manager.notify(notificationId(task.id), notification)
            // The task said aloud between the tone while this poke is up (NUD-124); a silent step says nothing.
            if (!quiet) speech.start(task, level) { task.id in showing() }
        }

        /**
         * "Zaten yaptın mı?" (NUD-129, glossary.md): on `poke_silent`, the task's title as text, Bitti only, no time and no
         * poke count (the plan stays hidden). It goes by itself when [step] is due, which takes it away when it rings.
         */
        override fun showHeadsUp(
            task: TaskEntity,
            step: PokeStep,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val done =
                if (task.finishMode == FinishMode.PHOTO.value) {
                    PendingIntent.getActivity(
                        context,
                        step.key.hashCode(),
                        MainActivity.takePhoto(context, step.key.taskId),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                } else {
                    action(PokeIntents.ACTION_HEADS_UP_DONE, step)
                }
            val notification =
                NotificationCompat
                    .Builder(context, NotificationChannels.POKE_SILENT)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setColor(ContextCompat.getColor(context, R.color.poke))
                    .setContentTitle(context.getString(R.string.heads_up_title))
                    .setContentText(task.title)
                    .setLargeIcon(avatars.senderPicture(task))
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setSilent(true)
                    .setContentIntent(open)
                    .setGroup(NotificationIds.headsUpGroup(task.id))
                    .setTimeoutAfter((step.plannedAt - clock.now()).inWholeMilliseconds.coerceAtLeast(0))
                    .addAction(0, context.getString(R.string.poke_done), done)
                    .build()
            NotificationManagerCompat.from(context).notify(NotificationIds.headsUp(task.id), notification)
        }

        override fun headsUps(): Set<String> =
            NotificationManagerCompat
                .from(context)
                .activeNotifications
                .mapNotNull { it.notification.group?.let(NotificationIds::taskOfHeadsUpGroup) }
                .toSet()

        override fun cancel(taskId: String) {
            NotificationManagerCompat.from(context).cancel(notificationId(taskId))
            NotificationManagerCompat.from(context).cancel(NotificationIds.headsUp(taskId))
            speech.stop(taskId)
            // The person's alarm volume comes back once no other poke is up (NUD-68). The system applies a cancel later,
            // so the poke just taken away may still be listed and does not count (NUD-159).
            if ((showing() - taskId).isEmpty()) volume.restore()
        }

        // Each poke is in a group of its own task (NotificationIds.pokeGroup), which names the task.
        override fun showing(): Set<String> =
            NotificationManagerCompat
                .from(context)
                .activeNotifications
                .mapNotNull { it.notification.group?.let(NotificationIds::taskOfPokeGroup) }
                .toSet()

        /**
         * Title and text (3a, NUD-130): the poke title over the task, the time line in the expanded text. A one-time
         * message has nothing to do, so the message stays the title (glossary.md, Poke titles).
         */
        private fun NotificationCompat.Builder.setPokeContent(
            task: TaskEntity,
            step: PokeStep,
            level: PokeLevel,
            notice: Boolean,
            example: Bitmap?,
        ): NotificationCompat.Builder {
            val taskText = task.detail?.let { context.getString(R.string.separator_dot, task.title, it) } ?: task.title
            val time =
                when {
                    notice -> {
                        context.getString(R.string.home_finish_notice)
                    }

                    task.isMoment -> {
                        context.getString(R.string.poke_moment_now)
                    }

                    else -> {
                        val timeLeft = PokeRules.timeLeft(clock.now(), Instant.fromEpochMilliseconds(task.deadline))
                        context.getString(R.string.poke_time_left_sentence, context.timeLeftText(timeLeft))
                    }
                }
            if (notice) return setContentTitle(taskText).setContentText(time)
            val expanded = context.getString(R.string.poke_text_expanded, taskText, time)
            return setContentTitle(context.pokeTitle(step, level))
                .setContentText(taskText)
                .setStyle(
                    if (example != null) {
                        NotificationCompat
                            .BigPictureStyle()
                            .bigPicture(example)
                            .bigLargeIcon(null as Bitmap?)
                            .setSummaryText(expanded)
                    } else {
                        NotificationCompat.BigTextStyle().bigText(expanded)
                    },
                )
        }

        /**
         * At most three actions (docs/ux/components.md, 7): Bitti, Ertele, Bugün olmaz. Ertele shows the length of the
         * snooze it gives and "son hakkın" on the last one; it is left out when none is left (NUD-36).
         */
        private fun NotificationCompat.Builder.addPokeActions(
            step: PokeStep,
            snooze: Snooze?,
            photo: Boolean,
            notice: Boolean,
        ): NotificationCompat.Builder {
            // A one-time message (NUD-79): "Tamam" and nothing else.
            if (notice) return addAction(0, context.getString(R.string.poke_ok), action(PokeIntents.ACTION_DONE, step))
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
