package app.nuddle.data

import app.nuddle.api.model.SyncAnnouncement
import app.nuddle.api.model.SyncAnnouncementButton
import app.nuddle.data.announcement.RoomAnnouncementInbox
import app.nuddle.testing.InMemoryAnnouncementDao
import app.nuddle.testing.RecordingNewsAnnouncer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Announcements as sync brings them (ADR 0025): one notification per new one, read state kept here. */
class AnnouncementInboxTest {
    private val dao = InMemoryAnnouncementDao()
    private val announcer = RecordingNewsAnnouncer()
    private val inbox = RoomAnnouncementInbox(dao, announcer)

    private fun synced(
        id: String,
        summary: String = "Özet",
    ) = SyncAnnouncement(
        id = id,
        title = "Premium geldi",
        summary = summary,
        body = "**Yeni**",
        button = SyncAnnouncementButton("Davet et", "invite"),
        sentAt = "2026-10-19T10:00:00Z",
    )

    @Test
    fun `a new one is stored and notified once`() =
        runTest {
            inbox.receive(listOf(synced("n1")), notify = true)
            inbox.receive(listOf(synced("n1")), notify = true)

            assertEquals(listOf("n1"), announcer.announced)
            val stored = dao.get("n1")
            assertEquals("invite", stored?.buttonTarget)
            assertEquals(1_792_404_000_000, stored?.sentAt)
        }

    @Test
    fun `a full sync stores without notifying`() =
        runTest {
            inbox.receive(listOf(synced("n1")), notify = false)
            assertEquals(emptyList(), announcer.announced)
            assertEquals("Premium geldi", dao.get("n1")?.title)
        }

    @Test
    fun `a changed text keeps the phone's read state and does not notify again`() =
        runTest {
            inbox.receive(listOf(synced("n1")), notify = true)
            dao.markOpened("n1", at = 5)

            inbox.receive(listOf(synced("n1", summary = "Yeni metin")), notify = true)

            assertEquals("Yeni metin", dao.get("n1")?.summary)
            assertEquals(5L, dao.get("n1")?.openedAt)
            assertEquals(listOf("n1"), announcer.announced)
        }
}
