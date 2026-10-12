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
    private val notifications = AndroidPokeNotifications(app, FakeClock())

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
    fun `the first step says 1 dürtme`() {
        assertEquals("1. dürtme", show(seq = 1).extras.getString(Notification.EXTRA_SUB_TEXT))
    }

    @Test
    fun `the third step says 3 dürtme`() {
        assertEquals("3. dürtme", show(seq = 3).extras.getString(Notification.EXTRA_SUB_TEXT))
    }

    @Test
    fun `Ertele says how long, and son hakkın on the last one`() {
        assertEquals(listOf("Bitti", "15 dk ertele", "Bugün olmaz"), show(seq = 1).actionTitles())
    }

    @Test
    fun `the second snooze is the last`() {
        assertEquals(listOf("Bitti", "10 dk ertele · son hakkın", "Bugün olmaz"), show(seq = 2, snoozesUsed = 1).actionTitles())
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
}
