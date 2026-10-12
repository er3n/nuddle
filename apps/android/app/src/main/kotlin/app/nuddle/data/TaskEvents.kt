package app.nuddle.data

import app.nuddle.data.db.TaskEventEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

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
    const val HUMAN_NOTIFIED = "human_notified"
}
