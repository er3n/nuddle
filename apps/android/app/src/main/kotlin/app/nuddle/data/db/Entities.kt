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
