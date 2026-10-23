package app.nuddle.data.outbox

import app.nuddle.data.api.RawCommandApi
import app.nuddle.data.db.OutboxDao
import app.nuddle.data.session.SessionStore
import app.nuddle.domain.OutboxPolicy
import app.nuddle.domain.SendOutcome
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject

/** Sends queued commands in order. Returns false when something must be retried later. */
class OutboxSender
    @Inject
    constructor(
        private val dao: OutboxDao,
        private val api: RawCommandApi,
        private val sessions: SessionStore,
    ) {
        suspend fun drain(): Boolean {
            for (entry in dao.pending()) {
                val status =
                    try {
                        api.send(entry.command, entry.body.toRequestBody(JSON)).code()
                    } catch (_: IOException) {
                        // No answer: try again with backoff.
                        null
                    }
                when (OutboxPolicy.outcome(status)) {
                    SendOutcome.SENT -> {
                        dao.remove(entry.idempotencyKey)
                    }

                    SendOutcome.DROP -> {
                        dao.remove(entry.idempotencyKey)
                        // The phone showed this answer before the server had it; a sync meanwhile kept that local state
                        // and moved its cursor past the server's own change, which the refusal does not touch again.
                        // The sync after this drain is a full one, so the server's state comes back (NUD-168).
                        if (entry.command in LOCAL_FIRST) sessions.saveSyncCursor(null)
                    }

                    SendOutcome.RETRY -> {
                        dao.countAttempt(entry.idempotencyKey)
                        return false
                    }
                }
            }
            return true
        }

        private companion object {
            val JSON = "application/json".toMediaType()

            /** Commands the phone applies to its own copy of the task before the server answers. */
            val LOCAL_FIRST = setOf("done", "snooze", "cant_today", "cancel", "approve", "did_it_later", "edit", "remove_missed")
        }
    }
