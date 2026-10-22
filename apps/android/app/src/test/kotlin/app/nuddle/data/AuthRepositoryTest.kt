package app.nuddle.data

import app.nuddle.BuildConfig
import app.nuddle.api.AuthApi
import app.nuddle.api.CommandsApi
import app.nuddle.domain.IdGenerator
import app.nuddle.testing.Cast
import app.nuddle.testing.FakeClock
import app.nuddle.testing.InMemorySessionStore
import app.nuddle.testing.drtError
import app.nuddle.testing.json
import app.nuddle.testing.retrofit
import app.nuddle.testing.testJson
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class AuthRepositoryTest {
    private val server = MockWebServer()
    private val sessions = InMemorySessionStore()
    private lateinit var repo: ApiAuthRepository

    @Before
    fun setUp() {
        server.start()
        val retrofit = server.retrofit()
        repo =
            ApiAuthRepository(
                authApi = retrofit.create(AuthApi::class.java),
                commandsApi = retrofit.create(CommandsApi::class.java),
                sessions = sessions,
                ids = IdGenerator(FakeClock()),
                timeZone = TimeZone.of("Europe/Istanbul"),
                json = testJson,
            )
    }

    @After
    fun tearDown() = server.close()

    private fun session(registered: Boolean) =
        json(
            200,
            """{"accessToken":"a1","refreshToken":"r1","expiresAt":"2026-10-12T09:00:00+03:00",
               "personId":"${Cast.EREN_ID}","registered":$registered}""",
        )

    @Test
    fun `sending a code posts the E164 number`() =
        runTest {
            server.enqueue(json(200, """{"ok":true}"""))
            assertEquals(SendOtpOutcome.Sent, repo.sendOtp(Cast.EREN_PHONE))
            val request = server.takeRequest()
            assertEquals("/api/v1/auth/otp", request.url.encodedPath)
            assertEquals("""{"phone":"${Cast.EREN_PHONE}"}""", request.body?.utf8())
        }

    @Test
    fun `a known person is signed in and stays registered`() =
        runTest {
            server.enqueue(session(registered = true))
            assertEquals(VerifyOutcome.SignedIn(registered = true), repo.verify(Cast.EREN_PHONE, "123456"))
            val stored = sessions.current()!!
            assertEquals(Cast.EREN_ID, stored.personId)
            assertEquals("a1", stored.accessToken)
            assertTrue(stored.registered)
        }

    @Test
    fun `verify names the app version and the device this phone registered (ADR 0027)`() =
        runTest {
            server.enqueue(session(registered = true))
            repo.verify(Cast.EREN_PHONE, "123456")
            assertEquals(
                """{"phone":"${Cast.EREN_PHONE}","code":"123456","appVersion":"${BuildConfig.VERSION_NAME}"}""",
                server.takeRequest().body?.utf8(),
            )

            sessions.saveDeviceId("device-1")
            server.enqueue(session(registered = true))
            repo.verify(Cast.EREN_PHONE, "123456")
            val body = server.takeRequest().body!!.utf8()
            assertTrue(body.contains(""""deviceId":"device-1""""), body)
        }

    @Test
    fun `a new person signs in unregistered, then register stores name and quiet hours`() =
        runTest {
            server.enqueue(session(registered = false))
            assertEquals(VerifyOutcome.SignedIn(registered = false), repo.verify(Cast.EREN_PHONE, "123456"))
            assertFalse(sessions.current()!!.registered)

            server.enqueue(
                json(
                    200,
                    """{"id":"${Cast.EREN_ID}","phone":"${Cast.EREN_PHONE}","displayName":"Eren",
                       "quietHours":{"start":"23:00","end":"07:00","timeZone":"Europe/Istanbul"},
                       "useConnectionsForMyTasks":true,"notifyMeForOthersTasks":true}""",
                ),
            )
            server.takeRequest()
            assertTrue(repo.register("Eren"))
            val request = server.takeRequest()
            assertEquals("/api/v1/commands/register", request.url.encodedPath)
            val body = request.body!!.utf8()
            assertTrue(body.contains(""""displayName":"Eren""""), body)
            assertTrue(body.contains(""""timeZone":"Europe/Istanbul""""), body)
            assertTrue(sessions.current()!!.registered)
            assertEquals("Europe/Istanbul", sessions.quiet!!.timeZone.id)
        }

    @Test
    fun `a wrong code is told apart from an unreachable server`() =
        runTest {
            server.enqueue(drtError(401, "DRT-1006", "invalid_otp"))
            assertEquals(VerifyOutcome.WrongCode(), repo.verify(Cast.EREN_PHONE, "000000"))
            server.enqueue(drtError(502, "DRT-5002", "upstream_failed"))
            assertEquals(VerifyOutcome.Unreachable, repo.verify(Cast.EREN_PHONE, "123456"))
            assertEquals(null, sessions.current())
        }

    @Test
    fun `a wrong code says how many are left, the last one says when the wait ends (6c)`() =
        runTest {
            server.enqueue(
                json(
                    401,
                    """{"error":{"code":"DRT-1006","name":"invalid_otp","message":"x","requestId":"r",""" +
                        """"details":{"remainingAttempts":1}}}""",
                ),
            )
            assertEquals(VerifyOutcome.WrongCode(remaining = 1), repo.verify(Cast.EREN_PHONE, "000000"))

            val locked =
                """{"error":{"code":"DRT-1008","name":"otp_locked","message":"x","requestId":"r",""" +
                    """"details":{"retryAt":"2026-10-11T18:15:00.000Z"}}}"""
            server.enqueue(json(429, locked))
            assertEquals(VerifyOutcome.Locked(Instant.parse("2026-10-11T18:15:00Z")), repo.verify(Cast.EREN_PHONE, "000000"))
            server.enqueue(json(429, locked))
            assertEquals(SendOtpOutcome.Locked(Instant.parse("2026-10-11T18:15:00Z")), repo.sendOtp(Cast.EREN_PHONE))
        }

    @Test
    fun `too many codes in a day says until when, not a failed send (ADR 0024)`() =
        runTest {
            server.enqueue(
                json(
                    429,
                    """{"error":{"code":"DRT-1009","name":"otp_limit","message":"x","requestId":"r",""" +
                        """"details":{"retryAt":"2026-10-11T18:15:00.000Z"}}}""",
                ),
            )
            assertEquals(SendOtpOutcome.Limited(Instant.parse("2026-10-11T18:15:00Z")), repo.sendOtp(Cast.EREN_PHONE))
        }
}
