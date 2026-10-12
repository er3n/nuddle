package app.nuddle.data

import app.nuddle.api.CommandsApi
import app.nuddle.api.SyncApi
import app.nuddle.api.model.FinishMode
import app.nuddle.api.model.WriteTaskResult
import app.nuddle.data.db.NuddleDatabase
import app.nuddle.data.db.PokeStepEntity
import app.nuddle.data.sync.AlarmRearmer
import app.nuddle.data.sync.SyncRepository
import app.nuddle.testing.Cast
import app.nuddle.testing.InMemorySessionStore
import app.nuddle.testing.RecordingAnnouncer
import app.nuddle.testing.aSession
import app.nuddle.testing.aStep
import app.nuddle.testing.aTask
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** `write_task` from home (docs/ux/screens.md 2a): created, ask, or failed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TaskRepositoryTest {
    private val server = MockWebServer()
    private val sessions = InMemorySessionStore(aSession())
    private val closed = mutableListOf<String>()
    private lateinit var db: NuddleDatabase
    private lateinit var repository: ApiTaskRepository

    private val alarms =
        object : AlarmRearmer {
            override suspend fun applyPlan(
                taskId: String,
                version: Int,
                steps: List<PokeStepEntity>,
            ) = db.pokeSteps().replacePlan(taskId, version, steps)

            override suspend fun rearm() = Unit
        }

    @Before
    fun setUp() {
        server.start()
        db = inMemoryDatabase()
        val retrofit = server.retrofit()
        repository =
            ApiTaskRepository(
                commandsApi = retrofit.create(CommandsApi::class.java),
                sync =
                    SyncRepository(
                        retrofit.create(SyncApi::class.java),
                        sessions,
                        db.tasks(),
                        db.invites(),
                        db.connections(),
                        alarms,
                        RecordingAnnouncer(),
                        testJson,
                    ),
                closer =
                    object : TaskCloser {
                        override suspend fun done(taskId: String) {
                            closed += "done $taskId"
                        }

                        override suspend fun cancel(
                            taskId: String,
                            note: String?,
                        ) {
                            closed += "cancel $taskId"
                        }
                    },
                sessions = sessions,
                tasks = db.tasks(),
                pokeSteps = db.pokeSteps(),
                json = testJson,
            )
    }

    @After
    fun tearDown() {
        db.close()
        server.close()
    }

    private val syncBody =
        """
        {"cursor":"7",
         "me":{"id":"${Cast.EREN_ID}","phone":"${Cast.EREN_PHONE}","displayName":"Eren",
               "quietHours":{"start":"23:00:00","end":"07:00:00","timeZone":"Europe/Istanbul"},
               "useConnectionsForMyTasks":true,"notifyMeForOthersTasks":true},
         "connections":[],"sentInvites":[],"receivedInvites":[],"events":[],
         "tasks":[{"id":"${Cast.TASK_ID}","senderId":"${Cast.EREN_ID}","recipientId":"${Cast.EREN_ID}",
                   "title":"Para çek","detail":"1.000 TL","deadline":"2026-10-12T09:00:00+03:00",
                   "finishMode":"done","rawText":"1000 tl çek yarın 9da","status":"SCHEDULED","late":false,
                   "planVersion":1,"statusChangedAt":"2026-10-11T20:00:00+03:00","inRecipientList":true}],
         "plans":[]}
        """.trimIndent()

    @Test
    fun `a created task is sent with its key and is on the device after the sync`() =
        runTest {
            server.enqueue(json(200, """{"kind":"created","taskId":"${Cast.TASK_ID}","question":null,"draft":null}"""))
            server.enqueue(json(200, syncBody))

            val outcome = repository.write("1000 tl çek yarın 9da", "key-1")

            assertEquals(WriteOutcome.Created(Cast.TASK_ID), outcome)
            val write = server.takeRequest()
            assertEquals("/api/v1/commands/write_task", write.url.encodedPath)
            val body = write.body?.utf8() ?: ""
            assertTrue(""""idempotencyKey":"key-1"""" in body, body)
            assertTrue(""""text":"1000 tl çek yarın 9da"""" in body, body)
            assertEquals("/api/v1/sync", server.takeRequest().url.encodedPath)
            assertEquals("Para çek", db.tasks().get(Cast.TASK_ID)?.title)
        }

    @Test
    fun `missing information comes back as the one question to ask, without a sync`() =
        runTest {
            server.enqueue(
                json(
                    200,
                    """
                    {"kind":"ask","taskId":null,"question":"deadline",
                     "draft":{"recipient":null,"title":"Para çek","detail":"1.000 TL","deadline":null,"finishMode":"done"}}
                    """.trimIndent(),
                ),
            )

            assertEquals(
                WriteOutcome.Ask(WriteTaskResult.Question.DEADLINE, TaskDraft("Para çek", "1.000 TL", FinishMode.DONE)),
                repository.write("1000 tl çek", "key-2"),
            )
            assertEquals(1, server.requestCount)
            assertNull(db.tasks().get(Cast.TASK_ID))
        }

    @Test
    fun `the preview creates a task to self, then syncs`() =
        runTest {
            server.enqueue(json(200, """{"taskId":"${Cast.TASK_ID}","status":"SCHEDULED","planVersion":1}"""))
            server.enqueue(json(200, syncBody))

            val outcome =
                repository.createForSelf(
                    title = "Para çek",
                    detail = "1.000 TL",
                    deadline = Instant.parse("2026-10-12T09:00:00+03:00"),
                    finishMode = FinishMode.DONE,
                    rawText = "1000 tl çek",
                    idempotencyKey = "key-4",
                )

            assertEquals(WriteOutcome.Created(Cast.TASK_ID), outcome)
            val create = server.takeRequest()
            assertEquals("/api/v1/commands/create_task", create.url.encodedPath)
            val body = create.body?.utf8() ?: ""
            assertTrue(""""recipientId":"${Cast.EREN_ID}"""" in body, body)
            assertTrue(""""deadline":"2026-10-12T06:00:00Z"""" in body, body)
            assertTrue(""""rawText":"1000 tl çek"""" in body, body)
            assertEquals("/api/v1/sync", server.takeRequest().url.encodedPath)
        }

    @Test
    fun `Düzenle moves the deadline and keeps detail and finish mode`() =
        runTest {
            db.tasks().upsert(listOf(aTask().copy(senderId = Cast.EREN_ID)))
            server.enqueue(json(200, """{"taskId":"${Cast.TASK_ID}","status":"SCHEDULED","planVersion":2}"""))
            server.enqueue(json(200, syncBody))

            val outcome = repository.editDeadline(Cast.TASK_ID, Instant.parse("2026-10-12T19:00:00+03:00"), "key-5")

            assertEquals(WriteOutcome.Created(Cast.TASK_ID), outcome)
            val edit = server.takeRequest()
            assertEquals("/api/v1/commands/edit", edit.url.encodedPath)
            val body = edit.body?.utf8() ?: ""
            assertTrue(""""deadline":"2026-10-12T16:00:00Z"""" in body, body)
            assertTrue(""""detail":"1.000 TL"""" in body, body)
            assertTrue(""""finishMode":"photo"""" in body, body)
            assertEquals("/api/v1/sync", server.takeRequest().url.encodedPath)
        }

    @Test
    fun `editing a task the device does not have fails without a call`() =
        runTest {
            assertEquals(WriteOutcome.Failed, repository.editDeadline(Cast.TASK_ID, Instant.parse("2026-10-12T19:00:00+03:00"), "key-6"))
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `an error answer or no answer is a failed write`() =
        runTest {
            server.enqueue(drtError(400, "DRT-1001", "validation_failed"))
            assertEquals(WriteOutcome.Failed, repository.write("asdf", "key-3"))

            server.close()
            assertEquals(WriteOutcome.Failed, repository.write("asdf", "key-3"))
        }

    @Test
    fun `done answers which poke of the current plan caught the person`() =
        runTest {
            db.tasks().upsert(listOf(aTask(planVersion = 2)))
            db.pokeSteps().replacePlan(
                Cast.TASK_ID,
                2,
                listOf(
                    aStep(1, "2026-10-12T08:00:00+03:00", planVersion = 2, firedAt = 1L),
                    aStep(2, "2026-10-12T08:30:00+03:00", planVersion = 2, firedAt = 2L),
                    aStep(3, "2026-10-12T08:45:00+03:00", planVersion = 2),
                ),
            )
            assertEquals(2, repository.done(Cast.TASK_ID))
        }

    @Test
    fun `done before any poke rang has nothing to tell`() =
        runTest {
            db.tasks().upsert(listOf(aTask()))
            assertNull(repository.done(Cast.TASK_ID))
        }

    @Test
    fun `done early and undo go to the task closer`() =
        runTest {
            repository.done(Cast.TASK_ID)
            repository.cancel(Cast.TASK_ID)
            assertEquals(listOf("done ${Cast.TASK_ID}", "cancel ${Cast.TASK_ID}"), closed)
        }
}
