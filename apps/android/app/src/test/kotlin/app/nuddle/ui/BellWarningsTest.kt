package app.nuddle.ui

import app.nuddle.data.MissedRings
import app.nuddle.data.db.TaskEventEntity
import app.nuddle.domain.GrantedPermissions
import app.nuddle.domain.PokeLevel
import app.nuddle.domain.SetupStep
import app.nuddle.testing.Cast
import app.nuddle.testing.InMemoryMissedRingStore
import app.nuddle.testing.InMemorySessionStore
import app.nuddle.testing.InMemoryTaskDao
import app.nuddle.testing.InMemoryTaskEventDao
import app.nuddle.testing.aSession
import app.nuddle.testing.aStep
import app.nuddle.testing.aTask
import app.nuddle.ui.screens.inbox.BellWarning
import app.nuddle.ui.screens.inbox.NextPokeUi
import app.nuddle.ui.screens.inbox.PokeDay
import app.nuddle.ui.screens.inbox.bellWarnings
import app.nuddle.ui.screens.inbox.nextFullScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/** 2g: the warnings at the top of the bell (docs/ux/platform.md, Missed-ring detection; open-questions#38). */
class BellWarningsTest {
    private val istanbul = TimeZone.of("Europe/Istanbul")
    private val now = Instant.parse("2026-10-12T10:00:00+03:00")
    private val all = GrantedPermissions(notifications = true, exactAlarm = true, fullScreen = true)

    private fun lostEvent(
        taskId: String,
        plannedAt: String,
    ) = TaskEventEntity(
        id = "$taskId-lost-$plannedAt",
        taskId = taskId,
        actorId = null,
        type = "poke_lost",
        payload = """{"seq":2,"level":"SOUND","plannedAt":"$plannedAt"}""",
        at = Instant.parse(plannedAt).toEpochMilliseconds(),
    )

    @Test
    fun `a missed ring first, then each permission that is off, in setup order`() {
        val next = NextPokeUi("Para çek", PokeDay.Tomorrow)
        assertEquals(emptyList(), bellWarnings(null, all, next))
        assertEquals(
            listOf(
                BellWarning.MissedRing,
                BellWarning.PermissionOff(SetupStep.EXACT_ALARM),
                BellWarning.PermissionOff(SetupStep.FULL_SCREEN, next),
            ),
            bellWarnings(now, all.copy(exactAlarm = false, fullScreen = false), next),
        )
    }

    @Test
    fun `the full screen warning names my next full screen poke and its day`() {
        val money = aTask(id = "money").copy(title = "Para çek")
        val soon = aTask(id = "soon").copy(title = "Ekmek al")
        val toSelin = aTask(id = "mine").copy(senderId = Cast.EREN_ID, recipientId = Cast.SELIN_ID)
        val steps =
            listOf(
                aStep(3, "2026-10-13T08:45:00+03:00", PokeLevel.FULL_SCREEN, taskId = "money"),
                aStep(2, "2026-10-12T11:00:00+03:00", PokeLevel.SOUND, taskId = "soon"),
                aStep(3, "2026-10-12T09:00:00+03:00", PokeLevel.FULL_SCREEN, taskId = "soon"),
                aStep(3, "2026-10-12T11:30:00+03:00", PokeLevel.FULL_SCREEN, taskId = "mine"),
            )
        assertEquals(
            NextPokeUi("Para çek", PokeDay.Tomorrow),
            nextFullScreen(steps, listOf(money, soon, toSelin), Cast.EREN_ID, now, istanbul),
        )
        val today = aStep(3, "2026-10-12T20:00:00+03:00", PokeLevel.FULL_SCREEN, taskId = "soon")
        assertEquals(NextPokeUi("Ekmek al", PokeDay.Today), nextFullScreen(steps + today, listOf(money, soon), Cast.EREN_ID, now, istanbul))
        val later = aStep(3, "2026-10-15T08:45:00+03:00", PokeLevel.FULL_SCREEN, taskId = "money")
        assertEquals(PokeDay.Later, nextFullScreen(listOf(later), listOf(money), Cast.EREN_ID, now, istanbul)?.day)
        assertNull(nextFullScreen(steps, listOf(money.copy(status = "DONE")), Cast.EREN_ID, now, istanbul))
    }

    @Test
    fun `losses come from the server for my tasks and from this phone, the newest counts`() =
        runTest {
            val tasks = InMemoryTaskDao()
            val events = InMemoryTaskEventDao()
            val store = InMemoryMissedRingStore()
            val rings = MissedRings(InMemorySessionStore(aSession()), tasks, events, store)
            tasks.upsert(listOf(aTask(id = "toMe"), aTask(id = "toSelin").copy(senderId = Cast.EREN_ID, recipientId = Cast.SELIN_ID)))

            // Selin's lost poke reaches me through the sync of my task to her: not mine to fix.
            events.upsert(listOf(lostEvent("toSelin", "2026-10-12T08:45:00+03:00")))
            assertNull(rings.warning.first())

            events.upsert(listOf(lostEvent("toMe", "2026-10-12T08:15:00+03:00")))
            assertEquals(Instant.parse("2026-10-12T08:15:00+03:00"), rings.warning.first())

            store.lost(Instant.parse("2026-10-12T08:30:00+03:00"))
            assertEquals(Instant.parse("2026-10-12T08:30:00+03:00"), rings.warning.first())

            store.rang(Instant.parse("2026-10-12T09:00:00+03:00"))
            assertNull(rings.warning.first())
            assertEquals(Instant.parse("2026-10-12T08:30:00+03:00"), rings.latest.first())
        }
}
