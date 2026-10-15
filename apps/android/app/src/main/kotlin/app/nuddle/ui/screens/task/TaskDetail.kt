package app.nuddle.ui.screens.task

import app.nuddle.api.model.TaskStatus
import app.nuddle.data.db.PokeStepEntity
import app.nuddle.data.db.TaskEntity
import kotlin.time.Instant

/** Whose task it is from where I stand (docs/ux/screens.md, Home tabs and task detail). */
enum class DetailSide { ToMe, ToSelf, Mine }

/** The states the detail shows; anything else (done, cancelled, approved) closes it. */
enum class DetailStatus { Scheduled, ReRequested, AwaitingApproval, Missed, Dropped }

private val TO_ME_STATUSES =
    mapOf(
        TaskStatus.SCHEDULED.value to DetailStatus.Scheduled,
        TaskStatus.REJECTED.value to DetailStatus.ReRequested,
        TaskStatus.AWAITING_APPROVAL.value to DetailStatus.AwaitingApproval,
        TaskStatus.MISSED.value to DetailStatus.Missed,
    )

/** A photo waiting for me opens the approval (4f) instead, so it is not here. */
private val MINE_STATUSES =
    mapOf(
        TaskStatus.SCHEDULED.value to DetailStatus.Scheduled,
        TaskStatus.REJECTED.value to DetailStatus.ReRequested,
        TaskStatus.DROPPED.value to DetailStatus.Dropped,
        TaskStatus.MISSED.value to DetailStatus.Missed,
    )

/** My side of [task] and the state the detail shows, or null when the detail has nothing to show for it. */
fun detailOf(
    task: TaskEntity,
    me: String,
): Pair<DetailSide, DetailStatus>? {
    val side =
        when {
            task.senderId == me && task.recipientId == me -> DetailSide.ToSelf
            task.recipientId == me -> DetailSide.ToMe
            task.senderId == me -> DetailSide.Mine
            else -> return null
        }
    // A missed task I took off my list is gone for me (NUD-49).
    if (side != DetailSide.Mine && !task.inRecipientList) return null
    val status = (if (side == DetailSide.Mine) MINE_STATUSES else TO_ME_STATUSES)[task.status] ?: return null
    return side to status
}

/** What the bottom of the detail offers (docs/ux/screens.md, Task detail). */
enum class DetailAction {
    DoneEarly,
    TakePhoto,

    /** "Tamam" on a one-time message (NUD-79): the done command, the only answer. */
    Ok,
    NewPhoto,
    CantToday,
    DidItLater,
    RemoveMissed,
    CancelTask,
    NewTime,
    SenderHandles,
    Extend,
    CancelNow,
}

/** The actions for my side and the state, primary first; Ara and Mesaj are not here, they sit in the content. */
fun actionsFor(
    side: DetailSide,
    status: DetailStatus,
    photo: Boolean,
    notice: Boolean = false,
): List<DetailAction> {
    val done =
        when {
            notice -> DetailAction.Ok
            photo -> DetailAction.TakePhoto
            else -> DetailAction.DoneEarly
        }
    val actions =
        when (side) {
            DetailSide.ToMe -> toMeActions(status, done)
            DetailSide.ToSelf -> toSelfActions(status, done)
            DetailSide.Mine -> mineActions(status)
        }
    // A one-time message is not postponed (NUD-79).
    return if (notice) actions.filterNot { it == DetailAction.CantToday } else actions
}

private fun toMeActions(
    status: DetailStatus,
    done: DetailAction,
): List<DetailAction> =
    when (status) {
        DetailStatus.Scheduled -> listOf(done, DetailAction.CantToday)
        DetailStatus.ReRequested -> listOf(DetailAction.NewPhoto, DetailAction.CantToday)
        DetailStatus.Missed -> listOf(DetailAction.DidItLater, DetailAction.RemoveMissed)
        DetailStatus.AwaitingApproval, DetailStatus.Dropped -> emptyList()
    }

private fun toSelfActions(
    status: DetailStatus,
    done: DetailAction,
): List<DetailAction> =
    when (status) {
        DetailStatus.Scheduled -> listOf(done, DetailAction.CancelTask)
        DetailStatus.Missed -> listOf(DetailAction.DidItLater, DetailAction.RemoveMissed)
        DetailStatus.ReRequested, DetailStatus.AwaitingApproval, DetailStatus.Dropped -> emptyList()
    }

private fun mineActions(status: DetailStatus): List<DetailAction> =
    when (status) {
        DetailStatus.Scheduled, DetailStatus.ReRequested -> listOf(DetailAction.CancelTask)
        DetailStatus.Dropped -> listOf(DetailAction.NewTime, DetailAction.SenderHandles)
        DetailStatus.Missed -> listOf(DetailAction.Extend, DetailAction.CancelNow)
        DetailStatus.AwaitingApproval -> emptyList()
    }

/** Son zaman and Nasıl bitsin can be changed while my task to someone, or to self, is still going (7c, NUD-47). */
fun editable(
    side: DetailSide,
    status: DetailStatus,
): Boolean = side != DetailSide.ToMe && status in setOf(DetailStatus.Scheduled, DetailStatus.ReRequested)

/**
 * When poking starts on a task to me (NUD-68, "09:00'da hatırlatacağım"): the first step of the current plan, while none
 * of its steps has rung and it is still ahead. Null otherwise; the rest of the ladder stays hidden.
 */
fun pokeStart(
    steps: List<PokeStepEntity>,
    planVersion: Int,
    now: Instant,
): Instant? {
    val plan = steps.filter { it.planVersion == planVersion }
    if (plan.isEmpty() || plan.any { it.firedAt != null }) return null
    return Instant.fromEpochMilliseconds(plan.minOf { it.plannedAt }).takeIf { it > now }
}

/** Kurulu or Tekrar istendi: the pokes are still going. */
val DetailStatus.open: Boolean get() = this == DetailStatus.Scheduled || this == DetailStatus.ReRequested

/** My task to someone, after the first poke rang (NUD-81): how many so far and when the last one was. */
data class PokesSoFar(
    val count: Int,
    val lastAt: String,
)
