package app.nuddle.testing

import app.nuddle.data.db.TaskEventDao
import app.nuddle.data.db.TaskEventEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class InMemoryTaskEventDao : TaskEventDao {
    private val events = MutableStateFlow<Map<String, TaskEventEntity>>(emptyMap())

    override suspend fun upsert(events: List<TaskEventEntity>) {
        this.events.value += events.associateBy { it.id }
    }

    override suspend fun forTask(taskId: String): List<TaskEventEntity> =
        events.value.values
            .filter { it.taskId == taskId }
            .sortedBy { it.at }

    override fun observeAll(): Flow<List<TaskEventEntity>> = events.map { all -> all.values.sortedBy { it.at } }
}
