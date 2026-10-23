package app.nuddle.alarm

import app.nuddle.domain.AlarmKey
import app.nuddle.domain.Diagnostics
import javax.inject.Inject

/**
 * A ringing poke goes quiet without an answer (unlike [PokeActions]): the task stays open, the rest of the plan rings as
 * planned and the server is not told.
 */
class QuietPokes
    @Inject
    constructor(
        private val notifications: PokeNotifications,
        private val diagnostics: Diagnostics = Diagnostics.None,
    ) {
        /**
         * The poke was swiped away (NUD-154). Its voice stops now, and with no poke left the alarm volume goes back to the
         * person's level.
         */
        fun dismissed(key: AlarmKey) {
            diagnostics.log("dismissed ${key.taskId}")
            notifications.cancel(key.taskId)
        }
    }
