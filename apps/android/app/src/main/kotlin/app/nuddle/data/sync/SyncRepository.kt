package app.nuddle.data.sync

import app.nuddle.api.SyncApi
import app.nuddle.api.model.FinishMode
import app.nuddle.api.model.SyncConnection
import app.nuddle.api.model.SyncEvent
import app.nuddle.api.model.SyncPlan
import app.nuddle.api.model.SyncReceivedInvite
import app.nuddle.api.model.SyncSentInvite
import app.nuddle.api.model.SyncTask
import app.nuddle.api.model.TaskEventType
import app.nuddle.api.model.TaskStatus
import app.nuddle.api.model.Timing
import app.nuddle.data.EventTypes
import app.nuddle.data.api.ApiResult
import app.nuddle.data.api.apiCall
import app.nuddle.data.avatar.AvatarRef
import app.nuddle.data.avatar.AvatarRefresher
import app.nuddle.data.avatar.ME
import app.nuddle.data.db.ConnectionDao
import app.nuddle.data.db.ConnectionEntity
import app.nuddle.data.db.InviteDao
import app.nuddle.data.db.InviteEntity
import app.nuddle.data.db.PokeStepEntity
import app.nuddle.data.db.TaskDao
import app.nuddle.data.db.TaskEntity
import app.nuddle.data.db.TaskEventDao
import app.nuddle.data.db.TaskEventEntity
import app.nuddle.data.level
import app.nuddle.data.note
import app.nuddle.data.reason
import app.nuddle.data.session.SessionStore
import app.nuddle.data.session.TaskAcks
import app.nuddle.data.sinceLastDeadline
import app.nuddle.data.toProfile
import app.nuddle.domain.PokeLevel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import javax.inject.Inject
import kotlin.time.Instant

/** A sync right now, for screens that need fresh data (an invite link opened before its invite arrived). */
fun interface Syncer {
    suspend fun sync(): Boolean
}

/** Keeps alarms equal to the stored plans (implemented by the alarm package). */
interface AlarmRearmer {
    /** Stores a plan version for a task; alarms of the versions it replaces are cancelled first. */
    suspend fun applyPlan(
        taskId: String,
        version: Int,
        steps: List<PokeStepEntity>,
    )

    suspend fun rearm()
}

/** Tells me about a task someone else just wrote to me (4b). Implemented by the alarm package. */
interface TaskAnnouncer {
    fun announce(
        task: TaskEntity,
        senderName: String,
    )

    /** My task's photo came from [recipientName]: it waits for my approval (4f). */
    fun photoArrived(
        task: TaskEntity,
        recipientName: String,
    )

    /** The moment passed (Geri al within the undo window, the photo answered): the note goes too. */
    fun withdraw(taskId: String)

    /**
     * The human channel fired for my task to [recipient] (7a): [pokes] rang this round, [fullScreen] when one of them
     * was the full screen poke. Poking goes on.
     */
    fun cantReach(
        task: TaskEntity,
        recipient: ConnectionEntity,
        pokes: Int,
        fullScreen: Boolean,
    )

    /** My task to someone else is no longer open: "cevap vermiyor" goes. */
    fun withdrawCantReach(taskId: String)

    /** [otherName] closed a task between us (NUD-115): what happened, so it does not just vanish from a list. */
    fun closed(
        task: TaskEntity,
        otherName: String,
        close: TaskClose,
    )
}

/** How the other side closed a task between us (NUD-115). */
sealed interface TaskClose {
    /** The sender took the task to me back, with the note of 7d if any. */
    data class Cancelled(
        val note: String?,
    ) : TaskClose

    /** The sender gave the task to me to someone else (NUD-114). */
    data object Reassigned : TaskClose

    /** My task: the recipient said Bitti. */
    data object Done : TaskClose

    /** My task: the recipient said Bugün olmaz, with the note of 7f if any. */
    data class Dropped(
        val note: String?,
    ) : TaskClose

