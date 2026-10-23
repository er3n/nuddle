package app.nuddle.alarm

import app.nuddle.data.db.TaskDao
import app.nuddle.domain.AlarmKey
import app.nuddle.domain.Diagnostics
import app.nuddle.domain.PokeLevel
import javax.inject.Inject

/**
 * A ringing poke goes quiet without an answer (unlike [PokeActions]): the task stays open, the rest of the plan rings as
 * planned and the server is not told.
 */
class QuietPokes
    @Inject
    constructor(
        private val tasks: TaskDao,
        private val alarms: PokeAlarms,
        private val notifications: PokeNotifications,
        private val diagnostics: Diagnostics = Diagnostics.None,
        private val quietHours: QuietHoursSource = QuietHoursSource.None,
    ) {
        /**
         * The poke was swiped away (NUD-154). Its voice stops now, and with no poke left the alarm volume goes back to the
         * person's level.
         */
        fun dismissed(key: AlarmKey) {
            diagnostics.log("dismissed ${key.taskId}")
            notifications.cancel(key.taskId)
        }

        /** A volume key on the full screen poke (NUD-155): it goes quiet and stays with its answers. */
        suspend fun silence(key: AlarmKey) {
            val task = tasks.get(key.taskId) ?: return
            if (task.id !in notifications.showing()) return
            diagnostics.log("silence ${key.taskId}")
            notifications.silence(task, alarms.fullScreenStep(key), PokeLevel.FULL_SCREEN, quietHours.quietHours())
        }
    }
