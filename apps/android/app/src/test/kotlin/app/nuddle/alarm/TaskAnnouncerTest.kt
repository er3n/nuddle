package app.nuddle.alarm

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.core.content.getSystemService
import androidx.test.core.app.ApplicationProvider
import app.nuddle.testing.Cast
import app.nuddle.testing.FakeClock
import app.nuddle.testing.aTask
import kotlinx.datetime.TimeZone
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 4b: a task from someone else arrived (docs/ux/screens.md; open-questions#34 for the rephrased time). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TaskAnnouncerTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val announcer = AndroidTaskAnnouncer(app, FakeClock(), TimeZone.of("Europe/Istanbul"))
    private val manager get() = shadowOf(app.getSystemService<NotificationManager>())

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        NotificationChannels.create(app)
    }

    @Test
    fun `says who wrote what, by when, and that nothing is needed yet`() {
        announcer.announce(aTask(), "Selin")

        val notification = manager.allNotifications.single()
        assertEquals(NotificationChannels.TASKS, notification.channelId)
        assertEquals("Selin sana bir görev yazdı", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(
            "Temizlikçi için para çek · son zaman bugün 09:00. Bitince fotoğrafını istiyor.",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
        assertTrue(
            notification.extras
                .getCharSequence(Notification.EXTRA_BIG_TEXT)
                .toString()
                .endsWith("Bir şey yapman gerekmiyor. Zamanı gelince seni dürterim."),
        )
        assertEquals(listOf("Gör"), notification.actions.map { it.title.toString() })
    }

    @Test
    fun `a task finished by Bitti does not ask for a photo`() {
        announcer.announce(aTask().copy(finishMode = "done"), "Selin")

        assertEquals(
            "Temizlikçi için para çek · son zaman bugün 09:00.",
            manager.allNotifications
                .single()
                .extras
                .getCharSequence(Notification.EXTRA_TEXT)
                .toString(),
        )
    }

    @Test
    fun `withdraw takes the announcement away`() {
        announcer.announce(aTask(), "Selin")
        announcer.withdraw(Cast.TASK_ID)

        assertTrue(manager.allNotifications.isEmpty())
    }
}
