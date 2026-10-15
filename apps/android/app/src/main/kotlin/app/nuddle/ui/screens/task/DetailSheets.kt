package app.nuddle.ui.screens.task

import app.nuddle.data.DoneNote
import app.nuddle.data.DoneNotes
import app.nuddle.data.TaskCloser
import app.nuddle.data.TaskRepository
import app.nuddle.data.WriteOutcome
import app.nuddle.data.session.TaskAcks
import app.nuddle.domain.IdGenerator
import app.nuddle.ui.screens.home.DeadlineChoice
import app.nuddle.ui.screens.home.HOUR_MINUTE
import app.nuddle.ui.screens.home.NewDeadlineUi
import app.nuddle.ui.screens.home.nextOccurrence
import app.nuddle.ui.screens.home.quickDeadlines
import app.nuddle.ui.screens.poke.DropReason
import app.nuddle.ui.screens.poke.DropUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlin.time.Clock
import kotlin.time.Instant

// The parts of the task detail that answer a task in one tap or through a sheet (docs/ux/screens.md, Task detail).
// Kept out of TaskDetailViewModel so each stays small; the route binds their functions as callbacks.

/** The one-tap answers. Each is applied on this phone at once and goes through the outbox (ADR 0006). */
class TaskAnswers(
    private val taskId: String,
    private val tasks: TaskRepository,
    private val closer: TaskCloser,
    private val doneNotes: DoneNotes,
    private val scope: CoroutineScope,
) {
    /** Şimdiden bitti; home shows the done note once the detail closes (domain.md, Done note). */
    fun onDone(title: String) {
        scope.launch { doneNotes.post(DoneNote(taskId, title, tasks.done(taskId))) }
    }

    fun onDidItLater() {
        scope.launch { tasks.didItLater(taskId) }
    }

    fun onRemoveMissed() {
        scope.launch { tasks.removeMissed(taskId) }
    }

    fun onSenderHandles() {
        scope.launch { tasks.senderHandles(taskId) }
    }

    /** İptal et on my missed task: no 7d sheet (open-questions#36). */
    fun onCancelNow() {
        scope.launch { tasks.cancel(taskId) }
    }

    /** Bugün yapamayacağım: closed as dropped with the note, if any (7f). */
    fun onCantToday(note: String?) {
        scope.launch { closer.cantToday(taskId, note) }
    }
}

/** 7f over the detail: who the note is for, the chosen chip and what is written. */
class DropSheetHolder {
    private val sheet = MutableStateFlow<DropUi?>(null)
    val open: StateFlow<DropUi?> = sheet.asStateFlow()

    fun onOpen(senderName: String) {
        sheet.value = DropUi(senderName)
    }

    /** A second tap on the chosen chip clears it (ChoiceChip, docs/ux/components.md 5). */
    fun onReason(reason: DropReason) {
        sheet.update { it?.copy(reason = reason.takeIf { r -> r != it.reason }) }
    }

    fun onNoteChange(note: String) {
        sheet.update { it?.copy(note = note) }
    }

    fun onDismiss() {
        sheet.value = null
    }
}

/** Yeni zaman ver or Süreyi uzat (5d): the 2b deadline picks in a sheet over the detail. */
class NewDeadlineHolder(
    private val taskId: String,
    private val tasks: TaskRepository,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val timeZone: TimeZone,
    private val scope: CoroutineScope,
) {
    private val sheet = MutableStateFlow<NewDeadlineUi?>(null)
    val open: StateFlow<NewDeadlineUi?> = sheet.asStateFlow()

    /** Kept while the same new deadline is retried, so it is not applied twice. */
    private var saveKey: String? = null

    fun onOpen(toName: String) {
        saveKey = null
        sheet.value = NewDeadlineUi(taskId, toName, quickDeadlines(clock.now(), timeZone))
    }

    fun onChoice(choice: DeadlineChoice) {
        saveKey = null
        sheet.update { it?.copy(picked = if (it.picked == choice) null else choice, failed = false) }
    }

    fun onTimePicked(time: LocalTime) {
        saveKey = null
        val picked = DeadlineChoice.Picked(nextOccurrence(time, clock.now(), timeZone), time.format(HOUR_MINUTE))
        sheet.update { it?.copy(picked = picked, failed = false) }
    }

    fun onDismiss() {
        sheet.value = null
    }

    fun onSave() {
        val current = sheet.value ?: return
        val deadline = current.picked?.at ?: return
        if (current.saving) return
        val key = saveKey ?: ids.next().also { saveKey = it }
        sheet.value = current.copy(saving = true, failed = false)
        scope.launch {
            val ok = tasks.newDeadline(taskId, deadline, key) is WriteOutcome.Created
            if (ok) saveKey = null
            sheet.update { if (ok) null else it?.copy(saving = false, failed = true) }
        }
    }
}

/**
 * "Saat 09:00 olunca hatırlatırım" tapped (NUD-78): the recipient picks when the pokes begin, between now and the
 * task's time. The same sheet as a new deadline, without the quick choices (they are deadlines, not starts).
 */
class StartHolder(
    private val taskId: String,
    private val tasks: TaskRepository,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val timeZone: TimeZone,
    private val scope: CoroutineScope,
) {
    private val sheet = MutableStateFlow<NewDeadlineUi?>(null)
    val open: StateFlow<NewDeadlineUi?> = sheet.asStateFlow()

    /** Kept while the same start is retried, so it is not applied twice. */
    private var saveKey: String? = null

    /** [taskTime] is the task's own time: the moment of an `at` task, else the deadline. */
    fun onOpen(taskTime: Instant) {
        saveKey = null
        sheet.value = NewDeadlineUi(taskId, toName = "", choices = emptyList(), limit = taskTime)
    }

    fun onTimePicked(time: LocalTime) {
        saveKey = null
        val picked = DeadlineChoice.Picked(nextOccurrence(time, clock.now(), timeZone), time.format(HOUR_MINUTE))
        sheet.update { it?.copy(picked = picked, failed = false) }
    }

    fun onDismiss() {
        sheet.value = null
    }

    fun onSave() {
        val current = sheet.value ?: return
        val startAt = current.picked?.at ?: return
        if (current.saving || current.pickedTooLate) return
        val key = saveKey ?: ids.next().also { saveKey = it }
        sheet.value = current.copy(saving = true, failed = false)
        scope.launch {
            val ok = tasks.setStart(taskId, startAt, key) is WriteOutcome.Created
            if (ok) saveKey = null
            sheet.update { if (ok) null else it?.copy(saving = false, failed = true) }
        }
    }
}

/** Tells the server once that the task was on screen here (NUD-68). */
class SeenOnce(
    private val taskId: String,
    private val acks: TaskAcks,
    private val scope: CoroutineScope,
) {
    private var told = false

    fun tell() {
        if (told) return
        told = true
        scope.launch { acks.seen(listOf(taskId)) }
    }
}
