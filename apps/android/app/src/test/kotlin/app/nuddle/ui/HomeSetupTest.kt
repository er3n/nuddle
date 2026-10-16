package app.nuddle.ui

import app.cash.turbine.test
import app.nuddle.data.DoneNotes
import app.nuddle.data.SetupGate
import app.nuddle.domain.GrantedPermissions
import app.nuddle.domain.IdGenerator
import app.nuddle.domain.QuietHours
import app.nuddle.testing.FakeClock
import app.nuddle.testing.FakeConnectivity
import app.nuddle.testing.FakePermissions
import app.nuddle.testing.FakePhotoLoader
import app.nuddle.testing.FakePhotoUploads
import app.nuddle.testing.FakeTaskRepository
import app.nuddle.testing.InMemoryConnectionDao
import app.nuddle.testing.InMemoryInviteDao
import app.nuddle.testing.InMemoryQueuedWriteDao
import app.nuddle.testing.InMemorySessionStore
import app.nuddle.testing.InMemorySetupStore
import app.nuddle.testing.InMemoryTaskDao
import app.nuddle.testing.InMemoryTaskEventDao
import app.nuddle.testing.MainDispatcherRule
import app.nuddle.testing.RecordingErrorReporter
import app.nuddle.testing.RecordingTaskAcks
import app.nuddle.testing.aSession
import app.nuddle.testing.sibelConnection
import app.nuddle.ui.screens.home.HomeViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Rule
import kotlin.test.Test
import kotlin.time.Instant

/**
 * Setup from home once someone is connected (NUD-103): an install that never ran it, after a reinstall or the move
 * from a sideloaded build to Play, is asked even when it only receives tasks. The task-to-self trigger is in
 * [HomeViewModelTest].
 */
class HomeSetupTest {
    @get:Rule val main = MainDispatcherRule()

    private val istanbul = TimeZone.of("Europe/Istanbul")
    private val clock = FakeClock(Instant.parse("2026-10-11T20:00:00+03:00"))
    private val sessions = InMemorySessionStore(aSession())
    private val permissions = FakePermissions()
    private val setup = InMemorySetupStore()
    private val connectionDao = InMemoryConnectionDao()
    private val vm by lazy {
        HomeViewModel(
            sessions,
            InMemoryTaskDao(),
            InMemoryInviteDao(),
            connectionDao,
            FakeTaskRepository(),
            IdGenerator(clock),
            SetupGate(setup, permissions),
            DoneNotes(),
            FakePhotoLoader(),
            FakePhotoUploads(),
            FakeConnectivity(),
            InMemoryQueuedWriteDao(),
            RecordingErrorReporter(),
            clock,
            istanbul,
            InMemoryTaskEventDao(),
            { flowOf(Unit) },
            RecordingTaskAcks(),
        )
    }

    private suspend fun TestScope.ready() {
        sessions.saveMe("Eren", QuietHours.default(istanbul))
        backgroundScope.launch { vm.state.collect {} }
    }

    private fun notificationsOff() {
        permissions.granted = GrantedPermissions(notifications = false, exactAlarm = true, fullScreen = true)
    }

    @Test
    fun `an install that never ran setup opens it once someone is connected`() =
        runTest(main.dispatcher) {
            notificationsOff()
            vm.openSetup.test {
                ready()
                expectNoEvents()
                connectionDao.upsert(listOf(sibelConnection()))
                awaitItem()
            }
        }

    @Test
    fun `no setup from a connection when it ran on this install`() =
        runTest(main.dispatcher) {
            notificationsOff()
            setup.done = true
            connectionDao.upsert(listOf(sibelConnection()))
            vm.openSetup.test {
                ready()
                expectNoEvents()
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class) // Reason: runCurrent lets the connection reach home before setup is marked done.
    @Test
    fun `a request that waited while home was away is dropped once setup ran elsewhere (consent)`() =
        runTest(main.dispatcher) {
            notificationsOff()
            connectionDao.upsert(listOf(sibelConnection()))
            ready()
            runCurrent()
            setup.done = true
            vm.openSetup.test { expectNoEvents() }
        }
}
