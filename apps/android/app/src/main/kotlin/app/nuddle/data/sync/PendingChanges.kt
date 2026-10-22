package app.nuddle.data.sync

import app.nuddle.data.db.OutboxDao
import app.nuddle.data.photo.PhotoFiles
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

/**
 * Tasks this phone changed locally that the server does not know about yet: a Bitti, İptal, Ertele, Bugün olmaz or
 * the undo toast's finish switch still in the outbox, or a photo still uploading. A sync keeps their local state
 * until it is sent, so a sync that lands in between does not bring the card and its alarms back (NUD-64).
 */
fun interface PendingChanges {
    suspend fun taskIds(): Set<String>
}

/** Outbox commands applied on the phone first (the rest, like `report_fired`, change nothing the phone shows). */
private val LOCAL_FIRST =
    setOf("done", "cancel", "cant_today", "snooze", "did_it_later", "remove_missed", "sender_handles", "edit")

class LocalPendingChanges
    @Inject
    constructor(
        private val outbox: OutboxDao,
        private val photos: PhotoFiles,
        private val json: Json,
    ) : PendingChanges {
        override suspend fun taskIds(): Set<String> =
            outbox
                .pending()
                .filter { it.command in LOCAL_FIRST }
                .mapNotNull { entry ->
                    runCatching { json.parseToJsonElement(entry.body) as? JsonObject }
                        .getOrNull()
                        ?.get("taskId")
                        ?.jsonPrimitive
                        ?.content
                }.toSet() + photos.pendingTaskIds()
    }
