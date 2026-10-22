package app.nuddle.alarm

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.nuddle.data.avatar.AvatarFiles
import app.nuddle.data.db.TaskEntity
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/** NUD-124: a ringing poke says the task while it is up, and the voice stops with the poke. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PokeSpeechWiringTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val speech = RecordingSpeech()
    private val notifications =
        AndroidPokeNotifications(
            app,
            FakeClock(),
            TimeZone.of("Europe/Istanbul"),
            AlarmVolume(app),
            AvatarFiles(File(app.cacheDir, "avatars-speech-test").apply { deleteRecursively() }),
            speech,
        )

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        NotificationChannels.create(app)
    }

    private val step = PokeStep(Cast.TASK_ID, 1, 2, Instant.parse("2026-10-12T08:00:00+03:00"), PokeLevel.SOUND)

    @Test
    fun `the voice speaks only while the poke is up, and stops when it is answered`() {
        notifications.show(aTask(), step, PokeLevel.SOUND)
        assertEquals(listOf(Cast.TASK_ID to PokeLevel.SOUND), speech.started)
        assertTrue(speech.ringing())

        notifications.cancel(Cast.TASK_ID)
        assertEquals(listOf(Cast.TASK_ID), speech.stopped)
        assertFalse(speech.ringing())
    }

    private class RecordingSpeech : PokeSpeech {
        val started = mutableListOf<Pair<String, PokeLevel>>()
        val stopped = mutableListOf<String>()
        var ringing: () -> Boolean = { false }

        override fun start(
            task: TaskEntity,
            level: PokeLevel,
            ringing: () -> Boolean,
        ) {
            started += task.id to level
            this.ringing = ringing
        }

        override fun stop(taskId: String) {
            stopped += taskId
        }
    }
}
