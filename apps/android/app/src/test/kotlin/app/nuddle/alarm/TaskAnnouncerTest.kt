package app.nuddle.alarm

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.core.content.getSystemService
import androidx.test.core.app.ApplicationProvider
import app.nuddle.data.sync.TaskClose
import app.nuddle.testing.Cast
import app.nuddle.testing.FakeClock
import app.nuddle.testing.aTask
import app.nuddle.testing.sibelConnection
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
        announcer.announce(aTask(), "Sibel")

        val notification = manager.allNotifications.single()
        assertEquals(NotificationChannels.TASKS, notification.channelId)
        assertEquals("Sibel sana bir görev yazdı", notification.extras.getString(Notification.EXTRA_TITLE))
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
    fun `a close by the other side says who did what, apart from the arrival note (NUD-115)`() {
        announcer.announce(aTask(), "Sibel")
        announcer.closed(aTask(), "Sibel", TaskClose.Cancelled("Ben hallettim"))
        announcer.closed(aTask(id = "missed").copy(finishMode = "done"), "Eren", TaskClose.Missed)

        val texts =
            manager.allNotifications.map {
                it.extras.getString(Notification.EXTRA_TITLE) to it.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            }
        assertEquals(3, texts.size)
        assertTrue("Sibel iptal etti: \"Temizlikçi için para çek\"" to "Not: Ben hallettim · Dürtmeler durdu" in texts)
        assertTrue("Eren yetiştiremedi: \"Temizlikçi için para çek\"" to "Son zaman geçti, Eren bitti demedi." in texts)
    }

    @Test
    fun `a task finished by Bitti does not ask for a photo`() {
        announcer.announce(aTask().copy(finishMode = "done"), "Sibel")

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
        announcer.announce(aTask(), "Sibel")
        announcer.withdraw(Cast.TASK_ID)

        assertTrue(manager.allNotifications.isEmpty())
    }

    private val toSibel = aTask().copy(senderId = Cast.EREN_ID, recipientId = Cast.SIBEL_ID)

    @Test
    fun `7a says who does not answer, how hard I tried, and offers Ara, Mesaj and Gör`() {
        announcer.cantReach(toSibel, sibelConnection(), pokes = 4, fullScreen = true)

        val notification = manager.allNotifications.single()
        assertEquals(NotificationChannels.REACH_OUT, notification.channelId)
        assertEquals("Sibel cevap vermiyor", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(
            "Temizlikçi için para çek · son zaman bugün 09:00. 4 kez dürttüm, tam ekran dahil, cevap yok.",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
        assertTrue(
            notification.extras
                .getCharSequence(Notification.EXTRA_BIG_TEXT)
                .toString()
                .endsWith("Bir arasan iyi olur. Ben dürtmeye devam ediyorum."),
        )
        assertEquals(listOf("Ara", "Mesaj", "Gör"), notification.actions.map { it.title.toString() })
        assertEquals(NotificationIds.reachGroup(Cast.TASK_ID), notification.group)
    }

    @Test
    fun `7a without a number has only Gör, and goes away on its own`() {
        announcer.photoArrived(toSibel, "Sibel")
        announcer.cantReach(toSibel, sibelConnection(phone = null), pokes = 2, fullScreen = false)

        val reach = manager.allNotifications.single { it.channelId == NotificationChannels.REACH_OUT }
        assertEquals(
            "Temizlikçi için para çek · son zaman bugün 09:00. 2 kez dürttüm, cevap yok.",
            reach.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
        assertEquals(listOf("Gör"), reach.actions.map { it.title.toString() })

        announcer.withdrawCantReach(Cast.TASK_ID)
        assertEquals(listOf(NotificationChannels.TASKS), manager.allNotifications.map { it.channelId })
    }
}
