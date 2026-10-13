package app.nuddle.data

import app.nuddle.api.CommandsApi
import app.nuddle.api.model.EdgeType
import app.nuddle.domain.IdGenerator
import app.nuddle.testing.Cast
import app.nuddle.testing.FakeClock
import app.nuddle.testing.InMemorySessionStore
import app.nuddle.testing.drtError
import app.nuddle.testing.json
import app.nuddle.testing.retrofit
import app.nuddle.testing.testJson
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountRepositoryTest {
    private val server = MockWebServer()
    private val sessions = InMemorySessionStore()
    private var syncs = 0
    private var wipes = 0
    private lateinit var repo: ApiAccountRepository

    @Before
    fun setUp() {
        server.start()
        repo =
            ApiAccountRepository(
                commandsApi = server.retrofit().create(CommandsApi::class.java),
                sessions = sessions,
                sync = { true.also { syncs++ } },
                wipe = { wipes++ },
                ids = IdGenerator(FakeClock()),
                json = testJson,
            )
    }

    @After
    fun tearDown() = server.close()

    private fun me(
        start: String = "23:00",
        end: String = "07:00",
        useConnections: Boolean = true,
    ) = json(
        200,
        """{"id":"${Cast.EREN_ID}","phone":"${Cast.EREN_PHONE}","displayName":"Eren",
           "quietHours":{"start":"$start","end":"$end","timeZone":"Europe/Istanbul"},
           "useConnectionsForMyTasks":$useConnections,"notifyMeForOthersTasks":true}""",
    )

    private fun ok() = json(200, """{"ok":true}""")

    private fun bodyWithoutKey(): String =
        server
            .takeRequest()
            .body!!
            .utf8()
            .replace(Regex(""""idempotencyKey":"[^"]+",?"""), "")

    @Test
    fun `quiet hours go out alone, then the new me is kept and plans are synced`() =
        runTest {
            server.enqueue(me(start = "22:30", end = "06:30"))
            assertTrue(repo.setQuietHours(LocalTime(22, 30), LocalTime(6, 30)))
            assertEquals("""{"quietHours":{"start":"22:30","end":"06:30"}}""", bodyWithoutKey())
            val profile = sessions.profile.value!!
            assertEquals(LocalTime(22, 30), profile.quietHours.start)
            assertEquals(Cast.EREN_PHONE, profile.phone)
            assertEquals(1, syncs)
        }

    @Test
    fun `a switch goes out alone`() =
        runTest {
            server.enqueue(me(useConnections = false))
            assertTrue(repo.setUseConnectionsForMyTasks(false))
            assertEquals("""{"useConnectionsForMyTasks":false}""", bodyWithoutKey())
            assertFalse(sessions.profile.value!!.useConnectionsForMyTasks)
        }

    @Test
    fun `a setting that did not go keeps everything as it was`() =
        runTest {
            server.enqueue(drtError(500, "DRT-5001", "internal"))
            assertFalse(repo.setNotifyMeForOthersTasks(false))
            assertNull(sessions.profile.value)
            assertEquals(0, syncs)
        }

    @Test
    fun `relationship and the per-person switch change my own edge`() =
        runTest {
            server.enqueue(ok())
            server.enqueue(ok())
            assertTrue(repo.setRelationship(Cast.SIBEL_ID, EdgeType.ROOMMATE))
            assertEquals("""{"personId":"${Cast.SIBEL_ID}","relationship":"roommate"}""", bodyWithoutKey())
            assertTrue(repo.setNotifyMe(Cast.SIBEL_ID, false))
            assertEquals("""{"personId":"${Cast.SIBEL_ID}","notifyMe":false}""", bodyWithoutKey())
            assertEquals(2, syncs)
        }

    @Test
    fun `removing a connection that is already gone counts as done`() =
        runTest {
            server.enqueue(drtError(409, "DRT-4006", "not_connected"))
            assertTrue(repo.removeConnection(Cast.SIBEL_ID, "key-1"))
            server.enqueue(drtError(500, "DRT-5001", "internal"))
            assertFalse(repo.removeConnection(Cast.SIBEL_ID, "key-1"))
            assertEquals(1, syncs)
        }

    @Test
    fun `log out tells the server which phone, then wipes this one`() =
        runTest {
            sessions.saveDeviceId("device-1")
            server.enqueue(ok())
            assertTrue(repo.logOut("key-1"))
            assertEquals("""{"idempotencyKey":"key-1","deviceId":"device-1"}""", server.takeRequest().body!!.utf8())
            assertEquals(1, wipes)
        }

    @Test
    fun `log out that did not reach the server wipes nothing`() =
        runTest {
            sessions.saveDeviceId("device-1")
            server.enqueue(drtError(500, "DRT-5001", "internal"))
            assertFalse(repo.logOut("key-1"))
            assertEquals(0, wipes)
        }

    @Test
    fun `a phone that never registered logs out without a call`() =
        runTest {
            assertTrue(repo.logOut("key-1"))
            assertEquals(0, server.requestCount)
            assertEquals(1, wipes)
        }

    @Test
    fun `deleting the account wipes this phone only after the server confirmed`() =
        runTest {
            server.enqueue(drtError(500, "DRT-5001", "internal"))
            assertFalse(repo.deleteAccount("key-1"))
            assertEquals(0, wipes)
            server.enqueue(ok())
            assertTrue(repo.deleteAccount("key-1"))
            assertEquals(1, wipes)
        }
}
