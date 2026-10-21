package app.nuddle.data

import app.nuddle.api.CommandsApi
import app.nuddle.api.model.CreateTaskCommand
import app.nuddle.api.model.DidItLaterCommand
import app.nuddle.api.model.EditCommand
import app.nuddle.api.model.FinishMode
import app.nuddle.api.model.NewDeadlineCommand
import app.nuddle.api.model.RemoveMissedCommand
import app.nuddle.api.model.RepeatRule
import app.nuddle.api.model.SenderHandlesCommand
import app.nuddle.api.model.SetStartCommand
import app.nuddle.api.model.TaskStatus
import app.nuddle.api.model.Timing
import app.nuddle.api.model.WriteTaskCommand
import app.nuddle.api.model.WriteTaskResult
import app.nuddle.data.api.ApiFailure
import app.nuddle.data.api.ApiResult
import app.nuddle.data.api.apiCall
import app.nuddle.data.db.PokeStepDao
import app.nuddle.data.db.TaskDao
import app.nuddle.data.db.shownTime
import app.nuddle.data.outbox.Outbox
import app.nuddle.data.session.SessionStore
import app.nuddle.data.sync.SyncRepository
import app.nuddle.domain.IdGenerator
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
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
        /** For [WriteTaskResult.Question.QUIET_HOURS]: the recipient's quiet hours (NUD-120). */
        val quiet: QuietAsk? = null,
    ) : WriteOutcome

    /** The text is not a task ("merhaba"): [reply] shows above the bar and nothing was created (NUD-52). */
    data class Chat(
        val reply: String,
    ) : WriteOutcome

    /** No answer or an error answer. The text stays in the bar for "Tekrar dene" (6b). */
    data object Failed : WriteOutcome

    /** The server broke on our side (`internal` or another 5xx it named): 6f shows [code] (open-questions#39). */
    data class Broken(
        val code: String,
    ) : WriteOutcome
}

private const val HTTP_SERVER_MIN = 500

/** Waits before sending a write again after no answer (NUD-53). */
private val WRITE_RETRY_WAITS = listOf(1.seconds, 3.seconds)
private const val UPSTREAM_FAILED = "DRT-5002"

/** 6f for a server error with a code, except the LLM or SMS provider failing (6b); anything else is 6b. */
internal fun ApiResult.Failure.toOutcome(): WriteOutcome {
    val http = failure as? ApiFailure.Http ?: return WriteOutcome.Failed
    val code = http.code?.takeIf { http.status >= HTTP_SERVER_MIN && it != UPSTREAM_FAILED }
    return code?.let(WriteOutcome::Broken) ?: WriteOutcome.Failed
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
    /** [deadline] is the moment of an `at` task ("2 dakika sonra"), not a deadline (ADR 0017). */
    val moment: Boolean = false,
    /** For an `at` task: how long after the moment it is missed, as the extraction judged. */
    val missAfterMinutes: Int? = null,
    /** For an `at` task: how long before the moment the pokes begin, as the extraction judged (NUD-70). */
    val leadMinutes: Int? = null,
    /** A repeating task's rule (ADR 0021); the deadline is its first occurrence. */
    val repeat: RepeatRule? = null,
)

/** A moment inside the recipient's quiet hours (NUD-120): "23:00", "06:00", and the moment that starts as they end. */
data class QuietAsk(
    val start: String,
    val end: String,
    val suggested: Instant,
)

/** Closes a task on the device at once (implemented by the alarm package, which also stops its alarms). */
interface TaskCloser {
    /** The recipient finished it. */
    suspend fun done(
        taskId: String,
        cameraUnavailable: Boolean = false,
    )

    /**
     * A photo was taken for the task (4d): pokes stop at once and it waits for approval, or closes for a task to
     * self (open-questions#7). The upload follows separately.
     */
    suspend fun photoTaken(taskId: String)

    /** The sender takes it back: "Geri al" on the undo toast, or "Görevi iptal et" (7d) with an optional note. */
    suspend fun cancel(
        taskId: String,
        note: String? = null,
    )

