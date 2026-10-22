package app.nuddle.data

import app.nuddle.api.AuthApi
import app.nuddle.api.SyncApi
import app.nuddle.data.api.AuthInterceptor
import app.nuddle.data.api.TokenAuthenticator
import app.nuddle.testing.Cast
import app.nuddle.testing.InMemorySessionStore
import app.nuddle.testing.aSession
import app.nuddle.testing.drtError
import app.nuddle.testing.json
import app.nuddle.testing.retrofit
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TokenAuthenticatorTest {
    private val server = MockWebServer()
    private val sessions = InMemorySessionStore(aSession())
    private lateinit var syncApi: SyncApi

    /** The wipe of Çıkış yap (AndroidLocalWipe, LocalWipeTest): here it counts and clears the session as that one does. */
    private var wipes = 0
    private val wipe =
        LocalWipe {
            wipes++
            sessions.clear()
        }

    @Before
    fun setUp() {
        server.start()
        val auth = server.retrofit().create(AuthApi::class.java)
        syncApi = syncApiWith { auth }
    }

    private fun syncApiWith(auth: () -> AuthApi): SyncApi {
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor(AuthInterceptor(sessions))
                .authenticator(TokenAuthenticator(sessions, auth) { wipe })
                .build()
        return server.retrofit(client).create(SyncApi::class.java)
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun `an expired token is refreshed once and the call is retried`() =
        runTest {
            server.enqueue(drtError(401, "DRT-1002", "unauthorized"))
            server.enqueue(
                json(
                    200,
                    """{"accessToken":"access-2","refreshToken":"refresh-2","expiresAt":"2026-10-12T10:00:00+03:00",
                       "personId":"${Cast.EREN_ID}","registered":true}""",
                ),
            )
            server.enqueue(drtError(403, "DRT-1003", "not_registered"))

            syncApi.sync()

            assertEquals("Bearer access-1", server.takeRequest().headers["Authorization"])
            val refresh = server.takeRequest()
            assertEquals("/api/v1/auth/refresh", refresh.url.encodedPath)
            assertEquals("""{"refreshToken":"refresh-1"}""", refresh.body?.utf8())
            assertEquals("Bearer access-2", server.takeRequest().headers["Authorization"])
            assertEquals("refresh-2", sessions.current()!!.refreshToken)
        }

    @Test
    fun `a rejected refresh token wipes the phone as Çıkış yap does (NUD-141)`() =
        runTest {
            server.enqueue(drtError(401, "DRT-1002", "unauthorized"))
            server.enqueue(drtError(401, "DRT-1006", "invalid_otp"))

            val response = syncApi.sync()

            assertEquals(401, response.code())
            assertEquals(1, wipes)
            assertTrue(sessions.cleared)
        }

    @Test
    fun `offline the refresh never answers and the session stays`() =
        runTest {
            server.enqueue(drtError(401, "DRT-1002", "unauthorized"))
            val offline = syncApiWith { throw IOException("offline") }

            val response = offline.sync()

            assertEquals(401, response.code())
            assertEquals(0, wipes)
            assertFalse(sessions.cleared)
            assertEquals("refresh-1", sessions.current()!!.refreshToken)
        }

    @Test
    fun `a server error on refresh keeps the session`() =
        runTest {
            server.enqueue(drtError(401, "DRT-1002", "unauthorized"))
            server.enqueue(drtError(502, "DRT-1099", "upstream_failed"))

            val response = syncApi.sync()

            assertEquals(401, response.code())
            assertEquals(0, wipes)
            assertEquals("refresh-1", sessions.current()!!.refreshToken)
        }
}
