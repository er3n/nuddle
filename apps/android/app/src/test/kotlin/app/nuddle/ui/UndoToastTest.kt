package app.nuddle.ui

import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.nuddle.ui.components.UndoToast
import app.nuddle.ui.theme.DurtTheme
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The undo window (docs/domain.md, Undo toast) is 5 s even with animations off, as E2E emulators run. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class UndoToastTest {
    @get:Rule
    val compose =
        createComposeRule(
            effectContext =
                object : MotionDurationScale {
                    override val scaleFactor = 0f
                },
        )

    @Test
    fun `with animations off the toast still waits its whole window`() {
        var timedOut = false
        compose.mainClock.autoAdvance = false
        compose.setContent { DurtTheme { UndoToast("Kuruldu", "Para çek", {}, {}, { timedOut = true }) } }

        compose.mainClock.advanceTimeBy(4_000)
        assertFalse(timedOut)
        compose.mainClock.advanceTimeBy(1_100)
        assertTrue(timedOut)
    }
}
