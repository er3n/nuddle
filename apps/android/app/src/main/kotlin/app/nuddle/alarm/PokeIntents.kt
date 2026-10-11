package app.nuddle.alarm

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import app.nuddle.domain.AlarmKey

/** Intents between the alarm, the notification actions and [PokeActivity]. Keys travel as extras. */
object PokeIntents {
    const val ACTION_FIRE = "app.nuddle.action.POKE_FIRE"
    const val ACTION_DONE = "app.nuddle.action.POKE_DONE"
    const val ACTION_SNOOZE = "app.nuddle.action.POKE_SNOOZE"
    const val ACTION_CANT_TODAY = "app.nuddle.action.POKE_CANT_TODAY"
    private const val EXTRA_TASK = "taskId"
    private const val EXTRA_VERSION = "planVersion"
    private const val EXTRA_SEQ = "seq"

    fun fire(
        context: Context,
        key: AlarmKey,
    ): Intent = Intent(context, PokeAlarmReceiver::class.java).setAction(ACTION_FIRE).withKey(key)

    fun action(
        context: Context,
        action: String,
        key: AlarmKey,
    ): Intent = Intent(context, PokeActionReceiver::class.java).setAction(action).withKey(key)

    fun fullScreen(
        context: Context,
        key: AlarmKey,
    ): Intent =
        Intent(context, PokeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            .withKey(key)

    fun key(intent: Intent): AlarmKey? {
        val task = intent.getStringExtra(EXTRA_TASK) ?: return null
        return AlarmKey(task, intent.getIntExtra(EXTRA_VERSION, -1), intent.getIntExtra(EXTRA_SEQ, -1))
    }

    // A distinct data URI keeps PendingIntents of different steps apart.
    private fun Intent.withKey(key: AlarmKey): Intent =
        setData("nuddle://poke/${key.taskId}/${key.planVersion}/${key.seq}".toUri())
            .putExtra(EXTRA_TASK, key.taskId)
            .putExtra(EXTRA_VERSION, key.planVersion)
            .putExtra(EXTRA_SEQ, key.seq)
}
