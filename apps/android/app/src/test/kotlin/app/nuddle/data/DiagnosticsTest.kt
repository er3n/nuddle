package app.nuddle.data

import androidx.test.core.app.ApplicationProvider
import app.nuddle.api.model.DoneCommand
import app.nuddle.data.api.ApiCallFailed
import app.nuddle.data.api.DiagnosticsInterceptor
import app.nuddle.data.api.RawCommandApi
import app.nuddle.data.db.NuddleDatabase
import app.nuddle.data.diagnostics.CrashlyticsDiagnostics
import app.nuddle.data.outbox.Outbox
import app.nuddle.data.outbox.OutboxSender
import app.nuddle.data.outbox.reportingFailures
import app.nuddle.domain.Diagnostics
import app.nuddle.domain.routeShown
import app.nuddle.testing.Cast
import app.nuddle.testing.FakeClock
import app.nuddle.testing.InMemorySessionStore
import app.nuddle.testing.RecordingDiagnostics
import app.nuddle.testing.RecordingDrainer
import app.nuddle.testing.aSession
import app.nuddle.testing.drtError
import app.nuddle.testing.inMemoryDatabase
import app.nuddle.testing.json
import app.nuddle.testing.retrofit
import app.nuddle.testing.testJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Breadcrumbs and non-fatals (ADR 0023, NUD-95): what is left for a report, and what never is. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DiagnosticsTest {
    /** Stands in for the generated client: the interceptor only reads the method's name. */
    private interface TestApi {
        @POST("api/v1/commands/done")
        suspend fun done(): Response<ResponseBody>

        @GET("api/v1/invites/{code}/preview")
        suspend fun invitePreview(
            @Path("code") code: String,
        ): Response<ResponseBody>

        @POST("api/v1/auth/verify-otp")
        suspend fun verifyOtp(): Response<ResponseBody>
    }

    private val server = MockWebServer()
    private val diagnostics = RecordingDiagnostics()
    private lateinit var db: NuddleDatabase

    private fun client(readTimeoutMs: Long = 10_000): OkHttpClient =
        OkHttpClient
            .Builder()
            .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            .addInterceptor(DiagnosticsInterceptor(diagnostics))
            .build()

    private fun api(client: OkHttpClient = client()): TestApi = server.retrofit(client).create(TestApi::class.java)

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
    fun `a call that works leaves its name and status`() =
        runTest {
            server.enqueue(json(200, """{"ok":true}"""))
            api().done()
            assertEquals(listOf("api done 200"), diagnostics.lines)
            assertTrue(diagnostics.errors.isEmpty())
        }

    @Test
    fun `a refused call is a non-fatal with its status and code`() =
        runTest {
            server.enqueue(drtError(409, "DRT-4001", "wrong_status"))
            api().done()
            assertEquals(listOf("api done 409 DRT-4001"), diagnostics.lines)
            val error = diagnostics.errors.single()
            assertTrue(error is ApiCallFailed)
            assertEquals("done 409 DRT-4001", error.message)
        }

    @Test
    fun `a server error without a body is a non-fatal`() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(502)
                    .body("<html>Bad gateway</html>")
                    .build(),
            )
            api().done()
            assertEquals(listOf("api done 502"), diagnostics.lines)
            assertEquals("done 502", diagnostics.errors.single().message)
        }

    @Test
    fun `a mistyped code is expected and only a breadcrumb`() =
        runTest {
            server.enqueue(drtError(401, "DRT-1006", "invalid_otp"))
            api().verifyOtp()
            assertEquals(listOf("api verifyOtp 401 DRT-1006"), diagnostics.lines)
            assertTrue(diagnostics.errors.isEmpty())
        }

    @Test
    fun `the path is never logged, so an invite code stays out`() =
        runTest {
            server.enqueue(drtError(404, "DRT-1004", "not_found"))
            api().invitePreview("SECRETCODE")
            assertEquals(listOf("api invitePreview 404 DRT-1004"), diagnostics.lines)
            assertFalse(
                diagnostics.errors
                    .single()
                    .message!!
                    .contains("SECRETCODE"),
            )
        }

    @Test
    fun `a Storage upload leaves the method, never the signed URL`() {
        server.enqueue(MockResponse.Builder().code(200).build())
        val request =
            Request
                .Builder()
                .url(server.url("/storage/v1/object/upload/sign/photos/a.jpg?token=SECRETTOKEN"))
                .put("jpeg".toRequestBody())
                .build()
        client().newCall(request).execute().close()
        assertEquals(listOf("api storage PUT 200"), diagnostics.lines)
    }

    @Test
    fun `a lost connection is a breadcrumb, not a non-fatal`() =
        runTest {
            val api = api()
            server.close()
            assertFailsWith<IOException> { api.done() }
            assertTrue(diagnostics.lines.single().startsWith("api done network "))
            assertTrue(diagnostics.errors.isEmpty())
        }

    @Test
    fun `a server that does not answer in time is a non-fatal`() =
        runTest {
            server.enqueue(json(200, """{"ok":true}""").newBuilder().headersDelay(1, TimeUnit.SECONDS).build())
            assertFailsWith<IOException> { api(client(readTimeoutMs = 100)).done() }
            assertEquals(listOf("api done network SocketTimeoutException"), diagnostics.lines)
            assertEquals("done timeout", diagnostics.errors.single().message)
        }

    @Test
    fun `a queued Bitti the server refuses is dropped and recorded`() =
        runTest {
            val outbox = Outbox(db.outbox(), testJson, FakeClock(), RecordingDrainer(), diagnostics)
            outbox.enqueue("done", "key-1", DoneCommand("key-1", Cast.TASK_ID), DoneCommand.serializer())
            server.enqueue(drtError(409, "DRT-4001", "wrong_status"))
            val raw = server.retrofit(client()).create(RawCommandApi::class.java)
            assertTrue(OutboxSender(db.outbox(), raw, InMemorySessionStore(aSession())).drain())
            assertEquals(listOf("queued done key-1", "api outbox done 409 DRT-4001"), diagnostics.lines)
            assertEquals("outbox done 409 DRT-4001", diagnostics.errors.single().message)
        }

    @Test
    fun `a worker that throws is recorded and still fails`() =
        runTest {
            val boom = IllegalStateException("boom")
            val thrown = assertFailsWith<IllegalStateException> { diagnostics.reportingFailures("outbox") { throw boom } }
            assertEquals(boom, thrown)
            assertEquals(listOf("worker outbox failed"), diagnostics.lines)
            assertEquals(listOf<Throwable>(boom), diagnostics.errors)
        }

    @Test
    fun `a worker WorkManager stops is not an error`() =
        runTest {
            assertFailsWith<CancellationException> {
                diagnostics.reportingFailures("sync") { throw CancellationException("stopped") }
            }
            assertTrue(diagnostics.errors.isEmpty())
        }

    @Test
    fun `a route change leaves the pattern and sets the key`() {
        diagnostics.routeShown("task/{taskId}")
        assertEquals(listOf("route task/{taskId}"), diagnostics.lines)
        assertEquals("task/{taskId}", diagnostics.keys[Diagnostics.KEY_ROUTE])
    }

    @Test
    fun `without Firebase everything goes to the log and nothing crashes`() {
        val crashlytics = CrashlyticsDiagnostics(ApplicationProvider.getApplicationContext())
        crashlytics.log("app start")
        crashlytics.record(IllegalStateException("boom"))
        crashlytics.setKey(Diagnostics.KEY_ROUTE, "home")
        crashlytics.setPerson("person-1")
        assertTrue(ShadowLog.getLogsForTag("Nuddle").any { it.msg == "app start" })
    }
}
