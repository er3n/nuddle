package app.nuddle.testing

import app.nuddle.data.db.QueuedWriteDao
import app.nuddle.data.db.QueuedWriteEntity
import app.nuddle.data.network.Connectivity
import app.nuddle.data.network.ErrorReporter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Online until a test says otherwise. */
class FakeConnectivity(
    online: Boolean = true,
) : Connectivity {
    override val online = MutableStateFlow(online)
}

class InMemoryQueuedWriteDao : QueuedWriteDao {
    private val entries = MutableStateFlow<Map<String, QueuedWriteEntity>>(emptyMap())

    val all: List<QueuedWriteEntity> get() = entries.value.values.sortedWith(compareBy({ it.createdAt }, { it.idempotencyKey }))

    override fun observeAll(): Flow<List<QueuedWriteEntity>> =
        entries.map { all -> all.values.sortedWith(compareBy({ it.createdAt }, { it.idempotencyKey })) }

    override suspend fun add(entry: QueuedWriteEntity) {
        if (entry.idempotencyKey !in entries.value) entries.value += entry.idempotencyKey to entry
    }

    override suspend fun remove(key: String) {
        entries.value -= key
    }
}

class RecordingErrorReporter : ErrorReporter {
    val reported = mutableListOf<String>()

    override fun report(code: String) {
        reported += code
    }
}
