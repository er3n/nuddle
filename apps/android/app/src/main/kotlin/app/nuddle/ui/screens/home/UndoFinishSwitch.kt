package app.nuddle.ui.screens.home

import app.nuddle.api.model.FinishMode
import app.nuddle.data.FinishSwitcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * The finish switch on the undo toast (4i, NUD-137). One tap on the other option sends one `edit` through the outbox
 * and keeps the toast, whose window starts again; a tap on the current choice sends nothing. A one-time message has no
 * switch (NUD-79).
 */
internal class UndoFinishSwitch(
    private val undo: MutableStateFlow<UndoUi?>,
    private val switcher: FinishSwitcher,
    private val scope: CoroutineScope,
) {
    fun onPhoto(photo: Boolean) {
        val shown = undo.value ?: return
        if (shown.notice || shown.photo == photo) return
        undo.value = shown.copy(photo = photo)
        scope.launch { switcher.switch(shown.taskId, if (photo) FinishMode.PHOTO else FinishMode.DONE) }
    }
}
