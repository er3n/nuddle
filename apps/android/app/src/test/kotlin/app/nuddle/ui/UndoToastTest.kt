package app.nuddle.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.nuddle.ui.components.UndoToast
import app.nuddle.ui.theme.DurtTheme
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun `a tap on the other finish option calls back once and starts the window again (NUD-137)`() {
        var timedOut = false
        val taps = mutableListOf<Boolean>()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            var photo by remember { mutableStateOf(false) }
            DurtTheme {
                UndoToast("Gönderildi", "Eren · Para çek", {}, {}, { timedOut = true }, photo = photo, onPhoto = {
                    taps += it
                    photo = it
                })
            }
        }

        compose.mainClock.advanceTimeBy(4_000)
        compose.onNodeWithText("Bitti").performClick()
        compose.onNodeWithText("Fotoğrafla").performClick()
        assertEquals(listOf(true), taps, "the current choice sends nothing")

        compose.mainClock.advanceTimeBy(4_000)
        assertFalse(timedOut, "the window started again at the switch")
        compose.mainClock.advanceTimeBy(1_100)
        assertTrue(timedOut)
    }
}
