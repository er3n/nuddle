package app.nuddle.alarm

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.nuddle.data.db.NuddleDatabase
import app.nuddle.domain.AlarmKey
import app.nuddle.domain.PokeLevel
import app.nuddle.domain.PokeStep
import app.nuddle.domain.QuietHours
import app.nuddle.testing.Cast
import app.nuddle.testing.FakeClock
import app.nuddle.testing.InMemoryDirectBootStore
import app.nuddle.testing.InMemoryMissedRingStore
import app.nuddle.testing.InMemorySessionStore
import app.nuddle.testing.RecordingAlarmChecks
import app.nuddle.testing.RecordingDiagnostics
import app.nuddle.testing.RecordingNotifications
import app.nuddle.testing.RecordingScheduler
import app.nuddle.testing.aSession
import app.nuddle.testing.aStep
import app.nuddle.testing.aTask
import app.nuddle.testing.inMemoryDatabase
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** The hourly alarm check (docs/ux/platform.md, Alarms and pokes; NUD-125). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AlarmCheckTest {
    private lateinit var db: NuddleDatabase
    private val istanbul = TimeZone.of("Europe/Istanbul")
    private val clock = FakeClock(Instant.parse("2026-10-12T08:00:00+03:00"))
    private val scheduler = RecordingScheduler()
    private val missedRings = InMemoryMissedRingStore()
    private val sessions = InMemorySessionStore(aSession())
    private val checks = RecordingAlarmChecks()
    private val diagnostics = RecordingDiagnostics()
    private lateinit var alarms: PokeAlarms
    private lateinit var check: AlarmCheck

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        sessions.quiet = QuietHours.default(istanbul)
        val armed = ArmedPokesKeeper(InMemoryDirectBootStore(), sessions, checks, clock)
        alarms = PokeAlarms(db.tasks(), db.pokeSteps(), scheduler, clock, RecordingNotifications(), armed)
        check = AlarmCheck(alarms, db.tasks(), db.pokeSteps(), scheduler, missedRings, sessions, clock, istanbul, diagnostics)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun givenPlan() {
        db.tasks().upsert(listOf(aTask()))
        db.pokeSteps().replacePlan(
            Cast.TASK_ID,
            1,
            listOf(
                aStep(1, "2026-10-12T08:20:00+03:00"),
                aStep(2, "2026-10-12T08:40:00+03:00"),
                aStep(3, "2026-10-12T08:55:00+03:00", PokeLevel.FULL_SCREEN),
            ),
        )
    }

    private fun key(seq: Int) = AlarmKey(Cast.TASK_ID, 1, seq)

    @Test
    fun `every alarm in place, nothing is set again and nothing is reported`() =
        runTest {
            givenPlan()
            alarms.rearm()

            val result = check.run()

            assertEquals(0, result.reinstalled)
            assertNull(result.lostAt)
            assertTrue(result.armed)
            assertEquals(emptyList(), diagnostics.errors)
        }

    @Test
    fun `alarms the phone dropped are set again and reported`() =
        runTest {
            givenPlan()
            alarms.rearm()
            // An OEM battery saver took two of them.
            scheduler.scheduled.remove(key(2))
            scheduler.scheduled.remove(key(3))

            val result = check.run()

            assertEquals(2, result.reinstalled)
            assertEquals(setOf(key(1), key(2), key(3)), scheduler.scheduled.keys)
            assertIs<AlarmsMissing>(diagnostics.errors.single())
            assertNull(missedRings.value.lostAt)
        }

    @Test
    fun `a step that came due without its alarm since the last check counts as lost (2g)`() =
        runTest {
            givenPlan()
            alarms.rearm()
            check.run()
            scheduler.scheduled.clear()
            clock.advance(1.hours)

            val result = check.run()

            assertEquals(Instant.parse("2026-10-12T08:55:00+03:00"), result.lostAt)
            assertEquals(result.lostAt, missedRings.value.lostAt)
            assertEquals(clock.now(), missedRings.alarmCheck)
        }

    @Test
    fun `a step that rang is not lost`() =
        runTest {
            givenPlan()
            alarms.rearm()
            check.run()
            clock.advance(25.minutes)
            db.pokeSteps().markFired(Cast.TASK_ID, 1, 1, clock.now().toEpochMilliseconds())
            alarms.rearm()

            assertNull(check.run().lostAt)
        }

    @Test
    fun `the first check looks for no loss, since it does not know since when`() =
        runTest {
            givenPlan()
            clock.advance(1.hours)
            assertNull(check.run().lostAt)
        }

    @Test
    fun `with no poke left to arm the check stops itself`() =
        runTest {
            givenPlan()
            alarms.rearm()
            assertEquals(true, checks.active)
            db.tasks().setStatus(Cast.TASK_ID, "DONE")

            assertFalse(check.run().armed)
            assertEquals(false, checks.active)
        }

    @Test
    fun `the Android scheduler knows which alarms are set`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scheduler = AlarmManagerScheduler(context, context.getSystemService(AlarmManager::class.java))
        val step = PokeStep(Cast.TASK_ID, 1, 1, clock.now() + 20.minutes, PokeLevel.SOUND)

        assertFalse(scheduler.isScheduled(step.key))
        scheduler.schedule(step)
        assertTrue(scheduler.isScheduled(step.key))
        scheduler.cancel(step.key)
        assertFalse(scheduler.isScheduled(step.key))
    }
}
