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
import androidx.core.net.toUri
import app.nuddle.MainActivity
import app.nuddle.R
import app.nuddle.api.model.FinishMode
import app.nuddle.data.db.ConnectionEntity
import app.nuddle.data.db.TaskEntity
import app.nuddle.data.sync.TaskAnnouncer
import app.nuddle.ui.screens.home.DeadlineText
import app.nuddle.ui.screens.home.deadlineText
import app.nuddle.ui.screens.home.timeText
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.datetime.TimeZone
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Instant

/** "son zaman yarın 09:00", or "yarın 09:00" for the moment of an `at` task (open-questions#43). */
internal fun Context.deadlinePhrase(deadline: DeadlineText): String =
    if (deadline.moment) deadlineInline(deadline) else getString(R.string.home_deadline_inline, deadlineInline(deadline))

/** "bugün 18:00", "yarın 09:00", "14 Ekim 09:00": the deadline inside a sentence. */
internal fun Context.deadlineInline(deadline: DeadlineText): String =
    when (deadline) {
        is DeadlineText.Today -> {
            getString(R.string.deadline_today_inline, deadline.time)
        }

        is DeadlineText.Tomorrow -> {
            getString(R.string.deadline_tomorrow_inline, deadline.time)
        }

        is DeadlineText.OnDate -> {
            getString(
                R.string.deadline_on_date,
                deadline.day,
                resources.getStringArray(R.array.month_names)[deadline.month - 1],
                deadline.time,
            )
        }
    }

/** Request codes of 7a's Ara and Mesaj, next to its own. */
private const val CALL = 1
private const val MESSAGE = 2

/**
 * 4b: "Sibel sana bir görev yazdı" on the `tasks` channel. It only says a task is there and that nothing is needed
 * yet; the pokes come with the plan. Gör opens home, where the task is the next-up card (4c). For the sender, "Eren
 * fotoğraf gönderdi" with Gör opening the approval (4f), and "Eren cevap vermiyor" on `reach_out` when the human channel
 * fires (7a).
 */
class AndroidTaskAnnouncer
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val clock: Clock,
        private val timeZone: TimeZone,
    ) : TaskAnnouncer {
        override fun announce(
            task: TaskEntity,
            senderName: String,
        ) {
            val deadline = context.deadlinePhrase(task.timeText(clock.now(), timeZone))
            val body =
                if (task.finishMode == FinishMode.PHOTO.value) {
                    context.getString(R.string.task_arrived_body_photo, task.title, deadline)
                } else {
                    context.getString(R.string.task_arrived_body, task.title, deadline)
                }
            val calm = context.getString(R.string.task_arrived_calm)
            val open =
                PendingIntent.getActivity(
                    context,
                    notificationId(task.id),
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                )
            val notification =
                NotificationCompat
                    .Builder(context, NotificationChannels.TASKS)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setColor(ContextCompat.getColor(context, R.color.poke))
                    .setContentTitle(context.getString(R.string.task_arrived_title, senderName))
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.paragraphs, body, calm)))
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .addAction(0, context.getString(R.string.task_arrived_open), open)
                    .build()
            post(notificationId(task.id), notification)
        }

        override fun photoArrived(
            task: TaskEntity,
            recipientName: String,
        ) {
            val open =
                PendingIntent.getActivity(
                    context,
                    notificationId(task.id),
                    MainActivity.approve(context, task.id),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val notification =
                NotificationCompat
                    .Builder(context, NotificationChannels.TASKS)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setColor(ContextCompat.getColor(context, R.color.poke))
                    .setContentTitle(context.getString(R.string.photo_arrived_title, recipientName))
                    .setContentText(context.getString(R.string.photo_arrived_body, task.title))
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .addAction(0, context.getString(R.string.task_arrived_open), open)
                    .build()
            post(notificationId(task.id), notification)
        }

        override fun cantReach(
            task: TaskEntity,
            recipient: ConnectionEntity,
            pokes: Int,
            fullScreen: Boolean,
        ) {
            val id = NotificationIds.reach(task.id)
            val deadline = context.deadlinePhrase(task.timeText(clock.now(), timeZone))
            val body =
                when {
                    pokes == 0 -> context.getString(R.string.reach_body_no_pokes, task.title, deadline)
                    fullScreen -> context.resources.getQuantityString(R.plurals.reach_body_full_screen, pokes, pokes, task.title, deadline)
                    else -> context.resources.getQuantityString(R.plurals.reach_body, pokes, pokes, task.title, deadline)
                }
            val open =
                PendingIntent.getActivity(
                    context,
                    id,
                    MainActivity.task(context, task.id),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val builder =
                NotificationCompat
                    .Builder(context, NotificationChannels.REACH_OUT)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setColor(ContextCompat.getColor(context, R.color.poke))
                    .setContentTitle(context.getString(R.string.reach_title, recipient.displayName))
                    .setContentText(body)
                    .setStyle(
                        NotificationCompat.BigTextStyle().bigText(
                            context.getString(R.string.paragraphs, body, context.getString(R.string.reach_call_hint)),
                        ),
                    ).setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setGroup(NotificationIds.reachGroup(task.id))
                    .setContentIntent(open)
                    .setAutoCancel(true)
            // The app never calls by itself: Ara opens the dialer with the number (docs/product.md, Human channel).
            recipient.phone?.let { phone ->
                builder.addAction(
                    0,
                    context.getString(R.string.home_call),
                    activity(id + CALL, Intent(Intent.ACTION_DIAL, "tel:$phone".toUri())),
                )
                builder.addAction(
                    0,
                    context.getString(R.string.reach_message),
                    activity(id + MESSAGE, Intent(Intent.ACTION_SENDTO, "smsto:$phone".toUri())),
                )
            }
            builder.addAction(0, context.getString(R.string.task_arrived_open), open)
            post(id, builder.build())
        }

        private fun activity(
            requestCode: Int,
            intent: Intent,
        ): PendingIntent = PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE)

        override fun withdrawCantReach(taskId: String) {
            NotificationManagerCompat.from(context).cancel(NotificationIds.reach(taskId))
        }

        /** Posts unless notifications are not allowed; the check sits next to the call for Android Lint. */
        private fun post(
            id: Int,
            notification: Notification,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            NotificationManagerCompat.from(context).notify(id, notification)
        }

        override fun withdraw(taskId: String) {
            NotificationManagerCompat.from(context).cancel(notificationId(taskId))
        }

        private fun notificationId(taskId: String) = NotificationIds.note(taskId)
    }
