package app.nuddle.alarm

import app.nuddle.data.db.NuddleDatabase
import app.nuddle.domain.AlarmKey
import app.nuddle.domain.PokeLevel
import app.nuddle.domain.QuietHours
import app.nuddle.testing.Cast
import app.nuddle.testing.FakeClock
import app.nuddle.testing.InMemoryMissedRingStore
import app.nuddle.testing.InMemorySessionStore
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
import kotlin.test.assertNull
import kotlin.time.Instant

/** On launch after a force stop (docs/ux/platform.md, Missed-ring detection). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MissedRingCheckTest {
    private lateinit var db: NuddleDatabase
    private val clock = FakeClock(Instant.parse("2026-10-12T08:50:00+03:00"))
    private val scheduler = RecordingScheduler()
    private val store = InMemoryMissedRingStore()
    private val sessions = InMemorySessionStore(aSession())
    private var stoppedAt: Instant? = Instant.parse("2026-10-12T07:55:00+03:00")
    private lateinit var check: MissedRingCheck

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        val istanbul = TimeZone.of("Europe/Istanbul")
        sessions.quiet = QuietHours.default(istanbul)
        val alarms = PokeAlarms(db.tasks(), db.pokeSteps(), scheduler, clock)
        check = MissedRingCheck({ stoppedAt }, db.tasks(), db.pokeSteps(), alarms, store, sessions, clock, istanbul)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun givenPlan() {
        db.tasks().upsert(listOf(aTask()))
        db.pokeSteps().replacePlan(
            Cast.TASK_ID,
            1,
            listOf(
                aStep(1, "2026-10-12T08:00:00+03:00", PokeLevel.SILENT),
                aStep(2, "2026-10-12T08:30:00+03:00"),
                aStep(3, "2026-10-12T08:55:00+03:00", PokeLevel.FULL_SCREEN),
            ),
        )
    }

    @Test
    fun `pokes due while the app was force stopped did not ring, and the alarms come back`() =
        runTest {
            givenPlan()
            check.onLaunch()
            assertEquals(Instant.parse("2026-10-12T08:30:00+03:00"), store.value.lostAt)
            assertEquals(setOf(AlarmKey(Cast.TASK_ID, 1, 3)), scheduler.scheduled.keys)
        }

    @Test
    fun `the same force stop counts once`() =
        runTest {
            givenPlan()
            check.onLaunch()
            // The next launch, with the store as the first one left it apart from the loss.
            val again = InMemoryMissedRingStore().also { it.handled = store.handled }
            val second =
                MissedRingCheck(
                    { stoppedAt },
                    db.tasks(),
                    db.pokeSteps(),
                    PokeAlarms(db.tasks(), db.pokeSteps(), scheduler, clock),
                    again,
                    sessions,
                    clock,
                    TimeZone.UTC,
                )
            second.onLaunch()
            assertNull(again.value.lostAt)
        }

    @Test
    fun `another exit, or a force stop with nothing due since, loses nothing`() =
        runTest {
            givenPlan()
            stoppedAt = null
            check.onLaunch()
            assertNull(store.value.lostAt)

            stoppedAt = Instant.parse("2026-10-12T08:40:00+03:00")
            check.onLaunch()
            assertNull(store.value.lostAt)
        }
}
