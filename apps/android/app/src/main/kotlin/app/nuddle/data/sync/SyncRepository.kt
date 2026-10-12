package app.nuddle.data.sync

import app.nuddle.api.SyncApi
import app.nuddle.api.model.SyncPlan
import app.nuddle.api.model.SyncReceivedInvite
import app.nuddle.api.model.SyncSentInvite
import app.nuddle.api.model.SyncTask
import app.nuddle.api.model.TaskStatus
import app.nuddle.data.api.ApiResult
import app.nuddle.data.api.apiCall
import app.nuddle.data.db.InviteDao
import app.nuddle.data.db.InviteEntity
import app.nuddle.data.db.PokeStepEntity
import app.nuddle.data.db.TaskDao
import app.nuddle.data.db.TaskEntity
import app.nuddle.data.session.SessionStore
import app.nuddle.domain.QuietHours
import kotlinx.serialization.json.Json
import javax.inject.Inject
import kotlin.time.Instant

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

/**
 * `GET /api/v1/sync?cursor=&deviceId=` (ADR 0006, 0014): stores `me`, tasks and received plans, moves the cursor,
 * then re-arms alarms. The server is the source of truth; FCM only says when to call this.
 */
class SyncRepository
    @Inject
    constructor(
        private val api: SyncApi,
        private val sessions: SessionStore,
        private val tasks: TaskDao,
        private val invites: InviteDao,
        private val alarms: AlarmRearmer,
        private val json: Json,
    ) {
        suspend fun sync(): Boolean {
            if (sessions.current()?.registered != true) return true
            val result = apiCall(json) { api.sync(cursor = sessions.syncCursor(), deviceId = sessions.deviceId()) }
            if (result !is ApiResult.Success) return false
            val response = result.value
            val me = response.me
            sessions.saveMe(
                me.displayName,
                QuietHours.parse(me.quietHours.start, me.quietHours.end, me.quietHours.timeZone),
            )
            tasks.upsert(response.tasks.map(SyncTask::toEntity))
            invites.upsert(response.sentInvites.map(SyncSentInvite::toEntity) + response.receivedInvites.map(SyncReceivedInvite::toEntity))
            response.plans.forEach { plan -> alarms.applyPlan(plan.taskId, plan.version, plan.toEntities()) }
            sessions.saveSyncCursor(response.cursor)
            alarms.rearm()
            return true
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
