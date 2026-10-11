package app.nuddle.data

import app.nuddle.api.SyncApi
import app.nuddle.api.model.DoneCommand
import app.nuddle.data.api.RawCommandApi
import app.nuddle.data.db.NuddleDatabase
import app.nuddle.data.db.PokeStepEntity
import app.nuddle.data.outbox.Outbox
import app.nuddle.data.outbox.OutboxSender
import app.nuddle.data.sync.AlarmRearmer
import app.nuddle.data.sync.SyncRepository
import app.nuddle.testing.Cast
import app.nuddle.testing.FakeClock
import app.nuddle.testing.InMemorySessionStore
import app.nuddle.testing.RecordingDrainer
import app.nuddle.testing.aSession
import app.nuddle.testing.drtError
import app.nuddle.testing.inMemoryDatabase
import app.nuddle.testing.json
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SyncAndOutboxTest {
    private val server = MockWebServer()
    private val sessions = InMemorySessionStore(aSession())
    private lateinit var db: NuddleDatabase
    private var rearms = 0
    private val alarms =
        object : AlarmRearmer {
            override suspend fun applyPlan(
                taskId: String,
                version: Int,
                steps: List<PokeStepEntity>,
            ) = db.pokeSteps().replacePlan(taskId, version, steps)

            override suspend fun rearm() {
                rearms++
            }
        }

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

    private val syncBody =
        """
        {"cursor":"42",
         "me":{"id":"${Cast.EREN_ID}","phone":"${Cast.EREN_PHONE}","displayName":"Eren",
               "quietHours":{"start":"23:00:00","end":"07:00:00","timeZone":"Europe/Istanbul"},
               "useConnectionsForMyTasks":true,"notifyMeForOthersTasks":true},
         "connections":[],"sentInvites":[],"receivedInvites":[],"events":[],
         "tasks":[{"id":"${Cast.TASK_ID}","senderId":"${Cast.SELIN_ID}","recipientId":"${Cast.EREN_ID}",
                   "title":"Temizlikçi için para çek","detail":"1.000 TL","deadline":"2026-10-12T09:00:00+03:00",
                   "finishMode":"photo","rawText":null,"status":"SCHEDULED","late":false,"planVersion":2,
                   "statusChangedAt":"2026-10-11T14:32:00+03:00","inRecipientList":true,"futureField":1}],
         "plans":[{"taskId":"${Cast.TASK_ID}","version":2,
                   "steps":[{"seq":0,"plannedAt":"2026-10-12T08:00:00+03:00","level":"SOUND"},
                            {"seq":1,"plannedAt":"2026-10-12T08:30:00+03:00","level":"FULL_SCREEN"}]}]}
        """.trimIndent()

    @Test
    fun `sync stores me, tasks and plans, moves the cursor and re-arms alarms`() =
        runTest {
            sessions.cursor = "40"
            sessions.device = "d-1"
            server.enqueue(json(200, syncBody))
            val repo =
                SyncRepository(
                    api = server.retrofit().create(SyncApi::class.java),
                    sessions = sessions,
                    tasks = db.tasks(),
                    alarms = alarms,
                    json = testJson,
                )

            assertTrue(repo.sync())

            val request = server.takeRequest()
            assertEquals("40", request.url.queryParameter("cursor"))
            assertEquals("d-1", request.url.queryParameter("deviceId"))
            assertEquals("42", sessions.cursor)
            assertEquals("SCHEDULED", db.tasks().get(Cast.TASK_ID)!!.status)
            assertEquals(
                listOf(0, 1),
                db
                    .pokeSteps()
                    .forTask(Cast.TASK_ID)
                    .map { it.seq }
                    .sorted(),
            )
            assertEquals(1, rearms)
        }

    @Test
    fun `queued commands are replayed in order until the server answers`() =
        runTest {
            val drainer = RecordingDrainer()
            val clock = FakeClock()
            val outbox = Outbox(db.outbox(), testJson, clock, drainer)
            outbox.enqueue("done", "key-1", DoneCommand("key-1", Cast.TASK_ID), DoneCommand.serializer())
            outbox.enqueue("done", "key-1", DoneCommand("key-1", Cast.TASK_ID), DoneCommand.serializer())
            assertEquals(1, db.outbox().pending().size)
            assertEquals(2, drainer.kicks)

            val sender = OutboxSender(db.outbox(), server.retrofit().create(RawCommandApi::class.java))
            server.enqueue(drtError(503, "DRT-5001", "internal"))
            assertFalse(sender.drain())
            assertEquals(
                1,
                db
                    .outbox()
                    .pending()
                    .single()
                    .attempts,
            )

            server.enqueue(json(200, """{"ok":true}"""))
            assertTrue(sender.drain())
            assertTrue(db.outbox().pending().isEmpty())
            server.takeRequest()
            val sent = server.takeRequest()
            assertEquals("/api/v1/commands/done", sent.url.encodedPath)
            assertTrue(sent.body!!.utf8().contains(""""idempotencyKey":"key-1""""))
        }

    @Test
    fun `a command the server refuses on purpose is dropped`() =
        runTest {
            val outbox = Outbox(db.outbox(), testJson, FakeClock(), RecordingDrainer())
            outbox.enqueue("done", "key-2", DoneCommand("key-2", Cast.TASK_ID), DoneCommand.serializer())
            server.enqueue(drtError(409, "DRT-4001", "wrong_status"))
            assertTrue(OutboxSender(db.outbox(), server.retrofit().create(RawCommandApi::class.java)).drain())
            assertTrue(db.outbox().pending().isEmpty())
        }
}
