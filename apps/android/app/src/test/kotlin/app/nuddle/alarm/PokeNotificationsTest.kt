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
import kotlin.time.Instant

/** The poke notification (screen 3a) shows the step's 1-based `seq` as "N. dürtme". */
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

    private fun show(seq: Int): Notification {
        val step = PokeStep(Cast.TASK_ID, 1, seq, Instant.parse("2026-10-12T08:00:00+03:00"), PokeLevel.SOUND)
        notifications.show(aTask(), step, PokeLevel.SOUND)
        return shadowOf(app.getSystemService<NotificationManager>()).allNotifications.single()
    }

    @Test
    fun `the first step says 1 dürtme`() {
        assertEquals("1. dürtme", show(seq = 1).extras.getString(Notification.EXTRA_SUB_TEXT))
    }

    @Test
    fun `the third step says 3 dürtme`() {
        assertEquals("3. dürtme", show(seq = 3).extras.getString(Notification.EXTRA_SUB_TEXT))
    }
}
