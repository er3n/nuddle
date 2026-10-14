package app.nuddle.data

import app.nuddle.api.CommandsApi
import app.nuddle.api.SyncApi
import app.nuddle.api.model.FinishMode
import app.nuddle.api.model.WriteTaskResult
import app.nuddle.data.db.NuddleDatabase
import app.nuddle.data.db.PokeStepEntity
import app.nuddle.data.outbox.Outbox
import app.nuddle.data.sync.AlarmRearmer
import app.nuddle.data.sync.SyncRepository
import app.nuddle.domain.IdGenerator
import app.nuddle.testing.Cast
import app.nuddle.testing.FakeClock
import app.nuddle.testing.InMemorySessionStore
import app.nuddle.testing.RecordingAnnouncer
import app.nuddle.testing.RecordingDrainer
import app.nuddle.testing.aSession
import app.nuddle.testing.aStep
import app.nuddle.testing.aTask
import app.nuddle.testing.drtError
import app.nuddle.testing.inMemoryDatabase
import app.nuddle.testing.json
import app.nuddle.testing.retrofit
import app.nuddle.testing.testJson
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
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
                        db.taskEvents(),
                        alarms,
                        RecordingAnnouncer(),
                        testJson,
                    ),
                closer =
                    object : TaskCloser {
                        override suspend fun done(
                            taskId: String,
                            cameraUnavailable: Boolean,
                        ) {
                            closed += "done $taskId"
                        }

                        override suspend fun photoTaken(taskId: String) {
                            closed += "photo $taskId"
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
                outbox = Outbox(db.outbox(), testJson, FakeClock(), RecordingDrainer()),
                ids = IdGenerator(FakeClock()),
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
                repository.create(
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
    fun `a moment from the preview is created as an at task with its grace (ADR 0017)`() =
        runTest {
            server.enqueue(json(200, """{"taskId":"${Cast.TASK_ID}","status":"SCHEDULED","planVersion":1}"""))
            server.enqueue(json(200, syncBody))

            repository.create(
                title = "Su iç",
                detail = null,
                deadline = Instant.parse("2026-10-11T20:02:00+03:00"),
                finishMode = FinishMode.DONE,
                rawText = "2 dakika sonra su iç",
                idempotencyKey = "key-8",
                moment = true,
                missAfterMinutes = 30,
            )

            val body = server.takeRequest().body?.utf8() ?: ""
            assertTrue(""""timing":"at"""" in body, body)
            assertTrue(""""missAfterMinutes":30""" in body, body)
        }

    @Test
    fun `editing an at task sends its moment, so the server keeps the grace`() =
        runTest {
            val moment = Instant.parse("2026-10-12T09:00:00+03:00")
            db.tasks().upsert(
                listOf(
                    aTask(deadline = Instant.parse("2026-10-12T09:30:00+03:00"))
                        .copy(senderId = Cast.EREN_ID, remindAt = moment.toEpochMilliseconds()),
                ),
            )
            server.enqueue(json(200, """{"taskId":"${Cast.TASK_ID}","status":"SCHEDULED","planVersion":2}"""))
            server.enqueue(json(200, syncBody))

            repository.edit(Cast.TASK_ID, null, FinishMode.DONE, "key-9")

            val body = server.takeRequest().body?.utf8() ?: ""
            assertTrue(""""deadline":"2026-10-12T06:00:00Z"""" in body, body)
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
    fun `changing how it ends keeps the deadline and the detail`() =
        runTest {
            db.tasks().upsert(listOf(aTask().copy(senderId = Cast.EREN_ID, recipientId = Cast.SIBEL_ID)))
            server.enqueue(json(200, """{"taskId":"${Cast.TASK_ID}","status":"SCHEDULED","planVersion":2}"""))
            server.enqueue(json(200, syncBody))

            assertEquals(WriteOutcome.Created(Cast.TASK_ID), repository.edit(Cast.TASK_ID, null, FinishMode.DONE, "key-7"))

            val body = server.takeRequest().body?.utf8() ?: ""
            assertTrue(""""deadline":"2026-10-12T06:00:00Z"""" in body, body)
            assertTrue(""""detail":"1.000 TL"""" in body, body)
            assertTrue(""""finishMode":"done"""" in body, body)
        }

    @Test
    fun `editing a task the device does not have fails without a call`() =
        runTest {
            assertEquals(WriteOutcome.Failed, repository.editDeadline(Cast.TASK_ID, Instant.parse("2026-10-12T19:00:00+03:00"), "key-6"))
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `text that is not a task comes back as the reply, without a sync (NUD-52)`() =
        runTest {
            server.enqueue(
                json(200, """{"kind":"chat","taskId":null,"question":null,"draft":null,"reply":"Merhaba! Kime ne hatırlatayım?"}"""),
            )

            assertEquals(WriteOutcome.Chat("Merhaba! Kime ne hatırlatayım?"), repository.write("merhaba", "key-4"))
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `a write that got no answer is sent again with the same key and finds the task the server made (NUD-53)`() =
        runTest {
            // The server got the text and made the task, but the answer never reached the phone.
            server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.ShutdownConnection).build())
            server.enqueue(json(200, """{"kind":"created","taskId":"${Cast.TASK_ID}","question":null,"draft":null}"""))
            server.enqueue(json(200, syncBody))

            assertEquals(WriteOutcome.Created(Cast.TASK_ID), repository.write("1000 tl çek yarın 9da", "key-5"))
            val first = server.takeRequest().body?.utf8() ?: ""
            val second = server.takeRequest().body?.utf8() ?: ""
            assertTrue(""""idempotencyKey":"key-5"""" in first, first)
            assertTrue(""""idempotencyKey":"key-5"""" in second, second)
        }

    @Test
    fun `a server error with its code is 6f, the LLM failing is 6b`() =
        runTest {
            server.enqueue(drtError(500, "DRT-5001", "internal"))
            assertEquals(WriteOutcome.Broken("DRT-5001"), repository.write("asdf", "key-3"))

            server.enqueue(drtError(502, "DRT-5002", "upstream_failed"))
            assertEquals(WriteOutcome.Failed, repository.write("asdf", "key-3"))
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
    fun `Sonradan yaptım closes a missed task at once and queues did_it_later`() =
        runTest {
            db.tasks().upsert(listOf(aTask().copy(status = "MISSED")))

            repository.didItLater(Cast.TASK_ID)

            assertEquals("DONE", db.tasks().get(Cast.TASK_ID)?.status)
            val queued = db.outbox().pending().single()
            assertEquals("did_it_later", queued.command)
            assertTrue(""""taskId":"${Cast.TASK_ID}"""" in queued.body, queued.body)
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `Kaldır takes a missed card off my list at once and queues remove_missed (NUD-49)`() =
        runTest {
            db.tasks().upsert(listOf(aTask().copy(status = "MISSED")))

            repository.removeMissed(Cast.TASK_ID)

            val task = db.tasks().get(Cast.TASK_ID)
            assertEquals(false, task?.inRecipientList)
            assertEquals("MISSED", task?.status)
            assertEquals(
                "remove_missed",
                db
                    .outbox()
                    .pending()
                    .single()
                    .command,
            )
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `Ben hallederim cancels a dropped task at once and queues sender_handles`() =
        runTest {
            db.tasks().upsert(listOf(aTask().copy(senderId = Cast.EREN_ID, recipientId = Cast.SIBEL_ID, status = "DROPPED")))

            repository.senderHandles(Cast.TASK_ID)

            assertEquals("CANCELLED", db.tasks().get(Cast.TASK_ID)?.status)
            assertEquals(
                "sender_handles",
                db
                    .outbox()
                    .pending()
                    .single()
                    .command,
            )
        }

    @Test
    fun `a new deadline is sent with its key, then the task syncs`() =
        runTest {
            server.enqueue(json(200, """{"taskId":"${Cast.TASK_ID}","status":"SCHEDULED","planVersion":3}"""))
            server.enqueue(json(200, syncBody))

            val outcome = repository.newDeadline(Cast.TASK_ID, Instant.parse("2026-10-13T19:00:00+03:00"), "key-8")

            assertEquals(WriteOutcome.Created(Cast.TASK_ID), outcome)
            val request = server.takeRequest()
            assertEquals("/api/v1/commands/new_deadline", request.url.encodedPath)
            val body = request.body?.utf8() ?: ""
            assertTrue(""""idempotencyKey":"key-8"""" in body, body)
            assertTrue(""""deadline":"2026-10-13T16:00:00Z"""" in body, body)
            assertEquals("/api/v1/sync", server.takeRequest().url.encodedPath)

            server.enqueue(drtError(409, "DRT-2001", "wrong_status"))
            assertEquals(WriteOutcome.Failed, repository.newDeadline(Cast.TASK_ID, Instant.parse("2026-10-13T19:00:00+03:00"), "key-9"))
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
