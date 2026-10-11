package app.nuddle.domain

/** Sets and cancels one alarm per poke step (ADR 0005). The Android implementation uses `setAlarmClock`. */
interface AlarmScheduler {
    fun schedule(step: PokeStep)

    fun cancel(key: AlarmKey)
}
