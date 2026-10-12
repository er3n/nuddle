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
import app.nuddle.data.session.SessionStore
import app.nuddle.domain.AlarmKey
import app.nuddle.domain.AlarmScheduler
import app.nuddle.domain.FireDecision
import app.nuddle.domain.IdGenerator
import app.nuddle.domain.PokeLevel
import app.nuddle.domain.PokeRules
import app.nuddle.domain.PokeStep
import app.nuddle.domain.QuietHours
import app.nuddle.domain.SnoozeRules
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

    fun cancel(taskId: String)
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
    ) {
        suspend fun fire(key: AlarmKey) {
            val step = alarms.step(key) ?: return
            val task = tasks.get(key.taskId)
            val now = clock.now()
            val quietHours = sessions.quietHours() ?: QuietHours.default(timeZone)
            val decision = PokeRules.decideFire(step, task?.toLocalState(), quietHours, now, fullScreen.granted())
            if (decision !is FireDecision.Ring || task == null) return
            notifications.show(task, step, decision.level)
            steps.markFired(key.taskId, key.planVersion, key.seq, now.toEpochMilliseconds())
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
                        firedAt = now.toString(),
                        level = DeviceLevel.valueOf(decision.level.name),
                        permissionMissing = decision.permissionMissing,
                    ),
                serializer = ReportFiredCommand.serializer(),
            )
        }
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
    ) : TaskCloser {
        /** Bitti on the poke: the done note on home says which poke this was (domain.md, Done note). */
        suspend fun done(key: AlarmKey) {
            val title = tasks.get(key.taskId)?.title
            val caughtAt = alarms.step(key)?.number
            done(key.taskId)
            if (title != null) doneNotes.post(DoneNote(key.taskId, title, caughtAt))
        }

        /** Also "Şimdiden bitti" on home (2f), before any alarm rang. */
        override suspend fun done(taskId: String) {
            close(taskId, TaskStatus.DONE)
            val id = ids.next()
            outbox.enqueue("done", id, DoneCommand(idempotencyKey = id, taskId = taskId), DoneCommand.serializer())
        }

        /** The sender takes the task back: "Geri al" on the undo toast, or "İptal et" in 7d with an optional note. */
        override suspend fun cancel(
            taskId: String,
            note: String?,
        ) {
            close(taskId, TaskStatus.CANCELLED)
            val id = ids.next()
            outbox.enqueue("cancel", id, CancelCommand(idempotencyKey = id, taskId = taskId, note = note), CancelCommand.serializer())
        }

        /** The optional note sheet (7f) arrives with feature slice 5; until then the drop has no note. */
        suspend fun cantToday(key: AlarmKey) {
            close(key.taskId, TaskStatus.DROPPED)
            val id = ids.next()
            outbox.enqueue(
                "cant_today",
                id,
                CantTodayCommand(idempotencyKey = id, taskId = key.taskId, note = null),
                CantTodayCommand.serializer(),
            )
        }

        /**
         * Rings the same step again after the snooze the rules allow now (15, then 10 minutes) and tells the server,
         * which counts snoozes, refuses one too many and shifts the rest of the plan (NUD-22, NUD-36). Ertele is not
         * offered when no snooze is left, so a late tap on an old notification does nothing.
         */
        suspend fun snooze(key: AlarmKey) {
            val task = tasks.get(key.taskId) ?: return
            val now = clock.now()
            val snooze = SnoozeRules.next(task.snoozesUsed, now, Instant.fromEpochMilliseconds(task.deadline)) ?: return
            notifications.cancel(key.taskId)
            val step = alarms.step(key) ?: return
            scheduler.schedule(step.copy(plannedAt = now + snooze.minutes.minutes))
            tasks.countSnooze(key.taskId)
            val id = ids.next()
            outbox.enqueue("snooze", id, SnoozeCommand(idempotencyKey = id, taskId = key.taskId), SnoozeCommand.serializer())
        }

        private suspend fun close(
            taskId: String,
            status: TaskStatus,
        ) {
            tasks.setStatus(taskId, status.value)
            alarms.stopTask(taskId)
            notifications.cancel(taskId)
        }
    }
