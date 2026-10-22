package app.nuddle.alarm

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.graphics.Bitmap
import androidx.core.content.getSystemService
import androidx.test.core.app.ApplicationProvider
import app.nuddle.data.avatar.AvatarFiles
import app.nuddle.data.example.ExampleFiles
import app.nuddle.data.photo.toJpeg
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
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

/** The sender's example photo on the poke notification (NUD-138, screens.md 3a): a big picture once the phone has it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PokeNotificationExampleTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val avatars = AvatarFiles(File(app.cacheDir, "avatars-example-test").apply { deleteRecursively() })
    private val examples = ExampleFiles(File(app.cacheDir, "examples-test").apply { deleteRecursively() })
    private val notifications =
        AndroidPokeNotifications(app, FakeClock(), TimeZone.of("Europe/Istanbul"), AlarmVolume(app), avatars, examples = examples)
    private val step = PokeStep(Cast.TASK_ID, 1, 1, Instant.parse("2026-10-12T08:00:00+03:00"), PokeLevel.SOUND)

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        NotificationChannels.create(app)
    }

    private fun show(finishMode: String = "done"): Notification {
        app.getSystemService<NotificationManager>()!!.cancelAll()
        notifications.show(aTask().copy(finishMode = finishMode), step, PokeLevel.SOUND)
        return shadowOf(app.getSystemService<NotificationManager>()).allNotifications.single()
    }

    @Test
    fun `without the picture on the phone the notification is as before, big text`() {
        val shown = show()
        assertEquals(Notification.BigTextStyle::class.java.name, shown.extras.getString(Notification.EXTRA_TEMPLATE))
        assertNull(shown.getLargeIcon())
    }

    @Test
    fun `with it the notification expands as a big picture, the picture also the large icon`() {
        examples.save(Cast.TASK_ID, "tasks/${Cast.TASK_ID}/1.jpg", Bitmap.createBitmap(16, 12, Bitmap.Config.ARGB_8888).toJpeg())
        val shown = show()
        assertEquals(Notification.BigPictureStyle::class.java.name, shown.extras.getString(Notification.EXTRA_TEMPLATE))
        assertNotNull(shown.getLargeIcon())
        // The actions do not change: it is never proof (domain.md, Example photo).
        assertEquals(listOf("Bitti", "Bugün olmaz"), shown.actions.map { it.title.toString() }.filterNot { it.startsWith("Ertele") })
    }
}