    /** The recipient cannot today (7f): dropped, with an optional note for the sender. */
    suspend fun cantToday(
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
        moment: Boolean = false,
        missAfterMinutes: Int? = null,
        repeat: RepeatRule? = null,
    ): WriteOutcome

    /** "Düzenle" on the undo toast for a task to self: a new deadline, detail and finish mode kept. */
    suspend fun editDeadline(
        taskId: String,
        deadline: Instant,
        idempotencyKey: String,
    ): WriteOutcome = edit(taskId, deadline, null, idempotencyKey)

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
    suspend fun done(
        taskId: String,
        cameraUnavailable: Boolean = false,
    ): Int?

    /** "Geri al" or "İptal et" (7d): applied locally at once, then sent through the outbox. */
    suspend fun cancel(
        taskId: String,
        note: String? = null,
    )

    /** "Sonradan yaptım" on a missed task (7e): done, late (open-questions#9). Local first, then the outbox. */
    suspend fun didItLater(taskId: String)

    /** "Kaldır" on my missed card (NUD-49): off my list only; the task stays missed for the sender. Local first. */
    suspend fun removeMissed(taskId: String)

    /** "Ben hallederim" on a dropped task (5d): the sender takes it over (open-questions#8). Local first. */
    suspend fun senderHandles(taskId: String)

    /** "Yeni zaman ver" or "Süreyi uzat" (5d): the task opens again with a new deadline and a new plan. */
    suspend fun newDeadline(
        taskId: String,
        deadline: Instant,
        idempotencyKey: String,
    ): WriteOutcome

