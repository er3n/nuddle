package app.nuddle.data.announcement

import app.nuddle.api.model.SyncAnnouncement
import app.nuddle.data.db.AnnouncementDao
import app.nuddle.data.db.AnnouncementEntity
import javax.inject.Inject
import kotlin.time.Instant

/** Messages from Nuddle as sync brings them (ADR 0025). */
fun interface AnnouncementInbox {
    /** Stores [synced]; with [notify], each one new on this phone gets a notification. */
    suspend fun receive(
        synced: List<SyncAnnouncement>,
        notify: Boolean,
    )
}

/** Shows an announcement as a notification on the `news` channel. Implemented by the alarm package. */
interface NewsAnnouncer {
    fun announce(announcement: AnnouncementEntity)

    /** Opened in the app: the notification has done its job. */
    fun withdraw(announcementId: String)
}

/**
 * Keeps this phone's [AnnouncementEntity.openedAt] across syncs: a changed text arrives as the same id and is stored
 * without a second notification.
 */
class RoomAnnouncementInbox
    @Inject
    constructor(
        private val announcements: AnnouncementDao,
        private val announcer: NewsAnnouncer,
    ) : AnnouncementInbox {
        override suspend fun receive(
            synced: List<SyncAnnouncement>,
            notify: Boolean,
        ) {
            if (synced.isEmpty()) return
            val stored = synced.map { it.toEntity(announcements.get(it.id)) }
            val fresh = stored.filter { announcements.get(it.id) == null }
            announcements.upsert(stored)
            if (notify) fresh.forEach(announcer::announce)
        }
    }

internal fun SyncAnnouncement.toEntity(before: AnnouncementEntity?) =
    AnnouncementEntity(
        id = id,
        title = title,
        summary = summary,
        body = body,
        buttonLabel = button?.label,
        buttonTarget = button?.target,
        sentAt = Instant.parse(sentAt).toEpochMilliseconds(),
        openedAt = before?.openedAt,
    )
