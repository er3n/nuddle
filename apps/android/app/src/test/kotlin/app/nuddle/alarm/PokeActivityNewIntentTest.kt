package app.nuddle.alarm

import android.app.Application
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import app.nuddle.domain.AlarmKey
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 3b is singleTop: a full screen poke while it is up takes the screen over (NUD-157); a volume key silences (NUD-155). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PokeActivityNewIntentTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `the next full screen poke takes over the screen and its answers`() {
        val first = AlarmKey("task-a", 1, 3)
        val next = AlarmKey("task-b", 2, 1)
        val screen = Robolectric.buildActivity(PokeActivity::class.java, PokeIntents.fullScreen(app, first)).create()
        assertEquals(first, screen.get().shown)

        screen.newIntent(PokeIntents.fullScreen(app, next))

        assertEquals(next, screen.get().shown)
        assertEquals(next, PokeIntents.key(screen.get().intent))
    }

    @Test
    fun `the first volume key silences, later ones change the volume, a newer poke can be silenced again (NUD-155)`() {
        val screen = Robolectric.buildActivity(PokeActivity::class.java, PokeIntents.fullScreen(app, AlarmKey("task-a", 1, 3))).create()
        val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN)

        assertTrue(screen.get().onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN, down))
        assertFalse(screen.get().onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN, down))

        screen.newIntent(PokeIntents.fullScreen(app, AlarmKey("task-a", 1, 4)))
        assertTrue(screen.get().onKeyDown(KeyEvent.KEYCODE_VOLUME_UP, down))
    }
}
