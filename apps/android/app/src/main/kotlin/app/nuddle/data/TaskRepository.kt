package app.nuddle.data

import app.nuddle.api.CommandsApi
import app.nuddle.api.model.CreateTaskCommand
import app.nuddle.api.model.EditCommand
import app.nuddle.api.model.FinishMode
import app.nuddle.api.model.WriteTaskCommand
import app.nuddle.api.model.WriteTaskResult
import app.nuddle.data.api.ApiResult
import app.nuddle.data.api.apiCall
import app.nuddle.data.db.PokeStepDao
import app.nuddle.data.db.TaskDao
import app.nuddle.data.session.SessionStore
import app.nuddle.data.sync.SyncRepository
import kotlinx.serialization.json.Json
import javax.inject.Inject
import kotlin.time.Instant

sealed interface WriteOutcome {
    /** The server created (or changed) the task. It is on the device after the sync that follows. */
    data class Created(
        val taskId: String,
    ) : WriteOutcome

    /** Something is missing: the preview (2b) shows [draft] and asks one question. */
    data class Ask(
        val question: WriteTaskResult.Question?,
        val draft: TaskDraft?,
    ) : WriteOutcome

    /** No answer or an error answer. The text stays in the bar for "Tekrar dene" (6b). */
    data object Failed : WriteOutcome
}

/**
 * What the server understood from the text (WriteTaskResult.draft). [recipient] is "self", a connection's name as the
 * extraction gave it, or null; [deadline] is null when the text gave none or it could not be read.
 */
data class TaskDraft(
    val title: String?,
    val detail: String?,
    val finishMode: FinishMode,
    val recipient: String? = null,
    val deadline: Instant? = null,
)

/** Closes a task on the device at once (implemented by the alarm package, which also stops its alarms). */
interface TaskCloser {
    /** The recipient finished it. */
    suspend fun done(taskId: String)

    /** The sender takes it back: "Geri al" on the undo toast, or "Görevi iptal et" (7d) with an optional note. */
    suspend fun cancel(
        taskId: String,
        note: String? = null,
    )
}

/** Writing and finishing tasks from home (docs/ux/screens.md 2a, 2f). */
interface TaskRepository {
    /** Sends free text as `write_task`. Reuse [idempotencyKey] when retrying the same text (ADR 0006). */
    suspend fun write(
        text: String,
        idempotencyKey: String,
    ): WriteOutcome

    /** Creates a task from the preview (2b, 4a) once its question is answered. [recipientId] null is a task to self. */
    suspend fun create(
        title: String,
        detail: String?,
        deadline: Instant,
        finishMode: FinishMode,
        rawText: String,
        idempotencyKey: String,
        recipientId: String? = null,
    ): WriteOutcome

    /** "Düzenle" on the undo toast for a task to self: a new deadline, detail and finish mode kept. */
    suspend fun editDeadline(
        taskId: String,
        deadline: Instant,
        idempotencyKey: String,
    ): WriteOutcome

    /** A row changed in the sender's task detail (7c): the deadline or how it ends, the rest kept. */
    suspend fun edit(
        taskId: String,
        deadline: Instant?,
        finishMode: FinishMode?,
        idempotencyKey: String,
    ): WriteOutcome

    /**
     * "Şimdiden bitti": applied locally at once, then sent through the outbox. Returns the number of the poke that
     * caught the person (the last one that rang in the current plan), or null when none had rung.
     */
    suspend fun done(taskId: String): Int?

    /** "Geri al" or "İptal et" (7d): applied locally at once, then sent through the outbox. */
    suspend fun cancel(
        taskId: String,
        note: String? = null,
    )
}

class ApiTaskRepository
    @Inject
    constructor(
        private val commandsApi: CommandsApi,
        private val sync: SyncRepository,
        private val closer: TaskCloser,
        private val sessions: SessionStore,
        private val tasks: TaskDao,
        private val pokeSteps: PokeStepDao,
        private val json: Json,
    ) : TaskRepository {
        override suspend fun write(
            text: String,
            idempotencyKey: String,
        ): WriteOutcome {
            val result = apiCall(json) { commandsApi.writeTask(WriteTaskCommand(idempotencyKey = idempotencyKey, text = text)) }
            if (result !is ApiResult.Success) return WriteOutcome.Failed
            return when (result.value.kind) {
                WriteTaskResult.Kind.CREATED -> {
                    val taskId = result.value.taskId ?: return WriteOutcome.Failed
                    // A failed sync is not a failed write: the task exists, and the next sync brings it.
                    sync.sync()
                    WriteOutcome.Created(taskId)
                }

                WriteTaskResult.Kind.ASK -> {
                    WriteOutcome.Ask(
                        result.value.question,
                        result.value.draft?.let { draft ->
                            TaskDraft(
                                title = draft.title,
                                detail = draft.detail,
                                finishMode = draft.finishMode,
                                recipient = draft.recipient,
                                deadline = draft.deadline?.let { runCatching { Instant.parse(it) }.getOrNull() },
                            )
                        },
                    )
                }
            }
        }

        override suspend fun create(
            title: String,
            detail: String?,
            deadline: Instant,
            finishMode: FinishMode,
            rawText: String,
            idempotencyKey: String,
            recipientId: String?,
        ): WriteOutcome {
            val me = sessions.current()?.personId ?: return WriteOutcome.Failed
            val command =
                CreateTaskCommand(
                    idempotencyKey = idempotencyKey,
                    recipientId = recipientId ?: me,
                    title = title,
                    detail = detail,
                    deadline = deadline.toString(),
                    finishMode = finishMode,
                    rawText = rawText,
                )
            val result = apiCall(json) { commandsApi.createTask(command) }
            if (result !is ApiResult.Success) return WriteOutcome.Failed
            sync.sync()
            return WriteOutcome.Created(result.value.taskId)
        }

        override suspend fun editDeadline(
            taskId: String,
            deadline: Instant,
            idempotencyKey: String,
        ): WriteOutcome = edit(taskId, deadline, null, idempotencyKey)

        override suspend fun edit(
            taskId: String,
            deadline: Instant?,
            finishMode: FinishMode?,
            idempotencyKey: String,
        ): WriteOutcome {
            val task = tasks.get(taskId) ?: return WriteOutcome.Failed
            val command =
                EditCommand(
                    idempotencyKey = idempotencyKey,
                    taskId = taskId,
                    // What did not change is sent as it is: the edit command replaces every field it carries.
                    deadline = (deadline ?: Instant.fromEpochMilliseconds(task.deadline)).toString(),
                    detail = task.detail,
                    finishMode = finishMode ?: FinishMode.entries.first { it.value == task.finishMode },
                )
            val result = apiCall(json) { commandsApi.edit(command) }
            if (result !is ApiResult.Success) return WriteOutcome.Failed
            sync.sync()
            return WriteOutcome.Created(taskId)
        }

        override suspend fun done(taskId: String): Int? {
            val planVersion = tasks.get(taskId)?.planVersion
            val caught =
                pokeSteps
                    .forTask(taskId)
                    .filter { it.planVersion == planVersion && it.firedAt != null }
                    .maxOfOrNull { it.seq }
            closer.done(taskId)
            return caught
        }

        override suspend fun cancel(
            taskId: String,
            note: String?,
        ) = closer.cancel(taskId, note)
    }
