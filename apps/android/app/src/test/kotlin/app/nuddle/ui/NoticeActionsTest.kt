package app.nuddle.ui

import app.nuddle.ui.screens.task.DetailAction
import app.nuddle.ui.screens.task.DetailSide
import app.nuddle.ui.screens.task.DetailStatus
import app.nuddle.ui.screens.task.actionsFor
import kotlin.test.Test
import kotlin.test.assertEquals

/** A one-time message (NUD-79): "Tamam" is the only answer; a task keeps its Bugün olmaz. */
class NoticeActionsTest {
    @Test
    fun `a message to me has Tamam and nothing else`() {
        assertEquals(listOf(DetailAction.Ok), actionsFor(DetailSide.ToMe, DetailStatus.Scheduled, photo = false, notice = true))
        assertEquals(
            listOf(DetailAction.Ok, DetailAction.CancelTask),
            actionsFor(DetailSide.ToSelf, DetailStatus.Scheduled, photo = false, notice = true),
        )
    }

    @Test
    fun `a task keeps Şimdiden bitti and Bugün yapamayacağım`() {
        assertEquals(
            listOf(DetailAction.DoneEarly, DetailAction.CantToday),
            actionsFor(DetailSide.ToMe, DetailStatus.Scheduled, photo = false),
        )
        assertEquals(
            listOf(DetailAction.TakePhoto, DetailAction.CantToday),
            actionsFor(DetailSide.ToMe, DetailStatus.Scheduled, photo = true),
        )
    }

    @Test
    fun `the sender of a message sees the sender's actions as for any task`() {
        assertEquals(
            actionsFor(DetailSide.Mine, DetailStatus.Scheduled, photo = false),
            actionsFor(DetailSide.Mine, DetailStatus.Scheduled, photo = false, notice = true),
        )
    }
}
