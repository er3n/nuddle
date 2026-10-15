package app.nuddle.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A task as last synced (SyncTask), plus local state applied before the server knows (outbox). */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val senderId: String,
    val recipientId: String,
    val title: String,
    val detail: String?,
    /** Epoch milliseconds. */
    val deadline: Long,
    val finishMode: String,
    val status: String,
    val planVersion: Int,
    val inRecipientList: Boolean,
    /** From sync, and bumped locally on Ertele so the next one is right offline too (NUD-36). */
    @ColumnInfo(defaultValue = "0") val snoozesUsed: Int = 0,
    /** Epoch milliseconds when the last snooze ends; null without one ("Ertelendi · tekrar 22:37", NUD-63). */
    val snoozedUntil: Long? = null,
    /** Epoch milliseconds; "Sibel yazdı · 14:32" (4c). 0 when an older server did not send it. */
    @ColumnInfo(defaultValue = "0") val createdAt: Long = 0,
    /** What the sender typed: "Yazdığın: ..." (7c). Only the sender gets it. */
    val rawText: String? = null,
    /** Epoch milliseconds of the last photo sent (4e, 4f); null before one. */
    val photoSentAt: Long? = null,
    /** The note of the last Tekrar iste while the task is REJECTED (4g). */
    val reRequestNote: String? = null,
    /** Epoch milliseconds of the last status change: a cancelled row stays a day after it (7e). 0 before NUD-16. */
    @ColumnInfo(defaultValue = "0") val statusChangedAt: Long = 0,
    /**
     * Epoch milliseconds of the moment of an `at` task ("2 dakika sonra su iç"), null for a `by` task (ADR 0017). The
     * deadline of an `at` task is when it becomes missed; screens show this moment instead.
     */
    val remindAt: Long? = null,
)

/** The time the sender picked and screens show: the moment of an `at` task, otherwise the deadline (ADR 0017). */
val TaskEntity.shownTime: Long get() = remindAt ?: deadline

/** A task "at" a moment rather than "by" a deadline (ADR 0017). */
val TaskEntity.isMoment: Boolean get() = remindAt != null

/** A connection from sync (SyncConnection): what [displayName] is to me, how far pokes reach them, their phone. */
@Entity(tableName = "connections")
data class ConnectionEntity(
    @PrimaryKey val personId: String,
    val displayName: String,
    val relationship: String,
    val status: String,
    val reachability: String,
    /** For Call and Message; null once the connection is gone. */
    val phone: String?,
    /** Epoch milliseconds since the connection is active: "3 aydır bağlısınız" (5b). Null from an older server. */
    val connectedAt: Long? = null,
    /** My "Sibel'in işleri için bana haber verilebilir" switch (5b). */
    @ColumnInfo(defaultValue = "1") val notifyMe: Boolean = true,
    /** Their profile picture's path on the server; the picture is cached per path (NUD-72). Null: the initial. */
    val avatarPath: String? = null,
)

/**
 * An invite from sync (SyncSentInvite or SyncReceivedInvite). [sent] tells which: [name] is then the name I typed for
 * them, otherwise the inviter's name. [phone] is only known for sent invites, [inviterId] only for received ones.
 */
@Entity(tableName = "invites")
data class InviteEntity(
    @PrimaryKey val id: String,
    val code: String,
    val sent: Boolean,
    val name: String,
    val phone: String?,
    val inviterId: String?,
    val prefilledName: String,
    val relationship: String,
    val status: String,
    /** Epoch milliseconds. */
    val createdAt: Long,
)

/** One step of a received plan (SyncPlan). `firedAt` is set when the alarm rang, so it is not set again. */
@Entity(tableName = "poke_steps", primaryKeys = ["taskId", "planVersion", "seq"], indices = [Index("taskId")])
data class PokeStepEntity(
    val taskId: String,
    val planVersion: Int,
    val seq: Int,
    /** Epoch milliseconds. */
    val plannedAt: Long,
    val level: String,
    val firedAt: Long? = null,
)

/** A command waiting to reach the server; the key is the command's idempotency key (ADR 0006). */
@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey val idempotencyKey: String,
    /** The command name in `POST /api/v1/commands/<name>`. */
    val command: String,
    /** The JSON body, encoded from the generated contract model. */
    val body: String,
    /** Epoch milliseconds. */
    val createdAt: Long,
    val attempts: Int = 0,
)

/** A text written offline (6a): sent as `write_task` with its key once the connection is back. */
@Entity(tableName = "queued_writes")
data class QueuedWriteEntity(
    @PrimaryKey val idempotencyKey: String,
    val text: String,
    /** Epoch milliseconds. */
    val createdAt: Long,
)

/**
 * A task event as synced (SyncEvent): the drop note and the poke count on the 5d and 7e cards, the "cevap vermiyor"
 * notification (7a) and the bell (7b) are built from these. `payload` is the event's JSON object as text.
 */
@Entity(tableName = "task_events", indices = [Index("taskId")])
data class TaskEventEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val actorId: String?,
    val type: String,
    val payload: String,
    /** Epoch milliseconds. */
    val at: Long,
)