    /** My task: its time passed and the recipient did not finish it. */
    data object Missed : TaskClose
}

private val SYNC_LOCK = Mutex()

/**
 * `GET /api/v1/sync?cursor=&deviceId=` (ADR 0006, 0014): stores `me`, connections, tasks, their events, invites and
 * received plans, moves the cursor, then re-arms alarms. The server is the source of truth; FCM only says when to call this.
 * A task from someone else that arrives in a later sync is announced once; a full sync (sign-in, reinstall) brings
 * old ones too, so it announces nothing.
 */
class SyncRepository
    @Inject
    constructor(
        private val api: SyncApi,
        private val sessions: SessionStore,
        private val tasks: TaskDao,
        private val invites: InviteDao,
        private val connections: ConnectionDao,
        private val events: TaskEventDao,
        private val alarms: AlarmRearmer,
        private val announcer: TaskAnnouncer,
        private val json: Json,
        private val pending: PendingChanges,
        private val acks: TaskAcks,
        private val avatars: AvatarRefresher,
    ) : Syncer {
        /**
         * One sync at a time in the process: a push, the app coming to the front and a write can ask at once (NUD-59),
         * and the second one then starts from the cursor the first one saved.
         */
        override suspend fun sync(): Boolean = SYNC_LOCK.withLock { syncOnce() }

        private suspend fun syncOnce(): Boolean {
            val session = sessions.current()
            if (session?.registered != true) return true
            val cursor = sessions.syncCursor()
            val result = apiCall(json) { api.sync(cursor = cursor, deviceId = sessions.deviceId()) }
            if (result !is ApiResult.Success) return false
            val response = result.value
            val me = response.me
            sessions.saveMe(me.toProfile())
            connections.upsert(response.connections.map(SyncConnection::toEntity))
            // Pictures whose path changed are fetched; a missing one is tried again next time (NUD-72).
            avatars.refresh(
                listOf(AvatarRef(ME, session.personId, me.avatarPath)) +
                    response.connections.map { AvatarRef(it.personId, it.personId, it.avatarPath) },
            )
            // Compared with what this phone had before the sync; a full sync (cursor null) is old news.
            val news = if (cursor == null) emptyList() else news(response.tasks, session.personId)
            val closes =
                if (cursor == null) emptyList() else closes(response.tasks, response.events.map(SyncEvent::toEntity), session.personId)
            val unreached =
                if (cursor == null) {
                    emptySet()
                } else {
                    response.events
                        .filter { it.type == TaskEventType.HUMAN_NOTIFIED }
                        .map { it.taskId }
                        .toSet()
                }
            tasks.upsert(keepLocalChanges(response.tasks.map(SyncTask::toEntity)))
            events.upsert(response.events.map(SyncEvent::toEntity))
            invites.upsert(response.sentInvites.map(SyncSentInvite::toEntity) + response.receivedInvites.map(SyncReceivedInvite::toEntity))
            response.plans.forEach { plan -> alarms.applyPlan(plan.taskId, plan.version, plan.toEntities()) }
            sessions.saveSyncCursor(response.cursor)
            alarms.rearm()
            // The sender's tick (NUD-68): tasks to me are on this phone now.
            acks.reached(
                response.tasks
                    .filter { it.recipientId == session.personId && it.senderId != session.personId && it.status.value in OPEN_STATUSES }
                    .map { it.id },
            )
            tell(news, response.tasks, session.personId)
            tellUnreached(unreached, response.tasks, session.personId)
            closes.forEach { (task, close) ->
                val other = if (task.recipientId == session.personId) task.senderId else task.recipientId
                connections.get(other)?.let { announcer.closed(task.toEntity(), it.displayName, close) }
            }
            return true
        }

        /**
         * Tasks between me and someone else that the other side just closed (NUD-115). What I closed myself is already
         * closed here, so it is never told back to me. A one-time message is left out: its ticks say enough.
         */
        private suspend fun closes(
            synced: List<SyncTask>,
            events: List<TaskEventEntity>,
            me: String,
        ): List<Pair<SyncTask, TaskClose>> =
            synced.mapNotNull { task ->
                val before = tasks.get(task.id)
                if (task.senderId == task.recipientId || task.finishMode == FinishMode.NOTICE) return@mapNotNull null
                if (before == null || before.status == task.status.value) return@mapNotNull null
                val last = { type: String -> events.lastOrNull { it.taskId == task.id && it.type == type } }
                val close =
                    when {
                        task.recipientId == me && task.status == TaskStatus.CANCELLED && before.status in STILL_MINE -> {
                            val cancel = last(EventTypes.CANCELLED)
                            when (cancel?.reason) {
                                SENDER_CANCELLED -> TaskClose.Cancelled(cancel.note)
                                REASSIGNED -> TaskClose.Reassigned
                                else -> null
                            }
                        }

                        task.senderId == me && before.status in OPEN_STATUSES -> {
                            when (task.status) {
                                TaskStatus.DONE -> TaskClose.Done
                                TaskStatus.DROPPED -> TaskClose.Dropped(last(EventTypes.DROPPED)?.note)
                                TaskStatus.MISSED -> TaskClose.Missed
                                else -> null
                            }
                        }

                        else -> {
                            null
                        }
                    }
                close?.let { task to it }
            }

        /**
         * What this phone did and has not sent yet stays as it is here: the status, whether it is on my list and the
         * snooze. The server's version comes with the sync after the outbox sends it (NUD-64).
         */
        private suspend fun keepLocalChanges(synced: List<TaskEntity>): List<TaskEntity> {
            val unsent = pending.taskIds()
            if (unsent.isEmpty()) return synced
            return synced.map { task ->
                val local = task.takeIf { it.id in unsent }?.let { tasks.get(it.id) } ?: return@map task
                task.copy(
                    status = local.status,
                    inRecipientList = local.inRecipientList,
                    snoozesUsed = maxOf(local.snoozesUsed, task.snoozesUsed),
                    snoozedUntil = local.snoozedUntil ?: task.snoozedUntil,
                )
            }
        }

        /** 7a for my open tasks whose human channel just fired; it goes once the task is no longer open. */
        private suspend fun tellUnreached(
            unreached: Set<String>,
            synced: List<SyncTask>,
            me: String,
        ) {
            unreached.forEach { taskId ->
                val task = tasks.get(taskId)?.takeIf { it.senderId == me && it.recipientId != me && it.status in OPEN_STATUSES }
                val recipient = task?.let { connections.get(it.recipientId) } ?: return@forEach
                val round = events.forTask(taskId).sinceLastDeadline()
                val fired = round.filter { it.type == EventTypes.POKE_FIRED }
                announcer.cantReach(task, recipient, fired.size, fired.any { it.level == PokeLevel.FULL_SCREEN.name })
            }
            synced
                .filter { it.senderId == me && it.recipientId != me && it.status.value !in OPEN_STATUSES }
                .forEach { announcer.withdrawCantReach(it.id) }
        }

        /** Tasks to me that are new here, and my tasks to others whose photo just came (4f). */
        private suspend fun news(
            synced: List<SyncTask>,
            me: String,
        ): List<SyncTask> =
            synced.filter { task ->
                val before = tasks.get(task.id)
                when {
                    task.senderId == task.recipientId -> false
                    task.recipientId == me -> before == null && task.status.value in OPEN_STATUSES
                    else -> task.status == TaskStatus.AWAITING_APPROVAL && before?.status != TaskStatus.AWAITING_APPROVAL.value
                }
            }

        /** 4b for a new task to me, "Eren fotoğraf gönderdi" for a photo; a note whose moment passed goes away. */
        private suspend fun tell(
            news: List<SyncTask>,
            synced: List<SyncTask>,
            me: String,
        ) {
            news.forEach { task ->
                if (task.recipientId == me) {
                    connections.get(task.senderId)?.let { announcer.announce(task.toEntity(), it.displayName) }
                } else {
                    connections.get(task.recipientId)?.let { announcer.photoArrived(task.toEntity(), it.displayName) }
                }
            }
            synced
                .filter { it.senderId != it.recipientId }
                .filter { if (it.recipientId == me) it.status.value !in OPEN_STATUSES else it.status != TaskStatus.AWAITING_APPROVAL }
                .forEach { announcer.withdraw(it.id) }
        }
    }

