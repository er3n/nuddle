package app.nuddle.alarm

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.nuddle.MainActivity
import app.nuddle.R
import app.nuddle.data.announcement.NewsAnnouncer
import app.nuddle.data.db.AnnouncementEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** A message from Nuddle on the `news` channel (ADR 0025): its title and summary; a tap opens its detail. */
class AndroidNewsAnnouncer
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : NewsAnnouncer {
        override fun announce(announcement: AnnouncementEntity) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val id = NotificationIds.news(announcement.id)
            val open =
                PendingIntent.getActivity(
                    context,
                    id,
                    MainActivity.announcement(context, announcement.id),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val notification =
                NotificationCompat
                    .Builder(context, NotificationChannels.NEWS)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setColor(ContextCompat.getColor(context, R.color.poke))
                    .setContentTitle(announcement.title)
                    .setContentText(announcement.summary)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(announcement.summary))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build()
            NotificationManagerCompat.from(context).notify(id, notification)
        }

        override fun withdraw(announcementId: String) {
            NotificationManagerCompat.from(context).cancel(NotificationIds.news(announcementId))
        }
    }
