package app.nuddle.alarm

import app.nuddle.data.DoneNote
import app.nuddle.data.DoneNotes
import app.nuddle.data.db.NuddleDatabase
import app.nuddle.data.outbox.Outbox
import app.nuddle.domain.AlarmKey
import app.nuddle.domain.IdGenerator
import app.nuddle.domain.PokeLevel
import app.nuddle.domain.QuietHours
import app.nuddle.testing.Cast
import app.nuddle.testing.FakeClock
import app.nuddle.testing.FakeFullScreen
import app.nuddle.testing.InMemorySessionStore
import app.nuddle.testing.RecordingDrainer
import app.nuddle.testing.RecordingNotifications
import app.nuddle.testing.RecordingScheduler
import app.nuddle.testing.aSession
import app.nuddle.testing.aStep
import app.nuddle.testing.aTask
import app.nuddle.testing.inMemoryDatabase
import app.nuddle.testing.testJson
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Device execution of a plan (docs/domain.md): arm, fire, report, stop. Time comes from [FakeClock] only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PokeAlarmTest {
    private lateinit var db: NuddleDatabase
    private val clock = FakeClock(Instant.parse("2026-10-12T08:00:00+03:00"))
    private val scheduler = RecordingScheduler()
    private val notifications = RecordingNotifications()
    private val fullScreen = FakeFullScreen()
    private val sessions = InMemorySessionStore(aSession())
    private val drainer = RecordingDrainer()
    private val doneNotes = DoneNotes()
    private lateinit var alarms: PokeAlarms
    private lateinit var firing: PokeFiring
    private lateinit var actions: PokeActions

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        sessions.device = "device-1"
        sessions.quiet = QuietHours.default(TimeZone.of("Europe/Istanbul"))
        val ids = IdGenerator(clock)
        val outbox = Outbox(db.outbox(), testJson, clock, drainer)
        alarms = PokeAlarms(db.tasks(), db.pokeSteps(), scheduler, clock)
        firing =
            PokeFiring(
                alarms,
                db.tasks(),
                db.pokeSteps(),
                sessions,
                notifications,
                fullScreen,
                outbox,
                ids,
                clock,
                TimeZone.UTC,
            )
        actions = PokeActions(db.tasks(), alarms, scheduler, notifications, outbox, ids, clock, doneNotes)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun givenPlan() {
        db.tasks().upsert(listOf(aTask(planVersion = 2)))
        db.pokeSteps().replacePlan(
            Cast.TASK_ID,
            2,
            listOf(
                aStep(0, "2026-10-12T07:50:00+03:00", planVersion = 2),
                aStep(1, "2026-10-12T08:15:00+03:00", planVersion = 2),
                aStep(2, "2026-10-12T08:40:00+03:00", PokeLevel.FULL_SCREEN, planVersion = 2),
            ),
        )
    }

    private fun key(seq: Int) = AlarmKey(Cast.TASK_ID, 2, seq)

    @Test
    fun `rearm sets alarms for future steps only`() =
        runTest {
            givenPlan()
            alarms.rearm()
            assertEquals(setOf(key(1), key(2)), scheduler.scheduled.keys)
        }

    @Test
    fun `a new plan version replaces the old alarms`() =
        runTest {
            givenPlan()
            alarms.rearm()
            db.tasks().upsert(listOf(aTask(planVersion = 3)))
            alarms.applyPlan(Cast.TASK_ID, 3, listOf(aStep(0, "2026-10-12T08:20:00+03:00", planVersion = 3)))
            alarms.rearm()
            assertEquals(setOf(AlarmKey(Cast.TASK_ID, 3, 0)), scheduler.scheduled.keys)
        }

    @Test
    fun `a fired step rings, is remembered and reported with planned and actual time`() =
        runTest {
            givenPlan()
            clock.advance(15.minutes)
            firing.fire(key(1))

            assertEquals(listOf(Cast.TASK_ID to PokeLevel.SOUND), notifications.shown)
            assertNotNull(db.pokeSteps().get(Cast.TASK_ID, 2, 1)!!.firedAt)
            val report = db.outbox().pending().single()
            assertEquals("report_fired", report.command)
            assertTrue(report.body.contains(""""seq":1"""), report.body)
            assertTrue(report.body.contains(""""deviceId":"device-1""""), report.body)
            assertTrue(report.body.contains(""""permissionMissing":false"""), report.body)

            alarms.rearm()
            assertEquals(setOf(key(2)), scheduler.scheduled.keys)
        }

    @Test
    fun `full screen without permission rings as sound and says so`() =
        runTest {
            givenPlan()
            fullScreen.allowed = false
            clock.advance(40.minutes)
            firing.fire(key(2))
            assertEquals(listOf(Cast.TASK_ID to PokeLevel.SOUND), notifications.shown)
            assertTrue(
                db
                    .outbox()
                    .pending()
                    .single()
                    .body
                    .contains(""""permissionMissing":true"""),
            )
        }

    @Test
    fun `nothing rings inside quiet hours`() =
        runTest {
            givenPlan()
            clock.now = Instant.parse("2026-10-12T23:30:00+03:00")
            firing.fire(key(1))
            assertTrue(notifications.shown.isEmpty())
            assertNull(db.pokeSteps().get(Cast.TASK_ID, 2, 1)!!.firedAt)
        }

    @Test
    fun `done stops every alarm of the task at once and queues the command`() =
        runTest {
            givenPlan()
            alarms.rearm()
            actions.done(key(1))

            assertTrue(scheduler.scheduled.isEmpty())
            assertEquals(listOf(Cast.TASK_ID), notifications.cancelled)
            assertEquals("DONE", db.tasks().get(Cast.TASK_ID)!!.status)
            assertEquals(
                "done",
                db
                    .outbox()
                    .pending()
                    .single()
                    .command,
            )

            clock.advance(40.minutes)
            firing.fire(key(2))
            assertTrue(notifications.shown.isEmpty())
        }

    @Test
    fun `Bitti on the poke leaves the done note for home with that poke's number`() =
        runTest {
            givenPlan()
            actions.done(key(2))
            assertEquals(DoneNote(Cast.TASK_ID, "Temizlikçi için para çek", caughtAt = 2), doneNotes.pending.value)
        }

    @Test
    fun `undo cancels the task at once, stops its alarms and queues the command`() =
        runTest {
            givenPlan()
            alarms.rearm()
            actions.cancel(Cast.TASK_ID, note = null)

            assertTrue(scheduler.scheduled.isEmpty())
            assertEquals("CANCELLED", db.tasks().get(Cast.TASK_ID)!!.status)
            val queued = db.outbox().pending().single()
            assertEquals("cancel", queued.command)
            assertTrue(""""note":null""" in queued.body, queued.body)
        }

    @Test
    fun `a cancel from the task detail carries the reason as the note`() =
        runTest {
            givenPlan()
            actions.cancel(Cast.TASK_ID, note = "Ben hallettim")
            assertTrue(
                """"note":"Ben hallettim"""" in
                    db
                        .outbox()
                        .pending()
                        .single()
                        .body,
            )
        }

    @Test
    fun `a photo taken stops the pokes and waits for approval`() =
        runTest {
            givenPlan()
            alarms.rearm()
            actions.photoTaken(Cast.TASK_ID)
            assertTrue(scheduler.scheduled.isEmpty())
            assertEquals("AWAITING_APPROVAL", db.tasks().get(Cast.TASK_ID)!!.status)
        }

    @Test
    fun `a photo on a task to self closes it (open-questions#7)`() =
        runTest {
            db.tasks().upsert(listOf(aTask().copy(senderId = Cast.EREN_ID)))
            actions.photoTaken(Cast.TASK_ID)
            assertEquals("DONE", db.tasks().get(Cast.TASK_ID)!!.status)
        }

    @Test
    fun `Bitti without a camera says so to the server`() =
        runTest {
            givenPlan()
            actions.done(Cast.TASK_ID, cameraUnavailable = true)
            assertTrue(
                """"cameraUnavailable":true""" in
                    db
                        .outbox()
                        .pending()
                        .single()
                        .body,
            )
        }

    @Test
    fun `cant today closes the task as dropped without a note`() =
        runTest {
            givenPlan()
            actions.cantToday(key(1))
            assertEquals("DROPPED", db.tasks().get(Cast.TASK_ID)!!.status)
            val queued = db.outbox().pending().single()
            assertEquals("cant_today", queued.command)
            assertTrue(queued.body.contains(""""note":null"""), queued.body)
        }

    @Test
    fun `snooze rings the same step again 15 minutes later`() =
        runTest {
            givenPlan()
            clock.advance(15.minutes)
            actions.snooze(key(1))
            assertEquals(clock.now + 15.minutes, scheduler.scheduled.getValue(key(1)).plannedAt)
            assertEquals(listOf(Cast.TASK_ID), notifications.cancelled)
        }

    @Test
    fun `the second snooze is 10 minutes and a third one does nothing`() =
        runTest {
            givenPlan()
            actions.snooze(key(1))
            clock.advance(16.minutes)
            actions.snooze(key(1))
            assertEquals(clock.now + 10.minutes, scheduler.scheduled.getValue(key(1)).plannedAt)
            assertEquals(2, db.tasks().get(Cast.TASK_ID)!!.snoozesUsed)

            clock.advance(11.minutes)
            val before = scheduler.scheduled.getValue(key(1))
            actions.snooze(key(1))
            assertEquals(before, scheduler.scheduled.getValue(key(1)))
            assertEquals(2, db.outbox().pending().count { it.command == "snooze" })
        }

    @Test
    fun `no snooze that would run past the deadline`() =
        runTest {
            givenPlan()
            clock.advance(50.minutes)
            actions.snooze(key(2))
            assertTrue(db.outbox().pending().isEmpty())
            assertEquals(0, db.tasks().get(Cast.TASK_ID)!!.snoozesUsed)
        }

    @Test
    fun `snooze is queued for the server`() =
        runTest {
            givenPlan()
            actions.snooze(key(1))
            val queued = db.outbox().pending().single()
            assertEquals("snooze", queued.command)
            assertTrue(""""taskId":"${Cast.TASK_ID}"""" in queued.body, queued.body)
        }
}