internal fun SyncTask.toEntity() =
    TaskEntity(
        id = id,
        senderId = senderId,
        recipientId = recipientId,
        title = title,
        detail = detail,
        deadline = Instant.parse(deadline).toEpochMilliseconds(),
        finishMode = finishMode.value,
        status = status.value,
        planVersion = planVersion,
        inRecipientList = inRecipientList,
        snoozesUsed = snoozesUsed ?: 0,
        snoozedUntil = snoozedUntil?.let { Instant.parse(it).toEpochMilliseconds() },
        createdAt = createdAt?.let { Instant.parse(it).toEpochMilliseconds() } ?: 0,
        rawText = rawText,
        photoSentAt = photoSentAt?.let { Instant.parse(it).toEpochMilliseconds() },
        reRequestNote = reRequestNote,
        statusChangedAt = Instant.parse(statusChangedAt).toEpochMilliseconds(),
        remindAt = remindAt?.takeIf { timing == Timing.AT }?.let { Instant.parse(it).toEpochMilliseconds() },
        repeatEvery = repeat?.every?.value,
        repeatDays = repeat?.days?.joinToString(","),
        repeatTime = repeat?.time,
    )

internal fun SyncEvent.toEntity() =
    TaskEventEntity(
        id = id,
        taskId = taskId,
        actorId = actorId,
        type = type.value,
        payload = payload.toString(),
        at = Instant.parse(at).toEpochMilliseconds(),
    )

