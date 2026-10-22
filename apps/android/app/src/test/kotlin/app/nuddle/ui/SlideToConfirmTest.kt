package app.nuddle.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.Density
import app.nuddle.domain.TimeLeft
import app.nuddle.ui.components.SlideToConfirm
import app.nuddle.ui.screens.poke.PokeScreen
import app.nuddle.ui.screens.poke.PokeUiState
import app.nuddle.ui.screens.poke.SnoozeUi
import app.nuddle.ui.theme.DurtTheme
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals

/** Bitti over the lock screen is a slide (NUD-131, docs/ux/screens.md 3b, components.md 10). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class SlideToConfirmTest {
    @get:Rule
    val compose = createComposeRule()

    private var confirmed = 0

    private fun slider(fontScale: Float = 1f) {
        compose.setContent {
            DurtTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    SlideToConfirm(
                        text = "Kaydırarak bitir",
                        accessibilityLabel = "Bitti",
                        onConfirm = { confirmed++ },
                        modifier = Modifier.testTag(TAG),
                    )
                }
            }
        }
    }

    @Test
    fun `a tap does not confirm`() {
        slider()
        compose.onNodeWithTag(TAG).performTouchInput { click() }
        compose.onNodeWithTag(TAG).performTouchInput { click(centerLeft) }
        compose.waitForIdle()
        assertEquals(0, confirmed)
    }

    @Test
    fun `a short slide springs back without confirming`() {
        slider()
        compose.onNodeWithTag(TAG).performTouchInput { swipeRight(startX = left, endX = centerX) }
        compose.waitForIdle()
        assertEquals(0, confirmed)
    }

    @Test
    fun `a slide to the end confirms once`() {
        slider()
        compose.onNodeWithTag(TAG).performTouchInput { swipeRight(startX = left, endX = right) }
        compose.onNodeWithTag(TAG).performTouchInput { swipeRight(startX = left, endX = right) }
        compose.waitForIdle()
        assertEquals(1, confirmed)
    }

    @Test
    fun `a slide to the end confirms at 200 percent font`() {
        slider(fontScale = 2f)
        compose.onNodeWithTag(TAG).performTouchInput { swipeRight(startX = left, endX = right) }
        compose.waitForIdle()
        assertEquals(1, confirmed)
    }

    @Test
    fun `TalkBack sees a button named Bitti whose click confirms`() {
        slider()
        compose
            .onNodeWithTag(TAG)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Bitti")))
            .performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, confirmed)
    }

    @Composable
    private fun Poke(
        locked: Boolean,
        onDone: () -> Unit,
        onSnooze: () -> Unit,
    ) {
        DurtTheme {
            PokeScreen(
                state =
                    PokeUiState(
                        pokeNumber = 3,
                        time = "08:15",
                        title = "Para çek",
                        detail = null,
                        timeLeft = TimeLeft.Minutes(45),
                        snooze = SnoozeUi(time = "08:25", last = false),
                        locked = locked,
                    ),
                onDone = onDone,
                onSnooze = onSnooze,
                onCantToday = {},
            )
        }
    }

    @Test
    fun `over the lock screen a tap on Bitti does nothing, a slide finishes, Ertele stays a tap`() {
        var done = 0
        var snoozed = 0
        compose.setContent { Poke(locked = true, onDone = { done++ }, onSnooze = { snoozed++ }) }

        compose.onNodeWithTag(TestTags.POKE_DONE).performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(0, done)

        compose.onNodeWithTag(TestTags.POKE_DONE).performTouchInput { swipeRight(startX = left, endX = right) }
        compose.waitForIdle()
        assertEquals(1, done)

        compose.onNodeWithTag(TestTags.POKE_SNOOZE).performClick()
        assertEquals(1, snoozed)
    }

    @Test
    fun `on an unlocked phone Bitti is a tap`() {
        var done = 0
        compose.setContent { Poke(locked = false, onDone = { done++ }, onSnooze = {}) }
        compose.onNodeWithTag(TestTags.POKE_DONE).performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(1, done)
    }

    private companion object {
        const val TAG = "slide"
    }
}
