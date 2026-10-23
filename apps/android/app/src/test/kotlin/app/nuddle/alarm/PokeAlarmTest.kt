package app.nuddle.alarm

import app.nuddle.api.model.TaskStatus
import app.nuddle.data.DoneNote
import app.nuddle.data.DoneNotes
import app.nuddle.data.db.NuddleDatabase
import app.nuddle.data.db.OutboxDao
import app.nuddle.data.db.OutboxEntity
import app.nuddle.data.outbox.Outbox
import app.nuddle.domain.AlarmKey
import app.nuddle.domain.IdGenerator
import app.nuddle.domain.PokeLevel
import app.nuddle.domain.QuietHours
import app.nuddle.testing.Cast
import app.nuddle.testing.FakeClock
import app.nuddle.testing.FakeFullScreen
import app.nuddle.testing.InMemoryMissedRingStore
import app.nuddle.testing.InMemorySessionStore
import app.nuddle.testing.RecordingDrainer
import app.nuddle.testing.RecordingNotifications
import app.nuddle.testing.RecordingScheduler
import app.nuddle.testing.aSession
import app.nuddle.testing.aStep
import app.nuddle.testing.aTask
import app.nuddle.testing.inMemoryDatabase
import app.nuddle.testing.testJson
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
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

    /** The pokes a re-arm takes away, apart from the ones Bitti, Ertele and Bugün olmaz take. */
    private val rearmNotifications = RecordingNotifications()
    private val fullScreen = FakeFullScreen()
    private val sessions = InMemorySessionStore(aSession())
    private val drainer = RecordingDrainer()
    private val doneNotes = DoneNotes()
    private val missedRings = InMemoryMissedRingStore()
    private lateinit var alarms: PokeAlarms
    private lateinit var firing: PokeFiring
    private lateinit var actions: PokeActions
    private lateinit var quiet: QuietPokes

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        sessions.device = "device-1"
        sessions.quiet = QuietHours.default(TimeZone.of("Europe/Istanbul"))
        val ids = IdGenerator(clock)
        val outbox = Outbox(db.outbox(), testJson, clock, drainer)
        alarms = PokeAlarms(db.tasks(), db.pokeSteps(), scheduler, clock, rearmNotifications)
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
                missedRings,
            )
        actions = PokeActions(db.tasks(), alarms, scheduler, notifications, outbox, ids, clock, doneNotes)
        quiet = QuietPokes(db.tasks(), alarms, notifications)
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
    fun `a poke that rings is remembered, so an older missed ring stops showing (2g)`() =
        runTest {
            givenPlan()
            clock.advance(15.minutes)
            firing.fire(key(1))
            assertEquals(clock.now(), missedRings.value.rangAt)
        }

    @Test
    fun `a step that does not ring is not remembered as a ring`() =
        runTest {
            givenPlan()
            db.tasks().upsert(listOf(aTask(status = "DONE", planVersion = 2)))
            clock.advance(15.minutes)
            firing.fire(key(1))
            assertNull(missedRings.value.rangAt)
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
    fun `done carries when it was tapped, so a Bitti tapped offline before the deadline still counts (6a)`() =
        runTest {
            givenPlan()
            actions.done(key(1))

            val body =
                db
                    .outbox()
                    .pending()
                    .single()
                    .body
            assertTrue(""""doneAt":"${clock.now()}"""" in body, body)
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
    fun `Ara on the poke stops this ring and nothing else, the task stays open (NUD-128)`() =
        runTest {
            givenPlan()
            alarms.rearm()
            val armed = scheduler.scheduled.toList()
            actions.contact(key(1))

            assertEquals(listOf(Cast.TASK_ID), notifications.cancelled)
            assertEquals("SCHEDULED", db.tasks().get(Cast.TASK_ID)!!.status)
            assertEquals(armed, scheduler.scheduled.toList())
            assertTrue(db.outbox().pending().isEmpty())
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
    fun `İptal et is queued even when the screen that started it closes meanwhile (NUD-143)`() =
        runTest {
            givenPlan()
            // The task detail's scope dies as soon as it navigates home, here while the alarms stop.
            var started: Job? = null
            val closing =
                object : PokeNotifications by notifications {
                    override fun cancel(taskId: String) {
                        notifications.cancel(taskId)
                        started?.cancel()
                    }
                }
            // Room on a phone switches to its own dispatcher, which a cancelled caller never reaches; in memory it does not.
            val roomLike =
                object : OutboxDao by db.outbox() {
                    override suspend fun enqueue(entry: OutboxEntity) {
                        currentCoroutineContext().ensureActive()
                        db.outbox().enqueue(entry)
                    }
                }
            val queue = Outbox(roomLike, testJson, clock, drainer)
            val detail = PokeActions(db.tasks(), alarms, scheduler, closing, queue, IdGenerator(clock), clock, doneNotes)

            started = launch { detail.cancel(Cast.TASK_ID, note = null) }
            started.join()

            assertEquals(TaskStatus.CANCELLED.value, db.tasks().get(Cast.TASK_ID)?.status)
            assertEquals(listOf("cancel"), db.outbox().pending().map { it.command })
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
    fun `cant today from the full screen sends the note from the sheet (7f)`() =
        runTest {
            givenPlan()
            actions.cantToday(key(1), "  Vaktim olmadı ")
            assertEquals("DROPPED", db.tasks().get(Cast.TASK_ID)!!.status)
            assertTrue(
                db
                    .outbox()
                    .pending()
                    .single()
                    .body
                    .contains(""""note":"Vaktim olmadı""""),
            )
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
    fun `no snooze when under 10 minutes are left, 5 for the snooze and 5 for the task (NUD-63)`() =
        runTest {
            givenPlan()
            // Deadline 09:00: at 08:51 a snooze would leave less than 5 minutes for the task.
            clock.advance(51.minutes)
            actions.snooze(key(2))
            assertTrue(db.outbox().pending().isEmpty())
            assertEquals(0, db.tasks().get(Cast.TASK_ID)!!.snoozesUsed)
        }

    @Test
    fun `a snooze near the deadline is shortened and the card knows until when (NUD-63)`() =
        runTest {
            givenPlan()
            clock.advance(50.minutes)
            actions.snooze(key(2))
            val task = db.tasks().get(Cast.TASK_ID)!!
            assertEquals(1, task.snoozesUsed)
            assertEquals((clock.now() + 5.minutes).toEpochMilliseconds(), task.snoozedUntil)
        }

    @Test
    fun `a sync after Ertele keeps the snoozed ring until the server's new plan comes (NUD-64)`() =
        runTest {
            givenPlan()
            alarms.rearm()
            firing.fire(key(0))
            actions.snooze(key(0))

            alarms.rearm()
            assertEquals(clock.now() + 15.minutes, scheduler.scheduled[key(0)]?.plannedAt)

            // The server's plan after the snooze is the next version; the old step does not ring as well.
            db.tasks().upsert(listOf(db.tasks().get(Cast.TASK_ID)!!.copy(planVersion = 3)))
            alarms.applyPlan(Cast.TASK_ID, 3, listOf(aStep(0, "2026-10-12T08:15:00+03:00", planVersion = 3)))
            alarms.rearm()
            assertEquals(setOf(AlarmKey(Cast.TASK_ID, 3, 0)), scheduler.scheduled.keys)
        }

    @Test
    fun `Ertele holds the next step that would ring during the snooze, also after a re-arm (NUD-152)`() =
        runTest {
            db.tasks().upsert(listOf(aTask(planVersion = 2)))
            db.pokeSteps().replacePlan(
                Cast.TASK_ID,
                2,
                listOf(
                    aStep(0, "2026-10-12T08:00:00+03:00", planVersion = 2),
                    aStep(1, "2026-10-12T08:05:00+03:00", planVersion = 2),
                    aStep(2, "2026-10-12T08:40:00+03:00", PokeLevel.FULL_SCREEN, planVersion = 2),
                ),
            )
            alarms.rearm()
            firing.fire(key(0))

            actions.snooze(key(0))
            assertEquals(setOf(key(0), key(2)), scheduler.scheduled.keys)
            assertEquals(clock.now() + 15.minutes, scheduler.scheduled.getValue(key(0)).plannedAt)

            // Offline: an hourly check or a reboot arms again from Room and still leaves the held step out.
            alarms.rearm()
            assertEquals(setOf(key(0), key(2)), scheduler.scheduled.keys)
        }

    @Test
    fun `a task that closes in a sync takes the poke that rang for it off the screen (NUD-64)`() =
        runTest {
            givenPlan()
            alarms.rearm()
            rearmNotifications.show(db.tasks().get(Cast.TASK_ID)!!, alarms.step(key(0))!!, PokeLevel.SOUND)
            alarms.rearm()
            assertTrue(rearmNotifications.cancelled.isEmpty())

            // The sync that brings the cancel replaces the plan with the cancelled version's, which has no steps.
            db.tasks().upsert(listOf(aTask(status = "CANCELLED", planVersion = 3)))
            alarms.applyPlan(Cast.TASK_ID, 3, emptyList())
            alarms.rearm()
            assertEquals(listOf(Cast.TASK_ID), rearmNotifications.cancelled)
            assertTrue(scheduler.scheduled.isEmpty())
        }

    @Test
    fun `a swiped poke goes quiet but is no answer, the plan rings on and nothing is sent (NUD-154)`() =
        runTest {
            givenPlan()
            alarms.rearm()
            firing.fire(key(1))

            quiet.dismissed(key(1))

            assertEquals(listOf(Cast.TASK_ID), notifications.cancelled)
            assertEquals("SCHEDULED", db.tasks().get(Cast.TASK_ID)!!.status)
            assertTrue(key(2) in scheduler.scheduled.keys)
            assertTrue(db.outbox().pending().none { it.command != "report_fired" })
        }

    @Test
    fun `a volume key silences the ringing poke, nothing else changes, and an answered poke is left alone (NUD-155)`() =
        runTest {
            givenPlan()
            alarms.rearm()
            firing.fire(key(1))

            quiet.silence(key(1))

            assertEquals(listOf(Cast.TASK_ID), notifications.silenced)
            assertEquals("SCHEDULED", db.tasks().get(Cast.TASK_ID)!!.status)
            assertTrue(key(2) in scheduler.scheduled.keys)
            assertTrue(db.outbox().pending().none { it.command != "report_fired" })

            actions.done(key(1))
            quiet.silence(key(1))
            assertEquals(listOf(Cast.TASK_ID), notifications.silenced)
        }

    @Test
    fun `Ertele on a poke whose plan a sync replaced still snoozes, rings the current plan and tells the server (NUD-158)`() =
        runTest {
            givenPlan()
            alarms.rearm()
            firing.fire(key(0))
            // The LLM plan replaces the first plan seconds after its first poke rang.
            db.tasks().upsert(listOf(db.tasks().get(Cast.TASK_ID)!!.copy(planVersion = 3)))
            alarms.applyPlan(
                Cast.TASK_ID,
                3,
                listOf(
                    aStep(0, "2026-10-12T08:10:00+03:00", planVersion = 3),
                    aStep(1, "2026-10-12T08:12:00+03:00", planVersion = 3),
                    aStep(2, "2026-10-12T08:30:00+03:00", planVersion = 3),
                ),
            )
            alarms.rearm()

            assertTrue(actions.snooze(key(0)))

            val next = AlarmKey(Cast.TASK_ID, 3, 0)
            val later = AlarmKey(Cast.TASK_ID, 3, 2)
            assertEquals(setOf(next, later), scheduler.scheduled.keys)
            assertEquals(clock.now() + 15.minutes, scheduler.scheduled.getValue(next).plannedAt)
            assertEquals(1, db.tasks().get(Cast.TASK_ID)!!.snoozesUsed)
            assertEquals(1, db.outbox().pending().count { it.command == "snooze" })
            // Offline, a re-arm keeps the snoozed ring and still holds the step inside the snooze.
            alarms.rearm()
            assertEquals(setOf(next, later), scheduler.scheduled.keys)
            assertEquals(clock.now() + 15.minutes, scheduler.scheduled.getValue(next).plannedAt)
        }

    @Test
    fun `a snooze from before an edit does not pull the edited plan's first step forward (NUD-158)`() =
        runTest {
            givenPlan()
            alarms.rearm()
            firing.fire(key(0))
            actions.snooze(key(0))
            // The sender moves the task to tomorrow during the snooze: a new plan, nothing of it rang.
            db.tasks().upsert(listOf(db.tasks().get(Cast.TASK_ID)!!.copy(planVersion = 3)))
            alarms.applyPlan(Cast.TASK_ID, 3, listOf(aStep(0, "2026-10-13T08:00:00+03:00", planVersion = 3)))
            alarms.rearm()

            assertEquals(Instant.parse("2026-10-13T08:00:00+03:00"), scheduler.scheduled.getValue(AlarmKey(Cast.TASK_ID, 3, 0)).plannedAt)
        }

    @Test
    fun `a snooze refused at the tap says so, and nothing changes (NUD-158)`() =
        runTest {
            givenPlan()
            clock.advance(51.minutes)
            assertFalse(actions.snooze(key(2)))
            assertTrue(db.outbox().pending().isEmpty())
        }

    @Test
    fun `a volume key silences a full screen poke whose plan a sync replaced since it rang (NUD-156)`() =
        runTest {
            givenPlan()
            alarms.rearm()
            firing.fire(key(2))
            db.tasks().upsert(listOf(db.tasks().get(Cast.TASK_ID)!!.copy(planVersion = 3)))
            alarms.applyPlan(Cast.TASK_ID, 3, listOf(aStep(0, "2026-10-12T08:50:00+03:00", planVersion = 3)))

            quiet.silence(key(2))

            assertEquals(listOf(Cast.TASK_ID), notifications.silenced)
            assertEquals(key(2), alarms.fullScreenStep(key(2)).key)
        }

    @Test
    fun `Ertele ends before quiet hours start, where its ring would be skipped (NUD-163)`() =
        runTest {
            // Quiet hours 23:00-07:00; a task due 23:30, its poke at 22:50.
            db.tasks().upsert(listOf(aTask(planVersion = 2, deadline = Instant.parse("2026-10-11T23:30:00+03:00"))))
            db.pokeSteps().replacePlan(Cast.TASK_ID, 2, listOf(aStep(0, "2026-10-11T22:50:00+03:00", planVersion = 2)))
            clock.now = Instant.parse("2026-10-11T22:50:00+03:00")
            val quietActions =
                PokeActions(
                    db.tasks(),
                    alarms,
                    scheduler,
                    notifications,
                    Outbox(db.outbox(), testJson, clock, drainer),
                    IdGenerator(clock),
                    clock,
                    doneNotes,
                    quietHours =
                        object : QuietHoursSource {
                            override suspend fun quietHours() = QuietHours.default(TimeZone.of("Europe/Istanbul"))
                        },
                )
            firing.fire(key(0))

            assertTrue(quietActions.snooze(key(0)))

            assertEquals(Instant.parse("2026-10-11T22:59:00+03:00"), scheduler.scheduled.getValue(key(0)).plannedAt)
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
