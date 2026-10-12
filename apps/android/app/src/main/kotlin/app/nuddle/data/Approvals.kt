package app.nuddle.data

import app.nuddle.api.model.ApproveCommand
import app.nuddle.api.model.ReRequestCommand
import app.nuddle.api.model.TaskStatus
import app.nuddle.data.db.TaskDao
import app.nuddle.data.outbox.Outbox
import app.nuddle.domain.IdGenerator
import javax.inject.Inject

/**
 * The sender's answer to a photo (4f, docs/product.md, Photo and approval): applied on this phone at once and sent
 * through the outbox, like the recipient's Bitti. Approving closes the task; Tekrar iste starts the pokes again
 * where they stopped, with an optional note.
 */
interface Approvals {
    suspend fun approve(taskId: String)

    suspend fun reRequest(
        taskId: String,
        note: String?,
    )
}

class OutboxApprovals
    @Inject
    constructor(
        private val tasks: TaskDao,
        private val outbox: Outbox,
        private val ids: IdGenerator,
    ) : Approvals {
        override suspend fun approve(taskId: String) {
            tasks.setStatus(taskId, TaskStatus.DONE.value)
            val id = ids.next()
            outbox.enqueue("approve", id, ApproveCommand(idempotencyKey = id, taskId = taskId), ApproveCommand.serializer())
        }

        override suspend fun reRequest(
            taskId: String,
            note: String?,
        ) {
            tasks.setStatus(taskId, TaskStatus.REJECTED.value)
            val id = ids.next()
            outbox.enqueue(
                "re_request",
                id,
                ReRequestCommand(idempotencyKey = id, taskId = taskId, note = note),
                ReRequestCommand.serializer(),
            )
        }
    }
