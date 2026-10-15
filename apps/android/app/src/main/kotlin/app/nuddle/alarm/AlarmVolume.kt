package app.nuddle.alarm

import android.content.Context
import android.media.AudioManager
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * A ringing poke is never too quiet to hear (NUD-68, docs/ux/platform.md, Alarm volume): the alarm stream is raised to
 * at least [FLOOR] of its range while a poke rings, and put back to what the person had once no poke is left. The level
 * before is kept in preferences, so a restart in between still puts it back.
 */
class AlarmVolume
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        private val audio get() = context.getSystemService(AudioManager::class.java)
        private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun raise() {
            val audio = audio ?: return
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val floor = (max * FLOOR).toInt().coerceAtLeast(1)
            val now = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            if (now >= floor) return
            // The first raise keeps the person's own level; a second poke must not save the raised one.
            if (!prefs.contains(KEY_BEFORE)) prefs.edit { putInt(KEY_BEFORE, now) }
            setVolume(audio, floor)
        }

        fun restore() {
            if (!prefs.contains(KEY_BEFORE)) return
            val before = prefs.getInt(KEY_BEFORE, 0)
            prefs.edit { remove(KEY_BEFORE) }
            audio?.let { setVolume(it, before) }
        }

        private fun setVolume(
            audio: AudioManager,
            level: Int,
        ) {
            // Some phones refuse volume changes in Do Not Disturb; the poke then rings at the person's own level.
            runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, level, 0) }
        }

        private companion object {
            const val PREFS = "alarm_volume"
            const val KEY_BEFORE = "before"
            const val FLOOR = 0.7
        }
    }
