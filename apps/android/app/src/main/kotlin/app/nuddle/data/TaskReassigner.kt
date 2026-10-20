package app.nuddle.data

import app.nuddle.api.CommandsApi
import app.nuddle.api.model.ReassignCommand
import app.nuddle.data.api.ApiResult
import app.nuddle.data.api.apiCall
import app.nuddle.data.sync.SyncRepository
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Kime changed in 7c (NUD-114): the task closes for its recipient and starts for [recipientId] (self or a connection).
 * Online only, like a row edit; the result names the new task, which the sync right after brings.
 */
fun interface TaskReassigner {
    suspend fun reassign(
        taskId: String,
        recipientId: String,
        idempotencyKey: String,
    ): WriteOutcome
}

class ApiTaskReassigner
    @Inject
    constructor(
        private val commandsApi: CommandsApi,
        private val sync: SyncRepository,
        private val json: Json,
    ) : TaskReassigner {
        override suspend fun reassign(
            taskId: String,
            recipientId: String,
            idempotencyKey: String,
        ): WriteOutcome {
            val command = ReassignCommand(idempotencyKey = idempotencyKey, taskId = taskId, recipientId = recipientId)
            val result = apiCall(json) { commandsApi.reassign(command) }
            if (result !is ApiResult.Success) return WriteOutcome.Failed
            sync.sync()
            return WriteOutcome.Created(result.value.taskId)
        }
    }
