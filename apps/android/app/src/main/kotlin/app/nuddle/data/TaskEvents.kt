package app.nuddle.data

import app.nuddle.data.db.TaskEventEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlin.time.Instant

// Reading the payload of a synced task event (docs/domain.md, Task events). Unknown or missing fields read as absent.

private fun TaskEventEntity.field(name: String): JsonPrimitive? =
    runCatching { Json.parseToJsonElement(payload).jsonObject[name] as? JsonPrimitive }.getOrNull()

/** The optional note of `dropped`, `cancelled` and `re_requested`. */
val TaskEventEntity.note: String? get() = field("note")?.contentOrNull?.takeIf { it.isNotBlank() }

/** Why a task was cancelled: `sender_cancelled`, `sender_handles` or `account_deleted`. */
val TaskEventEntity.reason: String? get() = field("reason")?.contentOrNull

/** `approved` by the 3 hour timer rather than by the sender. */
val TaskEventEntity.auto: Boolean get() = field("auto")?.booleanOrNull == true

/** `done` through "Sonradan yaptım" after the task was missed. */
val TaskEventEntity.late: Boolean get() = field("late")?.booleanOrNull == true

/** `done` on a photo task without a usable camera. */
val TaskEventEntity.cameraUnavailable: Boolean get() = field("cameraUnavailable")?.booleanOrNull == true

/** When the poke of a `poke_lost` event should have rung. */
val TaskEventEntity.plannedAt: Instant?
    get() = field("plannedAt")?.contentOrNull?.let { runCatching { Instant.parse(it) }.getOrNull() }

/** The level of a `poke_fired` event. */
val TaskEventEntity.level: String? get() = field("level")?.contentOrNull

/** Event types the app reads (TaskEventType values). */
object EventTypes {
    const val CREATED = "created"
    const val EDITED = "edited"
    const val DONE = "done"
    const val APPROVED = "approved"
    const val DROPPED = "dropped"
    const val CANCELLED = "cancelled"
    const val POKE_FIRED = "poke_fired"
    const val POKE_LOST = "poke_lost"
    const val HUMAN_NOTIFIED = "human_notified"
    const val DELIVERED = "delivered"
    const val SEEN = "seen"
}

/** How far my task to someone got: sent, on their phone, or seen by them (NUD-68, the sender's tick). */
enum class Delivery { Sent, Reached, Seen }

/** The tick from a task's events. */
fun List<TaskEventEntity>.delivery(): Delivery =
    when {
        any { it.type == EventTypes.SEEN } -> Delivery.Seen
        any { it.type == EventTypes.DELIVERED } -> Delivery.Reached
        else -> Delivery.Sent
    }

/** Events since the task last got a deadline (created, or a new time): the pokes of this round. Oldest first. */
fun List<TaskEventEntity>.sinceLastDeadline(): List<TaskEventEntity> {
    val start = indexOfLast { it.type == EventTypes.EDITED || it.type == EventTypes.CREATED }
    return if (start < 0) this else drop(start)
}
