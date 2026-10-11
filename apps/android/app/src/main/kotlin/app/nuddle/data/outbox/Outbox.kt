package app.nuddle.data.outbox

import app.nuddle.data.db.OutboxDao
import app.nuddle.data.db.OutboxEntity
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import kotlin.time.Clock

/** Wakes whatever drains the outbox (WorkManager in the app, a recorder in tests). */
fun interface OutboxDrainer {
    fun drainSoon()
}

/**
 * Commands that must reach the server even if the phone is offline right now (docs/architecture.md, Sync model).
 * Local effects are applied by the caller first; the command is stored with its idempotency key and replayed until
 * the server answers.
 */
class Outbox
    @Inject
    constructor(
        private val dao: OutboxDao,
        private val json: Json,
        private val clock: Clock,
        private val drainer: OutboxDrainer,
    ) {
        suspend fun <T> enqueue(
            command: String,
            idempotencyKey: String,
            body: T,
            serializer: KSerializer<T>,
        ) {
            dao.enqueue(
                OutboxEntity(
                    idempotencyKey = idempotencyKey,
                    command = command,
                    body = json.encodeToString(serializer, body),
                    createdAt = clock.now().toEpochMilliseconds(),
                ),
            )
            drainer.drainSoon()
        }
    }
