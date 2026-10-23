package app.nuddle.alarm

import app.nuddle.api.model.CancelCommand
import app.nuddle.api.model.CantTodayCommand
import app.nuddle.api.model.DeviceLevel
import app.nuddle.api.model.DoneCommand
import app.nuddle.api.model.ReportFiredCommand
import app.nuddle.api.model.SnoozeCommand
import app.nuddle.api.model.TaskStatus
import app.nuddle.data.DoneNote
import app.nuddle.data.DoneNotes
import app.nuddle.data.TaskCloser
import app.nuddle.data.db.PokeStepDao
import app.nuddle.data.db.TaskDao
import app.nuddle.data.db.TaskEntity
import app.nuddle.data.outbox.Outbox
import app.nuddle.data.session.MissedRingStore
import app.nuddle.data.session.SessionStore
import app.nuddle.domain.AlarmKey
import app.nuddle.domain.AlarmScheduler
import app.nuddle.domain.Diagnostics
import app.nuddle.domain.FireDecision
import app.nuddle.domain.IdGenerator
import app.nuddle.domain.LockedFire
import app.nuddle.domain.PokeLevel
import app.nuddle.domain.PokeRules
import app.nuddle.domain.PokeStep
import app.nuddle.domain.QuietHours
import app.nuddle.domain.SnoozeRules
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Shows and removes poke notifications (3a) and the full screen poke (3b). */
interface PokeNotifications {
    fun show(
        task: TaskEntity,
        step: PokeStep,
        level: PokeLevel,
    )

    /** "Zaten yaptın mı?" before [step] (NUD-129): quiet, Bitti only, gone when [step] rings. */
    fun showHeadsUp(
        task: TaskEntity,
        step: PokeStep,
    )

    /** Takes the task's poke and its heads-up away. */
    fun cancel(taskId: String)

    /** The tasks whose poke notification is up now. A heads-up is not a poke. */
    fun showing(): Set<String>

    /** The tasks whose heads-up is up now. */
    fun headsUps(): Set<String>
}

/** Whether the system currently lets the app open a full screen intent (Android 14+ can revoke it). */
fun interface FullScreenPermission {
    fun granted(): Boolean
}

