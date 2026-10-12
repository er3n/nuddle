package app.nuddle.data.sync

import app.nuddle.api.SyncApi
import app.nuddle.api.model.SyncConnection
import app.nuddle.api.model.SyncEvent
import app.nuddle.api.model.SyncPlan
import app.nuddle.api.model.SyncReceivedInvite
import app.nuddle.api.model.SyncSentInvite
import app.nuddle.api.model.SyncTask
import app.nuddle.api.model.TaskStatus
import app.nuddle.data.api.ApiResult
import app.nuddle.data.api.apiCall
import app.nuddle.data.db.ConnectionDao
import app.nuddle.data.db.ConnectionEntity
import app.nuddle.data.db.InviteDao
import app.nuddle.data.db.InviteEntity
import app.nuddle.data.db.PokeStepEntity
import app.nuddle.data.db.TaskDao
import app.nuddle.data.db.TaskEntity
import app.nuddle.data.db.TaskEventDao
import app.nuddle.data.db.TaskEventEntity
import app.nuddle.data.session.SessionStore
import app.nuddle.domain.QuietHours
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
}

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
    ) : Syncer {
        override suspend fun sync(): Boolean {
            val session = sessions.current()
            if (session?.registered != true) return true
            val cursor = sessions.syncCursor()
            val result = apiCall(json) { api.sync(cursor = cursor, deviceId = sessions.deviceId()) }
            if (result !is ApiResult.Success) return false
            val response = result.value
            val me = response.me
            sessions.saveMe(
                me.displayName,
                QuietHours.parse(me.quietHours.start, me.quietHours.end, me.quietHours.timeZone),
            )
            connections.upsert(response.connections.map(SyncConnection::toEntity))
            // Compared with what this phone had before the sync; a full sync (cursor null) is old news.
            val news = if (cursor == null) emptyList() else news(response.tasks, session.personId)
            tasks.upsert(response.tasks.map(SyncTask::toEntity))
            events.upsert(response.events.map(SyncEvent::toEntity))
            invites.upsert(response.sentInvites.map(SyncSentInvite::toEntity) + response.receivedInvites.map(SyncReceivedInvite::toEntity))
            response.plans.forEach { plan -> alarms.applyPlan(plan.taskId, plan.version, plan.toEntities()) }
            sessions.saveSyncCursor(response.cursor)
            alarms.rearm()
            tell(news, response.tasks, session.personId)
            return true
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
        createdAt = createdAt?.let { Instant.parse(it).toEpochMilliseconds() } ?: 0,
        rawText = rawText,
        photoSentAt = photoSentAt?.let { Instant.parse(it).toEpochMilliseconds() },
        reRequestNote = reRequestNote,
        statusChangedAt = Instant.parse(statusChangedAt).toEpochMilliseconds(),
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
internal val OPEN_STATUSES = setOf(TaskStatus.SCHEDULED.value, TaskStatus.REJECTED.value)