internal fun SyncConnection.toEntity() =
    ConnectionEntity(
        personId = personId,
        displayName = displayName,
        relationship = relationship.value,
        status = status.value,
        reachability = reachability.value,
        phone = phone,
        connectedAt = connectedAt?.let { Instant.parse(it).toEpochMilliseconds() },
        notifyMe = notifyMe ?: true,
        avatarPath = avatarPath,
    )

internal fun SyncSentInvite.toEntity() =
    InviteEntity(
        id = id,
        code = code,
        sent = true,
        name = prefilledName,
        phone = inviteePhone,
        inviterId = null,
        prefilledName = prefilledName,
        relationship = relationship.value,
        status = status.value,
        createdAt = Instant.parse(createdAt).toEpochMilliseconds(),
    )

internal fun SyncReceivedInvite.toEntity() =
    InviteEntity(
        id = id,
        code = code,
        sent = false,
        name = inviterName,
        phone = null,
        inviterId = inviterId,
        prefilledName = prefilledName,
        relationship = relationship.value,
        status = status.value,
        createdAt = Instant.parse(createdAt).toEpochMilliseconds(),
    )

internal fun SyncPlan.toEntities() =
    steps.map { step ->
        PokeStepEntity(
            taskId = taskId,
            planVersion = version,
            seq = step.seq,
            plannedAt = Instant.parse(step.plannedAt).toEpochMilliseconds(),
            level = step.level.value,
        )
    }

/** Statuses in which the recipient is still poked (docs/domain.md). */
private const val SENDER_CANCELLED = "sender_cancelled"
private const val REASSIGNED = "reassigned"

/** A task to me that was still mine here: open, waiting for approval or missed, not one I closed myself. */
private val STILL_MINE =
    setOf(TaskStatus.SCHEDULED.value, TaskStatus.REJECTED.value, TaskStatus.AWAITING_APPROVAL.value, TaskStatus.MISSED.value)

internal val OPEN_STATUSES = setOf(TaskStatus.SCHEDULED.value, TaskStatus.REJECTED.value)