    /** The recipient moves when the pokes begin (NUD-78); the deadline stays. Reuse [idempotencyKey] when retrying. */
    suspend fun setStart(
        taskId: String,
        startAt: Instant,
        idempotencyKey: String,
    ): WriteOutcome
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
        private val outbox: Outbox,
        private val ids: IdGenerator,
        private val json: Json,
    ) : TaskRepository {
        override suspend fun write(
            text: String,
            idempotencyKey: String,
        ): WriteOutcome {
            val written =
                when (val result = writeWithRetry(text, idempotencyKey)) {
                    is ApiResult.Success -> result.value
                    is ApiResult.Failure -> return result.toOutcome()
                }
            return when (written.kind) {
                WriteTaskResult.Kind.CREATED -> {
                    val taskId = written.taskId ?: return WriteOutcome.Failed
                    // A failed sync is not a failed write: the task exists, and the next sync brings it.
                    sync.sync()
                    WriteOutcome.Created(taskId)
                }

                WriteTaskResult.Kind.CHAT -> {
                    written.reply?.let(WriteOutcome::Chat) ?: WriteOutcome.Failed
                }

                WriteTaskResult.Kind.ASK -> {
                    WriteOutcome.Ask(
                        written.question,
                        written.draft?.let { draft ->
                            TaskDraft(
                                title = draft.title,
                                detail = draft.detail,
                                finishMode = draft.finishMode,
                                recipient = draft.recipient,
                                deadline = draft.deadline?.let { runCatching { Instant.parse(it) }.getOrNull() },
                                moment = draft.timing == Timing.AT,
                                missAfterMinutes = draft.missAfterMinutes,
                                leadMinutes = draft.leadMinutes,
                                repeat = draft.repeat,
                            )
                        },
                        written.quiet?.let { quiet ->
                            runCatching { Instant.parse(quiet.suggested) }.getOrNull()?.let { QuietAsk(quiet.start, quiet.end, it) }
                        },
                    )
                }
            }
        }

        /**
         * A write that got no answer (timeout, or the connection cut when the app went to the background) is sent again
         * with the same key: the server returns the task it already made instead of a second one (NUD-53).
         */
        private suspend fun writeWithRetry(
            text: String,
            idempotencyKey: String,
        ): ApiResult<WriteTaskResult> {
            var result = apiCall(json) { commandsApi.writeTask(WriteTaskCommand(idempotencyKey = idempotencyKey, text = text)) }
            for (wait in WRITE_RETRY_WAITS) {
                if (result !is ApiResult.Failure || result.failure != ApiFailure.Network) break
                delay(wait)
                result = apiCall(json) { commandsApi.writeTask(WriteTaskCommand(idempotencyKey = idempotencyKey, text = text)) }
            }
            return result
        }

        override suspend fun create(
            title: String,
            detail: String?,
            deadline: Instant,
            finishMode: FinishMode,
            rawText: String,
            idempotencyKey: String,
            recipientId: String?,
            moment: Boolean,
            missAfterMinutes: Int?,
            repeat: RepeatRule?,
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
                    timing = if (moment) Timing.AT else Timing.BY,
                    missAfterMinutes = missAfterMinutes?.takeIf { moment },
                    repeat = repeat,
                )
            val created =
                when (val result = apiCall(json) { commandsApi.createTask(command) }) {
                    is ApiResult.Success -> result.value
                    is ApiResult.Failure -> return result.toOutcome()
                }
            sync.sync()
            return WriteOutcome.Created(created.taskId)
        }

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
                    deadline = (deadline ?: Instant.fromEpochMilliseconds(task.shownTime)).toString(),
                    detail = task.detail,
                    finishMode = finishMode ?: FinishMode.entries.first { it.value == task.finishMode },
                )
            val result = apiCall(json) { commandsApi.edit(command) }
            if (result !is ApiResult.Success) return WriteOutcome.Failed
            sync.sync()
            return WriteOutcome.Created(taskId)
        }

        override suspend fun done(
            taskId: String,
            cameraUnavailable: Boolean,
        ): Int? {
            val planVersion = tasks.get(taskId)?.planVersion
            val caught =
                pokeSteps
                    .forTask(taskId)
                    .filter { it.planVersion == planVersion && it.firedAt != null }
                    .maxOfOrNull { it.seq }
            closer.done(taskId, cameraUnavailable)
            return caught
        }

        override suspend fun cancel(
            taskId: String,
            note: String?,
        ) = closer.cancel(taskId, note)

        // A missed or dropped task has no alarms left, so closing it is only its status.
        override suspend fun didItLater(taskId: String) {
            tasks.setStatus(taskId, TaskStatus.DONE.value)
            val id = ids.next()
            outbox.enqueue("did_it_later", id, DidItLaterCommand(idempotencyKey = id, taskId = taskId), DidItLaterCommand.serializer())
        }

        override suspend fun removeMissed(taskId: String) {
            tasks.hideFromMyList(taskId)
            val id = ids.next()
            outbox.enqueue("remove_missed", id, RemoveMissedCommand(idempotencyKey = id, taskId = taskId), RemoveMissedCommand.serializer())
        }

        override suspend fun senderHandles(taskId: String) {
            tasks.setStatus(taskId, TaskStatus.CANCELLED.value)
            val id = ids.next()
            outbox.enqueue(
                "sender_handles",
                id,
                SenderHandlesCommand(idempotencyKey = id, taskId = taskId),
                SenderHandlesCommand.serializer(),
            )
        }

        override suspend fun newDeadline(
            taskId: String,
            deadline: Instant,
            idempotencyKey: String,
        ): WriteOutcome {
            val command = NewDeadlineCommand(idempotencyKey = idempotencyKey, taskId = taskId, deadline = deadline.toString())
            val result = apiCall(json) { commandsApi.newDeadline(command) }
            if (result !is ApiResult.Success) return WriteOutcome.Failed
            sync.sync()
            return WriteOutcome.Created(taskId)
        }

        override suspend fun setStart(
            taskId: String,
            startAt: Instant,
            idempotencyKey: String,
        ): WriteOutcome {
            val command = SetStartCommand(idempotencyKey = idempotencyKey, taskId = taskId, startAt = startAt.toString())
            val result = apiCall(json) { commandsApi.setStart(command) }
            if (result !is ApiResult.Success) return WriteOutcome.Failed
            // The new plan comes with the sync; the detail's start line follows it.
            sync.sync()
            return WriteOutcome.Created(taskId)
        }
    }
