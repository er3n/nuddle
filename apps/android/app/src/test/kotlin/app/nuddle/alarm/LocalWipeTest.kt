package app.nuddle.alarm

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import app.nuddle.data.db.NuddleDatabase
import app.nuddle.data.outbox.WorkManagerScheduling
import app.nuddle.data.photo.PendingPhoto
import app.nuddle.data.photo.PhotoFiles
import app.nuddle.testing.Cast
import app.nuddle.testing.FakeClock
import app.nuddle.testing.InMemorySessionStore
import app.nuddle.testing.RecordingNotifications
import app.nuddle.testing.RecordingScheduler
import app.nuddle.testing.aSession
import app.nuddle.testing.aStep
import app.nuddle.testing.aTask
import app.nuddle.testing.inMemoryDatabase
import app.nuddle.testing.sibelConnection
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Çıkış yap (7h) and Hesabımı sil (7i) leave nothing of the person on the phone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalWipeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: NuddleDatabase
    private val scheduler = RecordingScheduler()
    private val sessions = InMemorySessionStore(aSession())

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `wiping stops alarms, notifications and queued work, and empties the database, photos and session`() =
        runTest {
            val clock = FakeClock(Instant.parse("2026-10-12T08:00:00+03:00"))
            val alarms = PokeAlarms(db.tasks(), db.pokeSteps(), scheduler, clock, RecordingNotifications())
            db.tasks().upsert(listOf(aTask()))
            db.connections().upsert(listOf(sibelConnection()))
            alarms.applyPlan(Cast.TASK_ID, 1, listOf(aStep(1, "2026-10-12T08:30:00+03:00")))
            alarms.rearm()
            val workManager = WorkManager.getInstance(context)
            val work = WorkManagerScheduling(workManager)
            work.syncSoon()
            val photos = PhotoFiles(File(context.filesDir, "photos"))
            photos.save(Cast.TASK_ID, byteArrayOf(1), PendingPhoto("key-1", clock.now()))
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.notify(1, NotificationCompat.Builder(context, "test").setSmallIcon(android.R.drawable.ic_dialog_info).build())

            AndroidLocalWipe(context, alarms, work, db, photos, sessions).wipe()

            assertTrue(scheduler.scheduled.isEmpty())
            assertTrue(shadowOf(manager).allNotifications.isEmpty())
            assertEquals(
                listOf(WorkInfo.State.CANCELLED),
                workManager.getWorkInfosForUniqueWork("sync").get().map { it.state },
            )
            assertTrue(db.tasks().all().isEmpty())
            assertNull(db.connections().get(Cast.SIBEL_ID))
            assertNull(photos.pending(Cast.TASK_ID))
            assertNull(sessions.current())
        }
}
