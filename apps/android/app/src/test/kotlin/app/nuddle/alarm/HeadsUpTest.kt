package app.nuddle.alarm

import app.nuddle.data.DoneNote
import app.nuddle.data.DoneNotes
import app.nuddle.data.db.NuddleDatabase
import app.nuddle.data.outbox.Outbox
import app.nuddle.domain.AlarmKey
import app.nuddle.domain.HeadsUpKey
import app.nuddle.domain.IdGenerator
import app.nuddle.domain.PokeLevel
import app.nuddle.domain.QuietHours
import app.nuddle.testing.Cast
import app.nuddle.testing.FakeClock
import app.nuddle.testing.FakeFullScreen
import app.nuddle.testing.InMemoryDirectBootStore
import app.nuddle.testing.InMemoryHeadsUpStore
import app.nuddle.testing.InMemoryMissedRingStore
import app.nuddle.testing.InMemorySessionStore
import app.nuddle.testing.RecordingAlarmChecks
import app.nuddle.testing.RecordingDrainer
import app.nuddle.testing.RecordingHeadsUpChecks
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * "Zaten yaptın mı?" on the device (docs/domain.md, Already done?; NUD-129): shown before a loud step when the phone is
 * in use, once per plan version, never instead of the step. Time comes from [FakeClock] only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HeadsUpTest {
    private lateinit var db: NuddleDatabase
    private val clock = FakeClock(Instant.parse("2026-10-12T08:00:00+03:00"))
    private val scheduler = RecordingScheduler()
    private val notifications = RecordingNotifications()
    private val sessions = InMemorySessionStore(aSession())
    private val store = InMemoryHeadsUpStore()
    private val checks = RecordingHeadsUpChecks()
    private val doneNotes = DoneNotes()
    private lateinit var alarms: PokeAlarms
    private lateinit var headsUp: HeadsUp
    private lateinit var firing: PokeFiring
    private lateinit var actions: PokeActions

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        sessions.device = "device-1"
        sessions.quiet = QuietHours.default(TimeZone.of("Europe/Istanbul"))
        val ids = IdGenerator(clock)
        val outbox = Outbox(db.outbox(), testJson, clock, RecordingDrainer())
        val armed = ArmedPokesKeeper(InMemoryDirectBootStore(), sessions, RecordingAlarmChecks(), clock, checks)
        alarms = PokeAlarms(db.tasks(), db.pokeSteps(), scheduler, clock, notifications, armed)
        headsUp = HeadsUp(alarms, db.tasks(), sessions, notifications, store, checks, clock, TimeZone.UTC)
        firing =
            PokeFiring(
                alarms,
                db.tasks(),
                db.pokeSteps(),
                sessions,
                notifications,
                FakeFullScreen(),
                outbox,
                ids,
                clock,
                TimeZone.UTC,
                InMemoryMissedRingStore(),
            )
        actions = PokeActions(db.tasks(), alarms, scheduler, notifications, outbox, ids, clock, doneNotes)
    }

    @After
    fun tearDown() = db.close()

    /** A silent step that already rang (and was swiped away), then sound at 08:15 and full screen at 08:40. */
    private suspend fun givenPlan(version: Int = 2) {
        db.tasks().upsert(listOf(aTask(planVersion = version).copy(finishMode = "done")))
        db.pokeSteps().replacePlan(
            Cast.TASK_ID,
            version,
            listOf(
                aStep(1, "2026-10-12T07:50:00+03:00", PokeLevel.SILENT, planVersion = version, firedAt = 0),
                aStep(2, "2026-10-12T08:15:00+03:00", planVersion = version),
                aStep(3, "2026-10-12T08:40:00+03:00", PokeLevel.FULL_SCREEN, planVersion = version),
            ),
        )
        alarms.rearm()
    }

    private fun key(
        seq: Int,
        version: Int = 2,
    ) = AlarmKey(Cast.TASK_ID, version, seq)

    @Test
    fun `an unlock 9 minutes before the sound step shows it once, and the step stays armed`() =
        runTest {
            givenPlan()
            clock.now = Instant.parse("2026-10-12T08:06:00+03:00")
            headsUp.unlocked()
            assertEquals(listOf(Cast.TASK_ID to 2), notifications.headsUpsShown)
            assertEquals(setOf(HeadsUpKey(Cast.TASK_ID, 2)), store.keys)
            assertTrue(key(2) in scheduler.scheduled)
            assertTrue(key(3) in scheduler.scheduled)

            // Once per plan version, also before the full screen step later.
            clock.now = Instant.parse("2026-10-12T08:08:00+03:00")
            headsUp.unlocked()
            clock.now = Instant.parse("2026-10-12T08:35:00+03:00")
            headsUp.unlocked()
            assertEquals(1, notifications.headsUpsShown.size)
        }

    @Test
    fun `an unlock earlier than 10 minutes before the step shows nothing`() =
        runTest {
            givenPlan()
            clock.now = Instant.parse("2026-10-12T08:04:00+03:00")
            headsUp.unlocked()
            assertEquals(emptyList(), notifications.headsUpsShown)
        }

    @Test
    fun `a new plan version may ask once more`() =
        runTest {
            givenPlan()
            clock.now = Instant.parse("2026-10-12T08:06:00+03:00")
            headsUp.unlocked()
            givenPlan(version = 3)
            headsUp.unlocked()
            assertEquals(listOf(Cast.TASK_ID to 2, Cast.TASK_ID to 2), notifications.headsUpsShown)
        }

    @Test
    fun `the check alarm shows it only while the phone is in use, and sets the next check`() =
        runTest {
            givenPlan()
            clock.now = Instant.parse("2026-10-12T08:07:00+03:00")
            headsUp.check(inUse = false)
            assertEquals(emptyList(), notifications.headsUpsShown)
            assertEquals(Instant.parse("2026-10-12T08:09:00+03:00"), checks.nextCheck)

            clock.now = Instant.parse("2026-10-12T08:09:00+03:00")
            headsUp.check(inUse = true)
            assertEquals(listOf(Cast.TASK_ID to 2), notifications.headsUpsShown)
            assertEquals(Instant.parse("2026-10-12T08:11:00+03:00"), checks.nextCheck)
        }

    @Test
    fun `ignored, the step rings as planned`() =
        runTest {
            givenPlan()
            clock.now = Instant.parse("2026-10-12T08:06:00+03:00")
            headsUp.unlocked()
            clock.now = Instant.parse("2026-10-12T08:15:00+03:00")
            firing.fire(key(2))
            assertEquals(listOf(Cast.TASK_ID to PokeLevel.SOUND), notifications.shown)
        }

    @Test
    fun `Bitti on it closes the task like Done, alarms stop, the server is told, the note names no poke`() =
        runTest {
            givenPlan()
            clock.now = Instant.parse("2026-10-12T08:06:00+03:00")
            headsUp.unlocked()
            actions.doneFromHeadsUp(key(2))

            assertEquals("DONE", db.tasks().get(Cast.TASK_ID)!!.status)
            assertTrue(scheduler.scheduled.isEmpty())
            assertEquals(emptySet(), notifications.headsUps())
            val done = db.outbox().pending().single()
            assertEquals("done", done.command)
            assertTrue(""""doneAt":"${clock.now()}"""" in done.body, done.body)
            assertEquals(DoneNote(Cast.TASK_ID, aTask().title, caughtAt = null), doneNotes.pending.value)
            // Nothing left to look for.
            assertNull(checks.nextCheck)
            assertFalse(checks.watching)

            clock.now = Instant.parse("2026-10-12T08:15:00+03:00")
            firing.fire(key(2))
            assertEquals(emptyList(), notifications.shown)
        }

    @Test
    fun `the armed set sets the first check 10 minutes before the sound step and listens for the unlock`() =
        runTest {
            givenPlan()
            assertEquals(Instant.parse("2026-10-12T08:05:00+03:00"), checks.nextCheck)
            assertTrue(checks.watching)
        }

    @Test
    fun `the app's start sets the checks again, since the process that set them may be gone`() =
        runTest {
            givenPlan()
            checks.nextCheck = null
            checks.watching = false
            clock.advance(10.minutes)
            headsUp.start()
            assertEquals(Instant.parse("2026-10-12T08:11:00+03:00"), checks.nextCheck)
            assertTrue(checks.watching)
        }
}
