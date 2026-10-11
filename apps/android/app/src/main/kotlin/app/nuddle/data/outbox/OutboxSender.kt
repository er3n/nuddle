package app.nuddle.data.outbox

import app.nuddle.data.api.RawCommandApi
import app.nuddle.data.db.OutboxDao
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
                    SendOutcome.SENT, SendOutcome.DROP -> {
                        dao.remove(entry.idempotencyKey)
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
        }
    }
