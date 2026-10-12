package app.nuddle.testing

import app.nuddle.data.db.ConnectionEntity
import app.nuddle.data.db.PokeStepEntity
import app.nuddle.data.db.TaskEntity
import app.nuddle.data.session.StoredSession
import app.nuddle.domain.PokeLevel
import kotlin.time.Instant

/** The cast from docs/glossary.md: Selin writes, Eren gets poked. */
object Cast {
    const val SELIN_ID = "0b7a3b1e-0000-4000-8000-000000000001"
    const val EREN_ID = "0b7a3b1e-0000-4000-8000-000000000002"
    const val SELIN_PHONE = "+905320000001"
    const val EREN_PHONE = "+905320000002"
    const val TASK_ID = "7c3f6a10-0000-4000-8000-0000000000aa"
}

fun aSession(registered: Boolean = true) =
    StoredSession(
        accessToken = "access-1",
        refreshToken = "refresh-1",
        expiresAt = Instant.parse("2026-10-12T09:00:00+03:00"),
        personId = Cast.EREN_ID,
        registered = registered,
    )

/** "Temizlikçi için para çek · 1.000 TL · yarın 09:00" from Selin to Eren. */
fun aTask(
    id: String = Cast.TASK_ID,
    status: String = "SCHEDULED",
    planVersion: Int = 1,
    deadline: Instant = Instant.parse("2026-10-12T09:00:00+03:00"),
) = TaskEntity(
    id = id,
    senderId = Cast.SELIN_ID,
    recipientId = Cast.EREN_ID,
    title = "Temizlikçi için para çek",
    detail = "1.000 TL",
    deadline = deadline.toEpochMilliseconds(),
    finishMode = "photo",
    status = status,
    planVersion = planVersion,
    inRecipientList = true,
)

fun aStep(
    seq: Int,
    at: String,
    level: PokeLevel = PokeLevel.SOUND,
    planVersion: Int = 1,
    taskId: String = Cast.TASK_ID,
    firedAt: Long? = null,
) = PokeStepEntity(
    taskId = taskId,
    planVersion = planVersion,
    seq = seq,
    plannedAt = Instant.parse(at).toEpochMilliseconds(),
    level = level.name,
    firedAt = firedAt,
)

fun selinConnection(phone: String? = Cast.SELIN_PHONE) =
    ConnectionEntity(
        personId = Cast.SELIN_ID,
        displayName = "Selin",
        relationship = "spouse",
        status = "active",
        reachability = "FULL_SCREEN",
        phone = phone,
    )
