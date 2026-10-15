package app.nuddle.testing

import app.nuddle.data.db.PokeStepDao
import app.nuddle.data.db.PokeStepEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class InMemoryPokeStepDao : PokeStepDao {
    private val steps = MutableStateFlow<List<PokeStepEntity>>(emptyList())

    override suspend fun all(): List<PokeStepEntity> = steps.value

    override fun observeAll(): Flow<List<PokeStepEntity>> = steps

    override suspend fun forTask(taskId: String): List<PokeStepEntity> = steps.value.filter { it.taskId == taskId }

    override suspend fun get(
        taskId: String,
        planVersion: Int,
        seq: Int,
    ): PokeStepEntity? = steps.value.firstOrNull { it.taskId == taskId && it.planVersion == planVersion && it.seq == seq }

    override suspend fun insertIgnoringExisting(steps: List<PokeStepEntity>) {
        this.steps.update { known ->
            known + steps.filter { new -> known.none { it.taskId == new.taskId && it.planVersion == new.planVersion && it.seq == new.seq } }
        }
    }

    override suspend fun deleteOtherVersions(
        taskId: String,
        planVersion: Int,
    ) {
        steps.update { all -> all.filterNot { it.taskId == taskId && it.planVersion != planVersion } }
    }

    override suspend fun markFired(
        taskId: String,
        planVersion: Int,
        seq: Int,
        firedAt: Long,
    ) {
        steps.update { all ->
            all.map { if (it.taskId == taskId && it.planVersion == planVersion && it.seq == seq) it.copy(firedAt = firedAt) else it }
        }
    }
}