/** An alarm fired: decide, ring, remember, report planned vs actual time (docs/domain.md, Device execution). */
class PokeFiring
    @Inject
    constructor(
        private val alarms: PokeAlarms,
        private val tasks: TaskDao,
        private val steps: PokeStepDao,
        private val sessions: SessionStore,
        private val notifications: PokeNotifications,
        private val fullScreen: FullScreenPermission,
        private val outbox: Outbox,
        private val ids: IdGenerator,
        private val clock: Clock,
        private val timeZone: TimeZone,
        private val missedRings: MissedRingStore,
        private val diagnostics: Diagnostics = Diagnostics.None,
        private val directBoot: DirectBootStore = DirectBootStore.None,
    ) {
        suspend fun fire(key: AlarmKey) {
            val step = alarms.step(key) ?: return
            val task = tasks.get(key.taskId)
            val now = clock.now()
            val quietHours = sessions.quietHours() ?: QuietHours.default(timeZone)
            val decision = PokeRules.decideFire(step, task?.toLocalState(), quietHours, now, fullScreen.granted())
            diagnostics.log("poke ${key.taskId} v${key.planVersion} #${key.seq} ${decision.breadcrumb()}")
            if (decision !is FireDecision.Ring || task == null) return
            notifications.show(task, step, decision.level)
            rang(key, now, decision.level, decision.permissionMissing)
        }

        /**
         * After the first unlock (docs/ux/platform.md, Direct boot; NUD-125): the pokes that rang before it are marked
         * fired and reported with the time they really rang, and one still up is shown again in full, with its buttons.
         */
        suspend fun takeOverLockedFires() {
            val fires = directBoot.takeFires()
            if (fires.isEmpty()) return
            diagnostics.log("direct boot handover ${fires.size}")
            val showing = notifications.showing()
            fires.forEach { takeOver(it, showing) }
        }

        private suspend fun takeOver(
            fire: LockedFire,
            showing: Set<String>,
        ) {
            val step = alarms.step(fire.key) ?: return
            val task = tasks.get(fire.key.taskId) ?: return
            if (steps.get(fire.key.taskId, fire.key.planVersion, fire.key.seq)?.firedAt != null) return
            rang(fire.key, fire.firedAt, fire.level, permissionMissing = false)
            val state = task.toLocalState()
            if (fire.key.taskId in showing && state.open && state.currentPlanVersion == fire.key.planVersion) {
                notifications.show(task, step, fire.level)
            }
        }

        /** A poke rang: remembered, so it is not set again, and reported with planned and actual time. */
        private suspend fun rang(
            key: AlarmKey,
            at: Instant,
            level: PokeLevel,
            permissionMissing: Boolean,
        ) {
            steps.markFired(key.taskId, key.planVersion, key.seq, at.toEpochMilliseconds())
            // A poke rang again: an older "Son dürtmen çalmadı" no longer applies (2g).
            missedRings.rang(at)
            // TODO(NUD-26): without a registered device (no Firebase config) the fired report cannot name the device.
            val deviceId = sessions.deviceId() ?: return
            val id = ids.next()
            outbox.enqueue(
                command = "report_fired",
                idempotencyKey = id,
                body =
                    ReportFiredCommand(
                        idempotencyKey = id,
                        taskId = key.taskId,
                        deviceId = deviceId,
                        planVersion = key.planVersion,
                        seq = key.seq,
                        firedAt = at.toString(),
                        level = DeviceLevel.valueOf(level.name),
                        permissionMissing = permissionMissing,
                    ),
                serializer = ReportFiredCommand.serializer(),
            )
        }
    }

private fun FireDecision.breadcrumb(): String =
    when (this) {
        is FireDecision.Ring -> "ring $level" + if (permissionMissing) " permission_missing" else ""
        is FireDecision.Skip -> "skip $reason"
    }

/**
 * Bitti, Ertele and Bugün olmaz from the notification or the full screen poke. Applied locally first (alarms stop
 * at once, even offline), then sent through the outbox (docs/ux/platform.md, Alarms and pokes).
 */
