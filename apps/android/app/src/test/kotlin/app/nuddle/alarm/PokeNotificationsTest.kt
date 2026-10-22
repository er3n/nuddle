package app.nuddle.alarm

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.graphics.Bitmap
import androidx.core.content.getSystemService
import androidx.test.core.app.ApplicationProvider
import app.nuddle.R
import app.nuddle.data.avatar.AvatarFiles
import app.nuddle.data.photo.toJpeg
import app.nuddle.domain.PokeLevel
import app.nuddle.domain.PokeStep
import app.nuddle.domain.PokeTitles
import app.nuddle.testing.Cast
import app.nuddle.testing.FakeClock
import app.nuddle.testing.aTask
import kotlinx.datetime.TimeZone
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The poke notification (screen 3a): the step's 1-based `seq` as "N. dürtme", and Ertele from the snooze count. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PokeNotificationsTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val avatars = AvatarFiles(File(app.cacheDir, "avatars-test").apply { deleteRecursively() })
    private val notifications = AndroidPokeNotifications(app, FakeClock(), TimeZone.of("Europe/Istanbul"), AlarmVolume(app), avatars)

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        NotificationChannels.create(app)
    }

    private fun show(
        seq: Int,
        snoozesUsed: Int = 0,
        finishMode: String = "photo",
    ): Notification {
        val step = PokeStep(Cast.TASK_ID, 1, seq, Instant.parse("2026-10-12T08:00:00+03:00"), PokeLevel.SOUND)
        notifications.show(aTask().copy(snoozesUsed = snoozesUsed, finishMode = finishMode), step, PokeLevel.SOUND)
        return shadowOf(app.getSystemService<NotificationManager>()).allNotifications.single()
    }

    private fun Notification.actionTitles() = actions.map { it.title.toString() }

    @Test
    fun `the pokes that are up name their tasks, until they are cancelled (NUD-64)`() {
        show(seq = 1)
        assertEquals(setOf(Cast.TASK_ID), notifications.showing())
        notifications.cancel(Cast.TASK_ID)
        assertEquals(emptySet(), notifications.showing())
    }

    @Test
    fun `the sender's cached picture is the large icon, none without one or on a task to self (NUD-80)`() {
        assertNull(show(seq = 1).getLargeIcon())
        app.getSystemService<NotificationManager>()!!.cancelAll()

        avatars.save(Cast.SIBEL_ID, "avatars/sibel/1.jpg", Bitmap.createBitmap(8, 6, Bitmap.Config.ARGB_8888).toJpeg())
        assertNotNull(show(seq = 1).getLargeIcon())
        app.getSystemService<NotificationManager>()!!.cancelAll()

        val step = PokeStep(Cast.TASK_ID, 1, 1, Instant.parse("2026-10-12T08:00:00+03:00"), PokeLevel.SOUND)
        notifications.show(aTask().copy(recipientId = Cast.SIBEL_ID), step, PokeLevel.SOUND)
        assertNull(shadowOf(app.getSystemService<NotificationManager>()).allNotifications.single().getLargeIcon())
    }

    @Test
    fun `SOUND and FULL_SCREEN ring until silenced, SILENT does not ring`() {
        val manager = app.getSystemService<NotificationManager>()!!

        fun insistent(level: PokeLevel): Boolean {
            manager.cancelAll()
            val step = PokeStep(Cast.TASK_ID, 1, 1, Instant.parse("2026-10-12T08:00:00+03:00"), level)
            notifications.show(aTask(), step, level)
            val flags = shadowOf(manager).allNotifications.single().flags
            return flags and Notification.FLAG_INSISTENT != 0
        }

        assertTrue(insistent(PokeLevel.SOUND))
        assertTrue(insistent(PokeLevel.FULL_SCREEN))
        assertFalse(insistent(PokeLevel.SILENT))
    }

    @Test
    fun `Zaten yaptın mı is quiet, has Bitti only and no count, and the step's poke takes it away (NUD-129)`() {
        val manager = app.getSystemService<NotificationManager>()!!
        val step = PokeStep(Cast.TASK_ID, 1, 2, Instant.parse("2026-10-12T08:09:00+03:00"), PokeLevel.SOUND)
        notifications.showHeadsUp(aTask().copy(finishMode = "done"), step)

        val headsUp = shadowOf(manager).allNotifications.single()
        assertEquals(NotificationChannels.POKE_SILENT, headsUp.channelId)
        assertEquals("Zaten yaptın mı?", headsUp.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(aTask().title, headsUp.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertNull(headsUp.extras.getString(Notification.EXTRA_SUB_TEXT))
        assertEquals(listOf("Bitti"), headsUp.actionTitles())
        assertEquals(0, headsUp.flags and Notification.FLAG_INSISTENT)
        assertEquals(setOf(Cast.TASK_ID), notifications.headsUps())
        // A heads-up is not a poke: nothing counts it as one that rang.
        assertEquals(emptySet(), notifications.showing())

        notifications.show(aTask(), step, PokeLevel.SOUND)
        assertEquals(emptySet(), notifications.headsUps())
        assertEquals(setOf(Cast.TASK_ID), notifications.showing())
    }

    @Test
    fun `cancelling a task's poke takes its heads-up away too (NUD-129)`() {
        val step = PokeStep(Cast.TASK_ID, 1, 2, Instant.parse("2026-10-12T08:09:00+03:00"), PokeLevel.SOUND)
        notifications.showHeadsUp(aTask(), step)
        notifications.cancel(Cast.TASK_ID)
        assertEquals(emptySet(), notifications.headsUps())
    }

    @Test
    fun `the first step says 1 dürtme`() {
        assertEquals("1. dürtme", show(seq = 1).extras.getString(Notification.EXTRA_SUB_TEXT))
    }

    @Test
    fun `the third step says 3 dürtme`() {
        assertEquals("3. dürtme", show(seq = 3).extras.getString(Notification.EXTRA_SUB_TEXT))
    }

    @Test
    fun `Ertele says when it rings again (NUD-63), and son hakkın on the last one`() {
        // 08:00 now, deadline 09:00: 15 minutes, then 10.
        assertEquals(listOf("Bitti", "Ertele · 08:15", "Bugün olmaz"), show(seq = 1).actionTitles())
    }

    @Test
    fun `the second snooze is the last`() {
        assertEquals(listOf("Bitti", "Ertele · 08:10 · son hakkın", "Bugün olmaz"), show(seq = 2, snoozesUsed = 1).actionTitles())
    }

    @Test
    fun `no Ertele once both snoozes are used`() {
        assertEquals(listOf("Bitti", "Bugün olmaz"), show(seq = 3, snoozesUsed = 2).actionTitles())
    }

    @Test
    fun `Bitti on a photo task opens the camera, on a Bitti task it closes the task`() {
        val photo = show(seq = 1).actions.first().actionIntent
        assertTrue(photo.isActivity)
        assertEquals("nuddle://photo/${Cast.TASK_ID}", shadowOf(photo).savedIntent.dataString)

        app.getSystemService<NotificationManager>()!!.cancelAll()
        val done = show(seq = 1, finishMode = "done").actions.first().actionIntent
        assertTrue(done.isBroadcast)
        assertEquals(PokeIntents.ACTION_DONE, shadowOf(done).savedIntent.action)
    }

    @Test
    fun `a poke is in a group of its own and takes the task's earlier note away`() {
        AndroidTaskAnnouncer(app, FakeClock(), TimeZone.of("Europe/Istanbul")).announce(aTask(), "Sibel")
        val poke = show(seq = 1)

        // In the app's automatic group a poke would be silent and its full screen would never open (E2E smoke 3).
        assertEquals("poke:${Cast.TASK_ID}", poke.group)
        assertEquals(listOf(poke.channelId), shadowOf(app.getSystemService<NotificationManager>()).allNotifications.map { it.channelId })
    }

    private fun titles(level: PokeLevel): List<String> =
        app.resources
            .getStringArray(
                when (level) {
                    PokeLevel.SILENT -> R.array.poke_titles_silent
                    PokeLevel.SOUND -> R.array.poke_titles_sound
                    PokeLevel.FULL_SCREEN -> R.array.poke_titles_full_screen
                },
            ).toList()

    @Test
    fun `the title is a line of the ringing level and the task is the text under it (NUD-130)`() {
        val manager = app.getSystemService<NotificationManager>()!!
        PokeLevel.entries.forEach { level ->
            manager.cancelAll()
            val step = PokeStep(Cast.TASK_ID, 1, 2, Instant.parse("2026-10-12T08:00:00+03:00"), level)
            notifications.show(aTask(), step, level)
            val poke = shadowOf(manager).allNotifications.single()

            val title = poke.extras.getString(Notification.EXTRA_TITLE)
            assertEquals(titles(level)[PokeTitles.index(step, titles(level).size)], title)
            assertEquals("Temizlikçi için para çek · 1.000 TL", poke.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            assertEquals(
                "Temizlikçi için para çek · 1.000 TL\nSon zamana 1 saat var.",
                poke.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString(),
            )
        }
    }

    @Test
    fun `two pokes in a row show different titles, and a re-show keeps its title (NUD-130)`() {
        val first = show(seq = 1).extras.getString(Notification.EXTRA_TITLE)
        val again = show(seq = 1).extras.getString(Notification.EXTRA_TITLE)
        val second = show(seq = 2).extras.getString(Notification.EXTRA_TITLE)
        assertEquals(first, again)
        assertNotEquals(first, second)
    }

    @Test
    fun `a one-time message keeps the message as its title (NUD-130)`() {
        val step = PokeStep(Cast.TASK_ID, 1, 1, Instant.parse("2026-10-12T08:00:00+03:00"), PokeLevel.SOUND)
        notifications.show(aTask().copy(title = "Seni seviyorum", detail = null, finishMode = "notice"), step, PokeLevel.SOUND)
        val poke = shadowOf(app.getSystemService<NotificationManager>()).allNotifications.single()
        assertEquals("Seni seviyorum", poke.extras.getString(Notification.EXTRA_TITLE))
    }

    @Test
    fun `every level has lines to rotate, none with an em dash (NUD-130, glossary)`() {
        PokeLevel.entries.forEach { level ->
            val lines = titles(level)
            assertTrue(lines.size >= PokeTitles.MIN_TITLES, "$level has ${lines.size}")
            assertEquals(lines.size, lines.toSet().size, "$level repeats a line")
            lines.forEach { assertFalse('\u2014' in it, it) }
        }
        val all = PokeLevel.entries.flatMap(::titles)
        assertEquals(all.size, all.toSet().size, "levels share a line")
    }
}
