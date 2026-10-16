package app.nuddle.ui.nav

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A detail screen whose task closes is told to close again by every later write (the done command's sync), while its
 * exit animation still runs. Going back for each of them popped home too and left a blank window (the white screen
 * after "Bitti" in the first internal test).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackOnCloseTest {
    @get:Rule
    val compose = createComposeRule()

    private val close = Channel<Unit>(Channel.UNLIMITED)

    /** One flow for the life of the screen, as a view model holds it. */
    private val closes = close.receiveAsFlow()
    private lateinit var nav: NavHostController

    private fun showDetailOverHome() {
        compose.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = "home") {
                composable("home") {}
                composable("detail") { BackOnClose(closes, onBack = { nav.popBackStack() }) }
            }
        }
        compose.runOnIdle { nav.navigate("detail") }
        compose.waitForIdle()
    }

    @Test
    fun `one close goes back to home`() {
        showDetailOverHome()

        close.trySend(Unit)
        compose.waitForIdle()

        assertEquals("home", nav.currentDestination?.route)
    }

    @Test
    fun `a close that comes while the route is still entering goes back once it is in`() {
        compose.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = "home") {
                composable("home") {}
                composable("detail") { BackOnClose(closes, onBack = { nav.popBackStack() }) }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { nav.navigate("detail") }
        // An already closed task: its view model says close once, at the first frame of the enter animation.
        compose.mainClock.advanceTimeByFrame()
        close.trySend(Unit)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        assertEquals("home", nav.currentDestination?.route)
        assertEquals(listOf("home"), nav.currentBackStack.value.mapNotNull { it.destination.route })
    }

    @Test
    fun `closes that follow during the exit never pop home`() {
        showDetailOverHome()
        compose.mainClock.autoAdvance = false

        close.trySend(Unit)
        compose.mainClock.advanceTimeByFrame()
        close.trySend(Unit)
        close.trySend(Unit)
        compose.mainClock.advanceTimeBy(1_000)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        assertEquals("home", nav.currentDestination?.route)
        assertEquals(listOf("home"), nav.currentBackStack.value.mapNotNull { it.destination.route })
    }
}
