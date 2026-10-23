package app.nuddle.data

import app.nuddle.api.SyncApi
import app.nuddle.data.db.NuddleDatabase
import app.nuddle.data.db.PokeStepEntity
import app.nuddle.data.session.TaskAcks
import app.nuddle.data.sync.AlarmRearmer
import app.nuddle.data.sync.SyncRepository
import app.nuddle.testing.Cast
import app.nuddle.testing.InMemorySessionStore
import app.nuddle.testing.RecordingAnnouncer
import app.nuddle.testing.aSession
import app.nuddle.testing.inMemoryDatabase
import app.nuddle.testing.json
import app.nuddle.testing.retrofit
import app.nuddle.testing.testJson
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A sync interrupted after the server answered: cancelled (NUD-166) or overtaken by a sign-out (NUD-167). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SyncInterruptedTest {
    private val server = MockWebServer()
    private val sessions = InMemorySessionStore(aSession())
    private val announcer = RecordingAnnouncer()
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

    private val noAcks =
        object : TaskAcks {
            override suspend fun reached(taskIds: Collection<String>) = Unit

            override suspend fun seen(taskIds: Collection<String>) = Unit
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

    private fun syncRepository(
        acks: TaskAcks = noAcks,
        wipe: LocalWipe = LocalWipe {},
    ) = SyncRepository(
        api = server.retrofit().create(SyncApi::class.java),
        sessions = sessions,
        tasks = db.tasks(),
        invites = db.invites(),
        connections = db.connections(),
        events = db.taskEvents(),
        alarms = alarms,
        announcer = announcer,
        json = testJson,
        pending = { emptySet() },
        acks = acks,
        avatars = { },
        announcements = { _, _ -> },
        wipe = wipe,
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
                   "statusChangedAt":"2026-10-11T14:32:00+03:00","inRecipientList":true}],
         "plans":[{"taskId":"${Cast.TASK_ID}","version":2,
                   "steps":[{"seq":0,"plannedAt":"2026-10-12T08:00:00+03:00","level":"SOUND"}]}]}
        """.trimIndent()

    @Test
    fun `a sync cancelled once it has the server's answer still applies all of it and tells the new task (NUD-166)`() =
        runTest {
            sessions.cursor = "40"
            server.enqueue(json(200, syncBody.replace("\"connections\":[]", sibelConnection)))
            lateinit var syncing: Job
            // The acknowledge this sync queues replaces the outbox worker that runs it (unique work, REPLACE).
            val replacing =
                object : TaskAcks {
                    override suspend fun reached(taskIds: Collection<String>) = syncing.cancel()

                    override suspend fun seen(taskIds: Collection<String>) = Unit
                }

            syncing = launch { syncRepository(acks = replacing).sync() }
            syncing.join()

            assertEquals(listOf(Cast.TASK_ID to "Sibel"), announcer.announced)
        }

    @Test
    fun `a sign-out while a sync applies its answer wipes again what the sync wrote back (NUD-167)`() =
        runTest {
            server.enqueue(json(200, syncBody))
            var wipes = 0
            // Çıkış yap lands while the answer is applied: the session goes, the sync goes on.
            val signingOut =
                object : TaskAcks {
                    override suspend fun reached(taskIds: Collection<String>) = sessions.clear()

                    override suspend fun seen(taskIds: Collection<String>) = Unit
                }

            assertTrue(syncRepository(acks = signingOut, wipe = { wipes++ }).sync())

            assertEquals(1, wipes)
        }

    @Test
    fun `an answer that arrives after a sign-out is not applied (NUD-167)`() =
        runTest {
            // Çıkış yap lands while the sync waits for the server.
            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        runBlocking { sessions.clear() }
                        return json(200, syncBody)
                    }
                }

            assertTrue(syncRepository(wipe = { error("nothing was written to wipe again") }).sync())

            assertTrue(db.tasks().all().isEmpty())
            assertEquals(0, rearms)
        }
}
