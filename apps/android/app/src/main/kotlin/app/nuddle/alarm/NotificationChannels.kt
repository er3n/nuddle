package app.nuddle.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import app.nuddle.R

/** Channels from docs/ux/platform.md. A channel the user turns off counts as a missing permission. */
object NotificationChannels {
    const val POKE_SILENT = "poke_silent"
    const val POKE_ALARM = "poke_alarm"
    const val TASKS = "tasks"
    const val REACH_OUT = "reach_out"
    const val SYSTEM = "system"

    fun create(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val alarmAudio =
            AudioAttributes
                .Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        val channels =
            listOf(
                NotificationChannel(
                    POKE_SILENT,
                    context.getString(R.string.channel_poke_silent),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { setSound(null, null) },
                NotificationChannel(
                    POKE_ALARM,
                    context.getString(R.string.channel_poke_alarm),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    setSound(alarmSound, alarmAudio)
                    // USAGE_ALARM pierces Do Not Disturb by default; the bypass flag asks for it explicitly.
                    setBypassDnd(true)
                },
                NotificationChannel(
                    TASKS,
                    context.getString(R.string.channel_tasks),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
                NotificationChannel(
                    REACH_OUT,
                    context.getString(R.string.channel_reach_out),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
                NotificationChannel(
                    SYSTEM,
                    context.getString(R.string.channel_system),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { setSound(null, null) },
            )
        manager.createNotificationChannels(channels)
    }
}

/**
 * Notification ids per task, one place so a poke can take a task's earlier note away. The poke and the note about the
 * task (4b, or the photo that came) have different ids, so neither replaces the other.
 */
object NotificationIds {
    fun poke(taskId: String): Int = taskId.hashCode()

    fun note(taskId: String): Int = "arrived:$taskId".hashCode()

    /** 7a, apart from the photo note so each goes away on its own. */
    fun reach(taskId: String): Int = "reach:$taskId".hashCode()

    /**
     * The group of a task's poke. Without a group of its own, Android folds two notifications of the app into one
     * automatic group whose children are silent, and a full screen poke then never opens (found by E2E smoke 3).
     */
    fun pokeGroup(taskId: String): String = "poke:$taskId"

    /** 7a in a group of its own too, so it is not folded in silently next to a poke on the same phone. */
    fun reachGroup(taskId: String): String = "reach:$taskId"
}