class PokeActions
    @Inject
    constructor(
        private val tasks: TaskDao,
        private val alarms: PokeAlarms,
        private val scheduler: AlarmScheduler,
        private val notifications: PokeNotifications,
        private val outbox: Outbox,
        private val ids: IdGenerator,
        private val clock: Clock,
        private val doneNotes: DoneNotes,
        private val diagnostics: Diagnostics = Diagnostics.None,
    ) : TaskCloser {
        /** Bitti on the poke: the done note on home says which poke this was (domain.md, Done note). */
        suspend fun done(key: AlarmKey) {
            val title = tasks.get(key.taskId)?.title
            val caughtAt = alarms.step(key)?.number
            done(key.taskId, cameraUnavailable = false)
            if (title != null) doneNotes.post(DoneNote(key.taskId, title, caughtAt))
        }

        /**
         * Bitti on "Zaten yaptın mı?" (NUD-129): the same Done as on a poke, which stops the alarms still to come and
         * tells the server. The done note names no poke: the heads-up is not one.
         */
        suspend fun doneFromHeadsUp(key: AlarmKey) {
            val title = tasks.get(key.taskId)?.title
            diagnostics.log("heads_up done ${key.taskId}")
            done(key.taskId, cameraUnavailable = false)
            if (title != null) doneNotes.post(DoneNote(key.taskId, title, caughtAt = null))
        }

        /** Also "Şimdiden bitti" on home (2f), before any alarm rang. */
        override suspend fun done(
            taskId: String,
            cameraUnavailable: Boolean,
        ) = whole {
            diagnostics.log("done $taskId")
            close(taskId, TaskStatus.DONE)
            val id = ids.next()
            // A photo task without a usable camera falls back to plain Bitti (docs/domain.md, Task states). doneAt lets a
            // Bitti tapped offline before the deadline count when it reaches the server after it (6a).
            val command =
                DoneCommand(
                    idempotencyKey = id,
                    taskId = taskId,
                    cameraUnavailable = cameraUnavailable.takeIf { it },
                    doneAt = clock.now().toString(),
                )
            outbox.enqueue("done", id, command, DoneCommand.serializer())
        }

        override suspend fun photoTaken(taskId: String) =
            whole {
                val task = tasks.get(taskId) ?: return@whole
                diagnostics.log("photo taken $taskId")
                close(taskId, if (task.senderId == task.recipientId) TaskStatus.DONE else TaskStatus.AWAITING_APPROVAL)
            }

        /** The sender takes the task back: "Geri al" on the undo toast, or "İptal et" in 7d with an optional note. */
        override suspend fun cancel(
            taskId: String,
            note: String?,
        ) = whole {
            diagnostics.log("cancel $taskId")
            close(taskId, TaskStatus.CANCELLED)
            val id = ids.next()
            outbox.enqueue("cancel", id, CancelCommand(idempotencyKey = id, taskId = taskId, note = note), CancelCommand.serializer())
        }

        /** "Bugün olmaz" on the notification closes without a note; the full screen poke may add one (7f). */
        suspend fun cantToday(
            key: AlarmKey,
            note: String? = null,
        ) = cantToday(key.taskId, note)

        /** Also "Bugün yapamayacağım" in the task detail. */
        override suspend fun cantToday(
            taskId: String,
            note: String?,
        ) = whole {
            diagnostics.log("cant_today $taskId")
            close(taskId, TaskStatus.DROPPED)
            val id = ids.next()
            outbox.enqueue(
                "cant_today",
                id,
                CantTodayCommand(idempotencyKey = id, taskId = taskId, note = note?.trim()?.takeIf { it.isNotEmpty() }),
                CantTodayCommand.serializer(),
            )
        }

        /**
         * Rings the same step again after the snooze the rules allow now (15, then 10 minutes) and tells the server,
         * which counts snoozes, refuses one too many and shifts the rest of the plan (NUD-22, NUD-36). Ertele is not
         * offered when no snooze is left, so a late tap on an old notification does nothing.
         */
        suspend fun snooze(key: AlarmKey) =
            whole {
                val task = tasks.get(key.taskId) ?: return@whole
                val now = clock.now()
                val snooze = SnoozeRules.next(task.snoozesUsed, now, Instant.fromEpochMilliseconds(task.deadline)) ?: return@whole
                diagnostics.log("snooze ${key.taskId}")
                notifications.cancel(key.taskId)
                val step = alarms.step(key) ?: return@whole
                scheduler.schedule(step.copy(plannedAt = snooze.until))
                tasks.countSnooze(key.taskId, snooze.until.toEpochMilliseconds())
                alarms.remember()
                val id = ids.next()
                outbox.enqueue("snooze", id, SnoozeCommand(idempotencyKey = id, taskId = key.taskId), SnoozeCommand.serializer())
            }

        /**
         * Ara or Mesaj at on the full screen poke (NUD-128): this poke stops ringing and speaking, and nothing else
         * changes. The task stays open, the rest of the plan rings as planned and the server is not told.
         */
        fun contact(key: AlarmKey) {
            diagnostics.log("contact ${key.taskId}")
            notifications.cancel(key.taskId)
        }

        /**
         * Once started, an answer closes the task here and queues its command as one step (NUD-143): the screen that
         * started it may close at once (the task detail goes home), and a task closed on this phone but never sent stays
         * open on the server.
         */
        private suspend fun <T> whole(answer: suspend () -> T): T = withContext(NonCancellable) { answer() }

        private suspend fun close(
            taskId: String,
            status: TaskStatus,
        ) {
            tasks.setStatus(taskId, status.value)
            alarms.stopTask(taskId)
            notifications.cancel(taskId)
        }
    }
