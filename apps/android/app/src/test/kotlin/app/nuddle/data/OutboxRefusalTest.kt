package app.nuddle.data

import app.nuddle.api.model.DoneCommand
import app.nuddle.data.api.RawCommandApi
import app.nuddle.data.db.NuddleDatabase
import app.nuddle.data.outbox.Outbox
import app.nuddle.data.outbox.OutboxSender
import app.nuddle.testing.Cast
import app.nuddle.testing.FakeClock
import app.nuddle.testing.InMemorySessionStore
import app.nuddle.testing.RecordingDrainer
import app.nuddle.testing.aSession
import app.nuddle.testing.drtError
import app.nuddle.testing.inMemoryDatabase
import app.nuddle.testing.retrofit
import app.nuddle.testing.testJson
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A command the server refuses after the phone showed its answer (NUD-168). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OutboxRefusalTest {
    private val server = MockWebServer()
    private val sessions = InMemorySessionStore(aSession())
    private lateinit var db: NuddleDatabase

    @Before
    fun setUp() {
        server.start()
        db = inMemoryDatabase()
    }

    @After
    fun tearDown() {
        db.close()
        server.close()
    }

    @Test
    fun `a refused Bitti makes the next sync a full one, so the server's state comes back, a refused fired report does not (NUD-168)`() =
        runTest {
            sessions.cursor = "40"
            val outbox = Outbox(db.outbox(), testJson, FakeClock(), RecordingDrainer())
            val sender = OutboxSender(db.outbox(), server.retrofit().create(RawCommandApi::class.java), sessions)
            outbox.enqueue("report_fired", "key-3", DoneCommand("key-3", Cast.TASK_ID), DoneCommand.serializer())
            server.enqueue(drtError(409, "DRT-4001", "wrong_status"))
            assertTrue(sender.drain())
            assertEquals("40", sessions.cursor)

            outbox.enqueue("done", "key-4", DoneCommand("key-4", Cast.TASK_ID), DoneCommand.serializer())
            server.enqueue(drtError(409, "DRT-4001", "wrong_status"))
            assertTrue(sender.drain())
            assertNull(sessions.cursor)
        }
}
