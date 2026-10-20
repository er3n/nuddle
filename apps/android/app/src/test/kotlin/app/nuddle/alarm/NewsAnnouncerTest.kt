package app.nuddle.alarm

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.core.content.getSystemService
import androidx.test.core.app.ApplicationProvider
import app.nuddle.data.db.AnnouncementEntity
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A message from Nuddle on the `news` channel (ADR 0025). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NewsAnnouncerTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val announcer = AndroidNewsAnnouncer(app)
    private val manager get() = shadowOf(app.getSystemService<NotificationManager>())
    private val premium =
        AnnouncementEntity("n1", "Premium geldi", "SMS ile dürtme artık Premium'da.", "**Yeni**", null, null, sentAt = 1)

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        NotificationChannels.create(app)
    }

    @Test
    fun `shows the title and summary and opens the detail`() {
        announcer.announce(premium)

        val notification = manager.allNotifications.single()
        assertEquals(NotificationChannels.NEWS, notification.channelId)
        assertEquals("Premium geldi", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("SMS ile dürtme artık Premium'da.", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        val intent = shadowOf(notification.contentIntent).savedIntent
        assertEquals("nuddle://announcement/n1", intent.dataString)
    }

    @Test
    fun `withdraw takes the notification away`() {
        announcer.announce(premium)
        announcer.withdraw("n1")
        assertTrue(manager.allNotifications.isEmpty())
    }

    @Test
    fun `the news channel is created with its own name`() {
        val channel = app.getSystemService<NotificationManager>()!!.getNotificationChannel(NotificationChannels.NEWS)
        assertEquals("Nuddle'dan haberler", channel.name.toString())
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
    }

    @Test
    fun `nothing is posted without the notification permission`() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        announcer.announce(premium)
        assertTrue(manager.allNotifications.isEmpty())
    }
}
