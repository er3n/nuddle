package app.nuddle.data

import app.nuddle.api.model.EditCommand
import app.nuddle.api.model.FinishMode
import app.nuddle.data.db.TaskDao
import app.nuddle.data.outbox.Outbox
import app.nuddle.domain.IdGenerator
import javax.inject.Inject

/**
 * The finish switch on the undo toast (4i, NUD-137): the same `edit` as Nasıl bitsin in 7c, carrying only the finish
 * mode. Unlike a row edit it is applied on the phone first and sent through the outbox, so it works offline.
 */
fun interface FinishSwitcher {
    suspend fun switch(
        taskId: String,
        finishMode: FinishMode,
    )
}

class OutboxFinishSwitcher
    @Inject
    constructor(
        private val tasks: TaskDao,
        private val outbox: Outbox,
        private val ids: IdGenerator,
    ) : FinishSwitcher {
        override suspend fun switch(
            taskId: String,
            finishMode: FinishMode,
        ) {
            tasks.setFinishMode(taskId, finishMode.value)
            val id = ids.next()
            // Deadline and detail left out: the server keeps them, and a replay later cannot move the time.
            val command = EditCommand(idempotencyKey = id, taskId = taskId, finishMode = finishMode)
            outbox.enqueue("edit", id, command, EditCommand.serializer())
        }
    }
