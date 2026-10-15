package app.nuddle.alarm

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.core.content.getSystemService
import androidx.test.core.app.ApplicationProvider
import app.nuddle.domain.PokeLevel
import app.nuddle.domain.PokeStep
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
import kotlin.time.Instant

/** The poke notification (screen 3a): the step's 1-based `seq` as "N. dürtme", and Ertele from the snooze count. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PokeNotificationsTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val notifications = AndroidPokeNotifications(app, FakeClock(), TimeZone.of("Europe/Istanbul"), AlarmVolume(app))

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        NotificationChannels.create(app)
    }

    private fun show(
        seq: Int,
        snoozesUsed: Int = 0,
        finishMode: String = "photo",
    ): Notification {
        val step = PokeStep(Cast.TASK_ID, 1, seq, Instant.parse("2026-10-12T08:00:00+03:00"), PokeLevel.SOUND)
        notifications.show(aTask().copy(snoozesUsed = snoozesUsed, finishMode = finishMode), step, PokeLevel.SOUND)
        return shadowOf(app.getSystemService<NotificationManager>()).allNotifications.single()
    }

    private fun Notification.actionTitles() = actions.map { it.title.toString() }

    @Test
    fun `the pokes that are up name their tasks, until they are cancelled (NUD-64)`() {
        show(seq = 1)
        assertEquals(setOf(Cast.TASK_ID), notifications.showing())
        notifications.cancel(Cast.TASK_ID)
        assertEquals(emptySet(), notifications.showing())
    }

    @Test
    fun `the first step says 1 dürtme`() {
        assertEquals("1. dürtme", show(seq = 1).extras.getString(Notification.EXTRA_SUB_TEXT))
    }

    @Test
    fun `the third step says 3 dürtme`() {
        assertEquals("3. dürtme", show(seq = 3).extras.getString(Notification.EXTRA_SUB_TEXT))
    }

    @Test
    fun `Ertele says when it rings again (NUD-63), and son hakkın on the last one`() {
        // 08:00 now, deadline 09:00: 15 minutes, then 10.
        assertEquals(listOf("Bitti", "Ertele · 08:15", "Bugün olmaz"), show(seq = 1).actionTitles())
    }

    @Test
    fun `the second snooze is the last`() {
        assertEquals(listOf("Bitti", "Ertele · 08:10 · son hakkın", "Bugün olmaz"), show(seq = 2, snoozesUsed = 1).actionTitles())
    }

    @Test
    fun `no Ertele once both snoozes are used`() {
        assertEquals(listOf("Bitti", "Bugün olmaz"), show(seq = 3, snoozesUsed = 2).actionTitles())
    }

    @Test
    fun `Bitti on a photo task opens the camera, on a Bitti task it closes the task`() {
        val photo = show(seq = 1).actions.first().actionIntent
        assertTrue(photo.isActivity)
        assertEquals("nuddle://photo/${Cast.TASK_ID}", shadowOf(photo).savedIntent.dataString)

        app.getSystemService<NotificationManager>()!!.cancelAll()
        val done = show(seq = 1, finishMode = "done").actions.first().actionIntent
        assertTrue(done.isBroadcast)
        assertEquals(PokeIntents.ACTION_DONE, shadowOf(done).savedIntent.action)
    }

    @Test
    fun `a poke is in a group of its own and takes the task's earlier note away`() {
        AndroidTaskAnnouncer(app, FakeClock(), TimeZone.of("Europe/Istanbul")).announce(aTask(), "Sibel")
        val poke = show(seq = 1)

        // In the app's automatic group a poke would be silent and its full screen would never open (E2E smoke 3).
        assertEquals("poke:${Cast.TASK_ID}", poke.group)
        assertEquals(listOf(poke.channelId), shadowOf(app.getSystemService<NotificationManager>()).allNotifications.map { it.channelId })
    }
}
