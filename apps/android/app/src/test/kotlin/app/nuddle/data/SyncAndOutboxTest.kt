package app.nuddle.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.nuddle.api.SyncApi
import app.nuddle.api.model.AcknowledgeCommand
import app.nuddle.api.model.DoneCommand
import app.nuddle.data.api.RawCommandApi
import app.nuddle.data.db.NuddleDatabase
import app.nuddle.data.db.OutboxEntity
import app.nuddle.data.db.PokeStepEntity
import app.nuddle.data.outbox.Outbox
import app.nuddle.data.outbox.OutboxSender
import app.nuddle.data.photo.PendingPhoto
import app.nuddle.data.photo.PhotoFiles
import app.nuddle.data.session.OutboxTaskAcks
import app.nuddle.data.sync.AlarmRearmer
import app.nuddle.data.sync.LocalPendingChanges
import app.nuddle.data.sync.SyncRepository
import app.nuddle.domain.IdGenerator
import app.nuddle.testing.Cast
import app.nuddle.testing.FakeClock
import app.nuddle.testing.InMemorySessionStore
import app.nuddle.testing.RecordingAnnouncer
import app.nuddle.testing.RecordingDrainer
import app.nuddle.testing.RecordingTaskAcks
import app.nuddle.testing.aSession
import app.nuddle.testing.aTask
import app.nuddle.testing.drtError
import app.nuddle.testing.inMemoryDatabase
import app.nuddle.testing.json
import app.nuddle.testing.retrofit
import app.nuddle.testing.testJson
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

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

    private val announcer = RecordingAnnouncer()
    private val acks = RecordingTaskAcks()

    /** Tasks with a change this phone has not sent yet. */
    private var unsent = emptySet<String>()

    private fun syncRepository() =
        SyncRepository(
            api = server.retrofit().create(SyncApi::class.java),
            sessions = sessions,
            tasks = db.tasks(),
            invites = db.invites(),
            connections = db.connections(),
            events = db.taskEvents(),
            alarms = alarms,
            announcer = announcer,
            json = testJson,
            pending = { unsent },
            acks = acks,
        )

    private val sibelConnection =
        """
        "connections":[{"personId":"${Cast.SIBEL_ID}","displayName":"Sibel","relationship":"spouse","status":"active",
                        "reachability":"FULL_SCREEN","phone":"${Cast.SIBEL_PHONE}"}]
        """.trimIndent()

    private val syncBody =
        """
        {"cursor":"42",
         "me":{"id":"${Cast.EREN_ID}","phone":"${Cast.EREN_PHONE}","displayName":"Eren",
               "quietHours":{"start":"23:00:00","end":"07:00:00","timeZone":"Europe/Istanbul"},
               "useConnectionsForMyTasks":true,"notifyMeForOthersTasks":true},
         "connections":[],"sentInvites":[],"receivedInvites":[],"events":[],
         "tasks":[{"id":"${Cast.TASK_ID}","senderId":"${Cast.SIBEL_ID}","recipientId":"${Cast.EREN_ID}",
                   "title":"Temizlikçi için para çek","detail":"1.000 TL","deadline":"2026-10-12T09:00:00+03:00",
                   "finishMode":"photo","rawText":null,"status":"SCHEDULED","late":false,"planVersion":2,
                   "statusChangedAt":"2026-10-11T14:32:00+03:00","inRecipientList":true,"futureField":1}],
         "plans":[{"taskId":"${Cast.TASK_ID}","version":2,
                   "steps":[{"seq":0,"plannedAt":"2026-10-12T08:00:00+03:00","level":"SOUND"},
                            {"seq":1,"plannedAt":"2026-10-12T08:30:00+03:00","level":"FULL_SCREEN"}]}]}
        """.trimIndent()

    @Test
    fun `an at task keeps its moment, a by task has none (ADR 0017)`() =
        runTest {
            val moment =
                syncBody.replace(
                    """"futureField":1""",
                    """"futureField":1,"timing":"at","remindAt":"2026-10-12T08:30:00+03:00"""",
                )
            server.enqueue(json(200, moment))
            assertTrue(syncRepository().sync())
            assertEquals(
                kotlin.time.Instant
                    .parse("2026-10-12T08:30:00+03:00")
                    .toEpochMilliseconds(),
                db.tasks().get(Cast.TASK_ID)!!.remindAt,
            )

            server.enqueue(json(200, syncBody))
            assertTrue(syncRepository().sync())
            assertEquals(null, db.tasks().get(Cast.TASK_ID)!!.remindAt)
        }

    @Test
    fun `an open task to me that sync stored is told as reached, a closed one is not (NUD-68)`() =
        runTest {
            server.enqueue(json(200, syncBody))
            assertTrue(syncRepository().sync())
            assertEquals(listOf(Cast.TASK_ID), acks.reached)

            acks.reached.clear()
            server.enqueue(json(200, syncBody.replace("\"status\":\"SCHEDULED\"", "\"status\":\"DONE\"")))
            assertTrue(syncRepository().sync())
            assertEquals(emptyList(), acks.reached)
        }

    @Test
    fun `reached and seen go out once each, seen first covers reached (NUD-68)`() =
        runTest {
            val store = PreferenceDataStoreFactory.create(produceFile = { createTempDirectory().resolve("s.preferences_pb").toFile() })
            val outbox = Outbox(db.outbox(), testJson, FakeClock(), RecordingDrainer())
            val acks = OutboxTaskAcks(store, outbox, IdGenerator(FakeClock()))

            acks.reached(listOf("a", "a", "b"))
            acks.reached(listOf("a"))
            acks.seen(listOf("a", "c"))
            acks.seen(listOf("a"))
            acks.reached(listOf("c"))

            val sent = db.outbox().pending().map { testJson.decodeFromString(AcknowledgeCommand.serializer(), it.body) }
            // Same fake time for all, so the outbox order is by key: compare without order.
            assertEquals(
                listOf("a" to false, "a" to true, "b" to false, "c" to true),
                sent.map { it.taskId to it.seen }.sortedBy { "${it.first}${it.second}" },
            )
        }

    @Test
    fun `a sync before my Bitti is sent keeps it done here, the sync after takes the server's (NUD-64)`() =
        runTest {
            db.tasks().upsert(listOf(aTask(status = "DONE", planVersion = 2)))
            unsent = setOf(Cast.TASK_ID)
            server.enqueue(json(200, syncBody))
            assertTrue(syncRepository().sync())
            assertEquals("DONE", db.tasks().get(Cast.TASK_ID)!!.status)

            unsent = emptySet()
            server.enqueue(json(200, syncBody.replace("\"status\":\"SCHEDULED\"", "\"status\":\"CANCELLED\"")))
            assertTrue(syncRepository().sync())
            assertEquals("CANCELLED", db.tasks().get(Cast.TASK_ID)!!.status)
        }

    @Test
    fun `a sync before my Ertele is sent keeps the snooze here (NUD-64)`() =
        runTest {
            val until = Instant.parse("2026-10-12T08:45:00+03:00").toEpochMilliseconds()
            db.tasks().upsert(listOf(aTask(planVersion = 2).copy(snoozesUsed = 1, snoozedUntil = until)))
            unsent = setOf(Cast.TASK_ID)
            server.enqueue(json(200, syncBody))
            assertTrue(syncRepository().sync())
            val task = db.tasks().get(Cast.TASK_ID)!!
            assertEquals(1, task.snoozesUsed)
            assertEquals(until, task.snoozedUntil)
        }

    @Test
    fun `an unsent Bitti or Ertele and an uploading photo are local changes, a fired report is not (NUD-64)`() =
        runTest {
            val photos = PhotoFiles(createTempDirectory().toFile())
            photos.save("photo-task", byteArrayOf(1), PendingPhoto("k", Instant.parse("2026-10-12T08:00:00+03:00")))
            listOf(
                OutboxEntity("k1", "done", """{"idempotencyKey":"k1","taskId":"done-task"}""", 1),
                OutboxEntity("k2", "snooze", """{"idempotencyKey":"k2","taskId":"snoozed-task"}""", 2),
                OutboxEntity("k3", "report_fired", """{"idempotencyKey":"k3","taskId":"fired-task"}""", 3),
            ).forEach { db.outbox().enqueue(it) }

            assertEquals(
                setOf("done-task", "snoozed-task", "photo-task"),
                LocalPendingChanges(db.outbox(), photos, testJson).taskIds(),
            )
        }

    @Test
    fun `two syncs asked at once run one after the other, the second from the first one's cursor (NUD-59)`() =
        runTest {
            sessions.cursor = "40"
            server.enqueue(json(200, syncBody))
            server.enqueue(json(200, syncBody.replace("""{"cursor":"42",""", """{"cursor":"43",""")))

            val both = listOf(async { syncRepository().sync() }, async { syncRepository().sync() }).awaitAll()

            assertEquals(listOf(true, true), both)
            assertEquals("40", server.takeRequest().url.queryParameter("cursor"))
            assertEquals("42", server.takeRequest().url.queryParameter("cursor"))
            assertEquals("43", sessions.cursor)
        }

    @Test
    fun `sync stores me, tasks and plans, moves the cursor and re-arms alarms`() =
        runTest {
            sessions.cursor = "40"
            sessions.device = "d-1"
            server.enqueue(json(200, syncBody))
            val repo = syncRepository()

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
    fun `sync stores task events with their payload and when the status changed`() =
        runTest {
            server.enqueue(
                json(
                    200,
                    syncBody.replace(
                        """"events":[]""",
                        """
                        "events":[{"id":"9","taskId":"${Cast.TASK_ID}","actorId":"${Cast.EREN_ID}","type":"dropped",
                                   "payload":{"note":"Vaktim olmadı"},"at":"2026-10-12T08:46:00+03:00"},
                                  {"id":"10","taskId":"${Cast.TASK_ID}","actorId":null,"type":"approved",
                                   "payload":{"auto":true},"at":"2026-10-12T09:00:00+03:00"}]
                        """.trimIndent(),
                    ),
                ),
            )

            assertTrue(syncRepository().sync())

            val events = db.taskEvents().forTask(Cast.TASK_ID)
            assertEquals(listOf("dropped", "approved"), events.map { it.type })
            assertEquals("Vaktim olmadı", events[0].note)
            assertEquals(Cast.EREN_ID, events[0].actorId)
            assertTrue(events[1].auto)
            assertNull(events[1].note)
            assertEquals(Instant.parse("2026-10-12T08:46:00+03:00").toEpochMilliseconds(), events[0].at)
            assertEquals(
                Instant.parse("2026-10-11T14:32:00+03:00").toEpochMilliseconds(),
                db.tasks().get(Cast.TASK_ID)!!.statusChangedAt,
            )
        }

    private fun myTaskToSibel(
        status: String,
        events: String,
    ) = syncBody
        .replace(
            """"senderId":"${Cast.SIBEL_ID}","recipientId":"${Cast.EREN_ID}"""",
            """"senderId":"${Cast.EREN_ID}","recipientId":"${Cast.SIBEL_ID}"""",
        ).replace(""""status":"SCHEDULED"""", """"status":"$status"""")
        .replace(""""connections":[]""", sibelConnection)
        .replace(""""events":[]""", """"events":[$events]""")

    private fun event(
        id: Int,
        type: String,
        payload: String = "{}",
    ) = """{"id":"$id","taskId":"${Cast.TASK_ID}","actorId":null,"type":"$type","payload":$payload,"at":"2026-10-12T08:0$id:00+03:00"}"""

    @Test
    fun `the human channel firing on my open task tells me who does not answer, once (7a)`() =
        runTest {
            sessions.cursor = "40"
            val events =
                listOf(
                    event(1, "created"),
                    event(2, "poke_fired", """{"level":"SOUND"}"""),
                    event(3, "poke_fired", """{"level":"FULL_SCREEN"}"""),
                    event(4, "human_notified"),
                ).joinToString(",")
            server.enqueue(json(200, myTaskToSibel("SCHEDULED", events)))

            assertTrue(syncRepository().sync())

            assertEquals(listOf(Cast.TASK_ID to Triple("Sibel", 2, true)), announcer.unreached)
            assertTrue(announcer.reachWithdrawn.isEmpty())
        }

    @Test
    fun `a full sync tells nothing, and a closed task takes 7a away`() =
        runTest {
            sessions.cursor = null
            server.enqueue(json(200, myTaskToSibel("SCHEDULED", event(4, "human_notified"))))
            assertTrue(syncRepository().sync())
            assertTrue(announcer.unreached.isEmpty())

            server.enqueue(json(200, myTaskToSibel("DONE", event(5, "done"))))
            assertTrue(syncRepository().sync())
            assertEquals(listOf(Cast.TASK_ID), announcer.reachWithdrawn)
        }

    @Test
    fun `sync stores sent and received invites`() =
        runTest {
            server.enqueue(
                json(
                    200,
                    syncBody.replace(
                        "\"sentInvites\":[],\"receivedInvites\":[]",
                        """
                        "sentInvites":[{"id":"00000000-0000-7000-8000-0000000000a1","code":"abc","inviteePhone":"+905320000002",
                                        "prefilledName":"Sibel","relationship":"spouse","status":"open",
                                        "createdAt":"2026-10-12T14:32:00+03:00"}],
                        "receivedInvites":[{"id":"00000000-0000-7000-8000-0000000000a2","code":"xyz",
                                            "inviterId":"${Cast.SIBEL_ID}","inviterName":"Defne","prefilledName":"Eren",
                                            "relationship":"sibling","status":"open","createdAt":"2026-10-12T12:00:00+03:00"}]
                        """.trimIndent(),
                    ),
                ),
            )
            val repo = syncRepository()

            assertTrue(repo.sync())

            val invites = db.invites().observeAll().first()
            assertEquals(listOf("abc" to true, "xyz" to false), invites.map { it.code to it.sent })
            assertEquals("+905320000002", invites.first().phone)
            assertEquals("Defne", invites.last().name)
            assertEquals(Instant.parse("2026-10-12T14:32:00+03:00").toEpochMilliseconds(), invites.first().createdAt)
        }

    @Test
    fun `sync stores connections and when a task was written`() =
        runTest {
            server.enqueue(
                json(
                    200,
                    syncBody
                        .replace("\"connections\":[]", sibelConnection)
                        .replace("\"inRecipientList\":true,", "\"inRecipientList\":true,\"createdAt\":\"2026-10-11T14:32:00+03:00\","),
                ),
            )

            assertTrue(syncRepository().sync())

            val sibel = db.connections().get(Cast.SIBEL_ID)!!
            assertEquals("Sibel" to Cast.SIBEL_PHONE, sibel.displayName to sibel.phone)
            assertEquals(Instant.parse("2026-10-11T14:32:00+03:00").toEpochMilliseconds(), db.tasks().get(Cast.TASK_ID)!!.createdAt)
        }

    @Test
    fun `sync keeps since when we are connected, my switch for them and my whole profile (5a, 5b)`() =
        runTest {
            val connection =
                sibelConnection.replace(
                    "\"phone\":\"${Cast.SIBEL_PHONE}\"",
                    "\"phone\":\"${Cast.SIBEL_PHONE}\",\"connectedAt\":\"2026-07-11T14:32:00+03:00\",\"notifyMe\":false",
                )
            server.enqueue(
                json(
                    200,
                    syncBody
                        .replace(
                            "\"connections\":[]",
                            connection,
                        ).replace("\"useConnectionsForMyTasks\":true", "\"useConnectionsForMyTasks\":false"),
                ),
            )

            assertTrue(syncRepository().sync())

            val sibel = db.connections().get(Cast.SIBEL_ID)!!
            assertEquals(Instant.parse("2026-07-11T14:32:00+03:00").toEpochMilliseconds(), sibel.connectedAt)
            assertFalse(sibel.notifyMe)
            val me = sessions.profile.value!!
            assertEquals(Cast.EREN_PHONE to false, me.phone to me.useConnectionsForMyTasks)
        }

    @Test
    fun `a task from someone else that arrives later is announced once, with the sender's name`() =
        runTest {
            sessions.cursor = "40"
            val body = syncBody.replace("\"connections\":[]", sibelConnection)
            server.enqueue(json(200, body))
            server.enqueue(json(200, body))

            assertTrue(syncRepository().sync())
            assertTrue(syncRepository().sync())

            assertEquals(listOf(Cast.TASK_ID to "Sibel"), announcer.announced)
        }

    @Test
    fun `a full sync announces nothing, the tasks in it are old news`() =
        runTest {
            sessions.cursor = null
            server.enqueue(json(200, syncBody.replace("\"connections\":[]", sibelConnection)))

            assertTrue(syncRepository().sync())

            assertTrue(announcer.announced.isEmpty())
        }

    @Test
    fun `a task taken back before I looked withdraws its announcement`() =
        runTest {
            sessions.cursor = "40"
            server.enqueue(json(200, syncBody.replace("\"status\":\"SCHEDULED\"", "\"status\":\"CANCELLED\"")))

            assertTrue(syncRepository().sync())

            assertTrue(announcer.announced.isEmpty())
            assertEquals(listOf(Cast.TASK_ID), announcer.withdrawn)
        }

    @Test
    fun `my task's photo coming tells me once, and the note goes when it is answered`() =
        runTest {
            sessions.cursor = "40"
            // Sibel's phone: the task in syncBody is hers to Eren.
            val mine = syncBody.replace("\"connections\":[]", sibelConnection.replace(Cast.SIBEL_ID, Cast.EREN_ID).replace("Sibel", "Eren"))
            val sibelSession = InMemorySessionStore(aSession().copy(personId = Cast.SIBEL_ID)).also { it.cursor = "40" }
            val repo =
                SyncRepository(
                    server.retrofit().create(SyncApi::class.java),
                    sibelSession,
                    db.tasks(),
                    db.invites(),
                    db.connections(),
                    db.taskEvents(),
                    alarms,
                    announcer,
                    testJson,
                    { emptySet() },
                    RecordingTaskAcks(),
                )
            val waiting = mine.replace("\"status\":\"SCHEDULED\"", "\"status\":\"AWAITING_APPROVAL\"")
            server.enqueue(json(200, mine))
            server.enqueue(json(200, waiting))
            server.enqueue(json(200, waiting))
            server.enqueue(json(200, waiting.replace("\"status\":\"AWAITING_APPROVAL\"", "\"status\":\"DONE\"")))

            repeat(4) { assertTrue(repo.sync()) }

            assertEquals(listOf(Cast.TASK_ID to "Eren"), announcer.photos)
            assertEquals(Cast.TASK_ID, announcer.withdrawn.last())
            assertTrue(announcer.announced.isEmpty(), "a task I wrote is never announced to me")
        }

    @Test
    fun `Onayla and Tekrar iste apply at once and wait in the outbox`() =
        runTest {
            db.tasks().upsert(listOf(aTask(status = "AWAITING_APPROVAL")))
            val outbox = Outbox(db.outbox(), testJson, FakeClock(), RecordingDrainer())
            val approvals = OutboxApprovals(db.tasks(), outbox, IdGenerator(FakeClock()))

            approvals.reRequest(Cast.TASK_ID, "1.500 lazımdı")
            assertEquals("REJECTED", db.tasks().get(Cast.TASK_ID)!!.status)
            approvals.approve(Cast.TASK_ID)
            assertEquals("DONE", db.tasks().get(Cast.TASK_ID)!!.status)

            // Both are queued at the same fake instant, so their order in the outbox is not what is checked here.
            val queued = db.outbox().pending().associateBy { it.command }
            assertEquals(setOf("re_request", "approve"), queued.keys)
            assertTrue(""""note":"1.500 lazımdı"""" in queued.getValue("re_request").body)
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
