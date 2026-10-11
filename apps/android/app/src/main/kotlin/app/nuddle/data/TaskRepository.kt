package app.nuddle.data

import app.nuddle.api.CommandsApi
import app.nuddle.api.model.CreateTaskCommand
import app.nuddle.api.model.FinishMode
import app.nuddle.api.model.WriteTaskCommand
import app.nuddle.api.model.WriteTaskResult
import app.nuddle.data.api.ApiResult
import app.nuddle.data.api.apiCall
import app.nuddle.data.session.SessionStore
import app.nuddle.data.sync.SyncRepository
import kotlinx.serialization.json.Json
import javax.inject.Inject
import kotlin.time.Instant

sealed interface WriteOutcome {
    /** The server created the task. It is on the device after the sync that follows. */
    data object Created : WriteOutcome

    /** Something is missing: the preview (2b) shows [draft] and asks one question. */
    data class Ask(
        val question: WriteTaskResult.Question?,
        val draft: TaskDraft?,
    ) : WriteOutcome

    /** No answer or an error answer. The text stays in the bar for "Tekrar dene" (6b). */
    data object Failed : WriteOutcome
}

/** What the server understood from the text (WriteTaskResult.draft). */
data class TaskDraft(
    val title: String?,
    val detail: String?,
    val finishMode: FinishMode,
)

/** Closes a task the recipient finished (implemented by the alarm package, which also stops its alarms). */
fun interface TaskCloser {
    suspend fun done(taskId: String)
}

/** Writing and finishing tasks from home (docs/ux/screens.md 2a, 2f). */
interface TaskRepository {
    /** Sends free text as `write_task`. Reuse [idempotencyKey] when retrying the same text (ADR 0006). */
    suspend fun write(
        text: String,
        idempotencyKey: String,
    ): WriteOutcome

    /** Creates a task to self from the preview (2b) once its one question is answered. */
    suspend fun createForSelf(
        title: String,
        detail: String?,
        deadline: Instant,
        finishMode: FinishMode,
        rawText: String,
        idempotencyKey: String,
    ): WriteOutcome

    /** "Şimdiden bitti": applied locally at once, then sent through the outbox. */
    suspend fun done(taskId: String)
}

class ApiTaskRepository
    @Inject
    constructor(
        private val commandsApi: CommandsApi,
        private val sync: SyncRepository,
        private val closer: TaskCloser,
        private val sessions: SessionStore,
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
                    // A failed sync is not a failed write: the task exists, and the next sync brings it.
                    sync.sync()
                    WriteOutcome.Created
                }

                WriteTaskResult.Kind.ASK -> {
                    WriteOutcome.Ask(
                        result.value.question,
                        result.value.draft?.let { TaskDraft(it.title, it.detail, it.finishMode) },
                    )
                }
            }
        }

        override suspend fun createForSelf(
            title: String,
            detail: String?,
            deadline: Instant,
            finishMode: FinishMode,
            rawText: String,
            idempotencyKey: String,
        ): WriteOutcome {
            val me = sessions.current()?.personId ?: return WriteOutcome.Failed
            val command =
                CreateTaskCommand(
                    idempotencyKey = idempotencyKey,
                    recipientId = me,
                    title = title,
                    detail = detail,
                    deadline = deadline.toString(),
                    finishMode = finishMode,
                    rawText = rawText,
                )
            if (apiCall(json) { commandsApi.createTask(command) } !is ApiResult.Success) return WriteOutcome.Failed
            sync.sync()
            return WriteOutcome.Created
        }

        override suspend fun done(taskId: String) = closer.done(taskId)
    }
