package app.nuddle.ui.screens.home

import app.nuddle.api.model.FinishMode
import app.nuddle.api.model.Reachability
import app.nuddle.ui.screens.hourMinute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlin.time.Clock
import kotlin.time.Instant

/** The preview on screen plus what is needed to create the task from it, or to edit [editing]. */
internal data class OpenPreview(
    val ui: PreviewUi,
    /** For a task to self; for someone else the sheet's own choice ([PreviewUi.photo]) wins. */
    val finishMode: FinishMode,
    val key: String,
    val editing: String? = null,
    /** The deadline the text gave when only "Kime?" was asked. */
    val knownDeadline: Instant? = null,
    /** How far pokes reach each connection, for the note under 4a once a recipient is chosen. */
    val reaches: Map<String, Reachability> = emptyMap(),
) {
    /** What Kur or Gönder creates: the finish mode the sheet shows for someone else, the draft's for self. */
    val chosenFinishMode: FinishMode
        get() =
            when {
                ui.notice -> FinishMode.NOTICE
                ui.toOther -> if (ui.photo) FinishMode.PHOTO else FinishMode.DONE
                else -> finishMode
            }

    val deadline: Instant? get() = knownDeadline ?: ui.selected?.at
}

/** The choices on the preview sheet (2b, 4a), kept apart from HomeViewModel's sending and setup. */
internal class PreviewHolder(
    private val clock: Clock,
    private val timeZone: TimeZone,
) {
    val open = MutableStateFlow<OpenPreview?>(null)

    /** A second tap on the selected choice clears it (ChoiceChip, docs/ux/components.md 5). */
    fun onChoice(choice: DeadlineChoice) {
        open.update { it?.copy(ui = it.ui.copy(selected = if (it.ui.selected == choice) null else choice)) }
    }

    fun onTimePicked(time: LocalTime) {
        val picked = DeadlineChoice.Picked(nextOccurrence(time, clock.now(), timeZone), time.format(HOUR_MINUTE))
        open.update { it?.copy(ui = it.ui.copy(selected = picked)) }
    }

    /** "Kime?" answered (4a): the name, the reach note and the finish choice follow the person. */
    fun onRecipient(choice: RecipientChoice) {
        open.update { current ->
            current?.copy(
                ui =
                    current.ui.copy(
                        recipient = choice,
                        to = (choice as? RecipientChoice.Person)?.name,
                        reach = (choice as? RecipientChoice.Person)?.let { current.reaches[it.personId] ?: Reachability.NONE },
                    ),
            )
        }
    }

    /** A tap on the Kime row (NUD-108): the "Kime?" choices of 4a open with the current one selected. */
    fun onChangeRecipient() {
        open.update { it?.copy(ui = it.ui.copy(recipients = it.ui.recipientOptions)) }
    }

    /** A tap on the known time switches between a moment and a deadline (open-questions#43). */
    fun onToggleMoment() {
        open.update { current ->
            val known = current?.ui?.knownDeadline ?: return@update current
            current.copy(ui = current.ui.copy(knownDeadline = known.asMoment(!known.moment)))
        }
    }

    /** "06:00 olsun" (false) or "Yine de 05:00" (true), NUD-120; a second tap clears it. The time row follows. */
    fun onQuiet(keep: Boolean) {
        open.update { current ->
            val quiet = current?.ui?.quiet ?: return@update current
            val picked = quiet.copy(keep = if (quiet.keep == keep) null else keep)
            val at = picked.at
            current.copy(
                knownDeadline = at,
                ui =
                    current.ui.copy(
                        quiet = picked,
                        knownDeadline = deadlineText(at, clock.now(), timeZone, moment = true),
                        firstPokeAt = picked.firstPokeAt,
                        repeat = current.ui.repeat?.copy(time = at.hourMinute(timeZone)),
                    ),
            )
        }
    }

    fun onPhoto(photo: Boolean) {
        open.update { it?.copy(ui = it.ui.copy(photo = photo)) }
    }

    fun onDismiss() {
        open.value = null
    }
}
