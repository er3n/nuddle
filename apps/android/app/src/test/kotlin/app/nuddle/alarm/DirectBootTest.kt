package app.nuddle.alarm

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import app.nuddle.data.db.NuddleDatabase
import app.nuddle.data.outbox.Outbox
import app.nuddle.domain.AlarmKey
import app.nuddle.domain.FireDecision
import app.nuddle.domain.IdGenerator
import app.nuddle.domain.LockedFire
import app.nuddle.domain.LockedPlan
import app.nuddle.domain.LockedStep
import app.nuddle.domain.PokeLevel
import app.nuddle.domain.PokeStep
import app.nuddle.domain.QuietHours
import app.nuddle.domain.SkipReason
import app.nuddle.testing.Cast
import app.nuddle.testing.FakeClock
import app.nuddle.testing.FakeFullScreen
import app.nuddle.testing.InMemoryDirectBootStore
import app.nuddle.testing.InMemoryMissedRingStore
import app.nuddle.testing.InMemorySessionStore
import app.nuddle.testing.RecordingAlarmChecks
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
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Pokes after a reboot before the first unlock (docs/ux/platform.md, Direct boot; NUD-125). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DirectBootTest {
    @get:Rule val folder = TemporaryFolder()

    private lateinit var db: NuddleDatabase
    private val istanbul = TimeZone.of("Europe/Istanbul")
    private val clock = FakeClock(Instant.parse("2026-10-12T08:00:00+03:00"))
    private val scheduler = RecordingScheduler()
    private val notifications = RecordingNotifications()
    private val sessions = InMemorySessionStore(aSession())
    private val store = InMemoryDirectBootStore()
    private val checks = RecordingAlarmChecks()
    private val missedRings = InMemoryMissedRingStore()
    private val locked = mutableListOf<Pair<AlarmKey, PokeLevel>>()
    private lateinit var alarms: PokeAlarms
    private lateinit var firing: PokeFiring
    private lateinit var lockedFiring: LockedPokeFiring

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        sessions.device = "device-1"
        sessions.quiet = QuietHours.default(istanbul)
        alarms = PokeAlarms(db.tasks(), db.pokeSteps(), scheduler, clock, notifications, ArmedPokesKeeper(store, sessions, checks, clock))
        firing =
            PokeFiring(
                alarms,
                db.tasks(),
                db.pokeSteps(),
                sessions,
                notifications,
                FakeFullScreen(),
                Outbox(db.outbox(), testJson, clock, RecordingDrainer()),
                IdGenerator(clock),
                clock,
                istanbul,
                missedRings,
                directBoot = store,
            )
        lockedFiring = LockedPokeFiring(store, scheduler, { step, level -> locked += step.key to level }, clock, istanbul)
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
    fun `a rearm keeps a minimal copy of the armed steps and the quiet hours, and the hourly check running`() =
        runTest {
            givenPlan()
            alarms.rearm()

            assertEquals(listOf(key(1), key(2)), store.plan.steps.map { it.step.key })
            assertEquals(listOf(false, false), store.plan.steps.map { it.moment })
            assertEquals(QuietHours.default(istanbul), store.plan.quietHours)
            assertEquals(true, checks.active)
        }

    @Test
    fun `an at task is kept as a moment, so it rings in quiet hours before the unlock too`() =
        runTest {
            givenPlan()
            db.tasks().upsert(
                listOf(aTask(planVersion = 2).copy(remindAt = Instant.parse("2026-10-12T08:15:00+03:00").toEpochMilliseconds())),
            )
            alarms.rearm()
            assertEquals(listOf(true, true), store.plan.steps.map { it.moment })
        }

    @Test
    fun `a closed task leaves the copy and the check stops with no poke left`() =
        runTest {
            givenPlan()
            alarms.rearm()
            db.tasks().setStatus(Cast.TASK_ID, "DONE")
            alarms.stopTask(Cast.TASK_ID)

            assertEquals(emptyList(), store.plan.steps)
            assertEquals(false, checks.active)
        }

    @Test
    fun `signing out empties the copy`() =
        runTest {
            givenPlan()
            alarms.rearm()
            alarms.stopAll()
            assertTrue(store.cleared)
            assertEquals(false, checks.active)
        }

    @Test
    fun `after a reboot before the unlock the stored steps get their alarms again`() =
        runTest {
            givenPlan()
            alarms.rearm()
            scheduler.scheduled.clear()

            assertEquals(2, lockedFiring.rearm())
            assertEquals(setOf(key(1), key(2)), scheduler.scheduled.keys)
        }

    @Test
    fun `a poke due before the unlock rings from the copy and is kept for the handover`() =
        runTest {
            givenPlan()
            alarms.rearm()
            clock.advance(40.minutes)

            val decision = lockedFiring.fire(key(2))

            assertEquals(FireDecision.Ring(PokeLevel.SOUND, permissionMissing = false), decision)
            assertEquals(listOf(key(2) to PokeLevel.SOUND), locked)
            assertEquals(listOf(LockedFire(key(2), clock.now(), PokeLevel.SOUND)), store.fires)
            // The same alarm again (a second LOCKED_BOOT_COMPLETED re-arm) does not ring twice.
            lockedFiring.fire(key(2))
            assertEquals(1, locked.size)
            // Nor is it armed again: the earlier step is too late by now, and this one rang.
            scheduler.scheduled.clear()
            assertEquals(0, lockedFiring.rearm())
        }

    @Test
    fun `quiet hours hold before the unlock`() =
        runTest {
            store.plan =
                LockedPlan(
                    listOf(
                        LockedStep(
                            PokeStep(Cast.TASK_ID, 2, 1, Instant.parse("2026-10-12T02:00:00+03:00"), PokeLevel.SOUND),
                            moment = false,
                        ),
                    ),
                    QuietHours.default(istanbul),
                )
            clock.now = Instant.parse("2026-10-12T02:00:00+03:00")
            assertEquals(FireDecision.Skip(SkipReason.QUIET_HOURS), lockedFiring.fire(key(1)))
            assertEquals(emptyList(), locked)
        }

    @Test
    fun `after the unlock a poke that rang is marked fired, reported with its real time and shown in full`() =
        runTest {
            givenPlan()
            alarms.rearm()
            clock.advance(15.minutes)
            val rangAt = clock.now()
            lockedFiring.fire(key(1))
            // The locked notification is still up; the real one has the same group.
            notifications.show(aTask(planVersion = 2), alarms.step(key(1))!!, PokeLevel.SOUND)
            notifications.shown.clear()
            clock.advance(3.minutes)

            firing.takeOverLockedFires()
            alarms.rearm()

            assertEquals(rangAt.toEpochMilliseconds(), db.pokeSteps().get(Cast.TASK_ID, 2, 1)!!.firedAt)
            val report = db.outbox().pending().single()
            assertEquals("report_fired", report.command)
            assertTrue(report.body.contains(""""firedAt":"$rangAt""""), report.body)
            assertEquals(listOf(Cast.TASK_ID to PokeLevel.SOUND), notifications.shown)
            assertEquals(rangAt, missedRings.value.rangAt)
            assertEquals(setOf(key(2)), scheduler.scheduled.keys)
            assertEquals(emptyList(), store.fires)
        }

    @Test
    fun `a poke swiped away before the unlock is not shown again`() =
        runTest {
            givenPlan()
            alarms.rearm()
            clock.advance(15.minutes)
            lockedFiring.fire(key(1))

            firing.takeOverLockedFires()

            assertNotNull(db.pokeSteps().get(Cast.TASK_ID, 2, 1)!!.firedAt)
            assertEquals(emptyList(), notifications.shown)
        }

    @Test
    fun `the file keeps the copy and the fires, hands the fires over once and is emptied on sign out`() =
        runTest {
            val file = folder.root.resolve("files/direct-boot-pokes.json")
            val fileStore = FileDirectBootStore(file)
            val step = PokeStep(Cast.TASK_ID, 2, 1, Instant.parse("2026-10-12T08:15:00+03:00"), PokeLevel.FULL_SCREEN)
            val plan = LockedPlan(listOf(LockedStep(step, moment = true)), QuietHours.default(istanbul))
            fileStore.savePlan(plan)
            val fire = LockedFire(step.key, Instant.parse("2026-10-12T08:15:01+03:00"), PokeLevel.SOUND)
            fileStore.recordFire(fire)

            val again = FileDirectBootStore(file)
            assertEquals(plan, again.plan())
            assertEquals(listOf(fire), again.takeFires())
            assertEquals(emptyList(), again.fires())
            assertEquals(plan, again.plan())
            // Nothing that names the task or a person is kept.
            assertTrue("Temizlik" !in file.readText())

            again.clear()
            assertEquals(LockedPlan(), again.plan())
        }

    @Test
    fun `a broken file reads as empty`() =
        runTest {
            val file = folder.root.resolve("direct-boot-pokes.json")
            file.writeText("{not json")
            assertEquals(LockedPlan(), FileDirectBootStore(file).plan())
        }

    @Test
    fun `the alarm and boot receivers run before the first unlock and hear LOCKED_BOOT_COMPLETED`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val pm = context.packageManager
        for (receiver in listOf(PokeAlarmReceiver::class.java, RearmReceiver::class.java)) {
            val info = pm.getReceiverInfo(ComponentName(context, receiver), PackageManager.MATCH_DIRECT_BOOT_AWARE)
            assertTrue(info.directBootAware, receiver.simpleName)
        }
        val boot = Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED).setPackage(context.packageName)
        val receivers = pm.queryBroadcastReceivers(boot, 0).map { it.activityInfo.name }
        assertTrue(RearmReceiver::class.java.name in receivers, receivers.toString())
    }
}
