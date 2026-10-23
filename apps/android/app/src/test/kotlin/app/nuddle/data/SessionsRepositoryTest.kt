package app.nuddle.data

import app.nuddle.api.CommandsApi
import app.nuddle.api.DevicesApi
import app.nuddle.testing.drtError
import app.nuddle.testing.json
import app.nuddle.testing.retrofit
import app.nuddle.testing.testJson
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Cihazlarım (5e, ADR 0027): straight to the API, like the profile settings. */
class SessionsRepositoryTest {
    private val server = MockWebServer()
    private lateinit var repo: ApiSessionsRepository

    @Before
    fun setUp() {
        server.start()
        val retrofit = server.retrofit()
        repo = ApiSessionsRepository(retrofit.create(DevicesApi::class.java), retrofit.create(CommandsApi::class.java), testJson)
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun `lists my phones as the server sends them`() =
        runTest {
            server.enqueue(
                json(
                    200,
                    """{"sessions":[
                       {"deviceModel":"Google Pixel 8","appVersion":"0.1.7","lastSeenAt":"2026-10-23T11:32:00.000Z","current":true},
                       {"deviceModel":null,"appVersion":null,"lastSeenAt":"2026-10-22T06:10:00.000Z","current":false}]}""",
                ),
            )
            assertEquals(
                listOf(
                    SignedInPhone("Google Pixel 8", "0.1.7", Instant.parse("2026-10-23T11:32:00Z"), current = true),
                    SignedInPhone(null, null, Instant.parse("2026-10-22T06:10:00Z"), current = false),
                ),
                repo.phones(),
            )
            assertEquals("/api/v1/sessions", server.takeRequest().url.encodedPath)
        }

    @Test
    fun `a list that did not come is null`() =
        runTest {
            server.enqueue(drtError(502, "DRT-5001", "upstream_failed"))
            assertNull(repo.phones())
        }

    @Test
    fun `signing out the others sends the key it is given, so a retry is the same command`() =
        runTest {
            server.enqueue(json(200, """{"signedOut":2}"""))
            assertTrue(repo.signOutOthers("key-1"))
            val request = server.takeRequest()
            assertEquals("/api/v1/commands/sign_out_others", request.url.encodedPath)
            assertEquals("""{"idempotencyKey":"key-1"}""", request.body?.utf8())

            server.enqueue(drtError(502, "DRT-5001", "upstream_failed"))
            assertFalse(repo.signOutOthers("key-2"))
        }
}
