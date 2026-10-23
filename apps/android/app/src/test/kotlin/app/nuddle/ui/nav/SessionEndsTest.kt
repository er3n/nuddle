package app.nuddle.ui.nav

import app.nuddle.testing.InMemorySessionStore
import app.nuddle.testing.aSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The welcome screen follows every end of a session, also one the server ended by rejecting a refresh (NUD-141). */
class SessionEndsTest {
    private val session = aSession()

    @Test
    fun `a session that goes away ends once`() =
        runTest {
            assertEquals(1, flowOf(session, session, null, null).ends().toList().size)
        }

    @Test
    fun `being signed out from the start is no end`() =
        runTest {
            assertEquals(0, flowOf(null, null).ends().toList().size)
        }

    @Test
    fun `signing in again and out again ends twice`() =
        runTest {
            assertEquals(2, flowOf(null, session, null, session, null).ends().toList().size)
        }

    @OptIn(ExperimentalCoroutinesApi::class) // Reason: the collector must see the clear as it happens, like the nav host.
    @Test
    fun `clearing the store while the app is open ends the session`() =
        runTest {
            val sessions = InMemorySessionStore(session)
            val seen = mutableListOf<Unit>()
            val watching = launch(UnconfinedTestDispatcher(testScheduler)) { sessions.session.ends().toList(seen) }
            sessions.clear()
            assertEquals(1, seen.size)
            watching.cancel()
        }
}
