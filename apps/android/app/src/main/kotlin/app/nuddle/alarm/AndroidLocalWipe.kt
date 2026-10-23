package app.nuddle.alarm

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import app.nuddle.data.LocalWipe
import app.nuddle.data.avatar.AvatarFiles
import app.nuddle.data.db.NuddleDatabase
import app.nuddle.data.outbox.WorkManagerScheduling
import app.nuddle.data.photo.PhotoFiles
import app.nuddle.data.session.SessionStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * After Çıkış yap (7h), Hesabımı sil (7i) or a refresh the server rejected (NUD-141): alarms first, so nothing rings
 * while the rest goes (stopping them also empties the direct boot copy), then pokes that are up with their speech, the
 * alarm volume they raised, the other notifications, queued work, the database (an open poke screen closes when its
 * task goes), unsent photos and the session (which also clears the device id, the sync cursor and the setup and
 * consent marks kept with it).
 */
class AndroidLocalWipe
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val alarms: PokeAlarms,
        private val work: WorkManagerScheduling,
        private val database: NuddleDatabase,
        private val photos: PhotoFiles,
        private val avatars: AvatarFiles,
        private val sessions: SessionStore,
        private val pokes: PokeNotifications,
        private val volume: AlarmVolume,
    ) : LocalWipe {
        override suspend fun wipe() {
            alarms.stopAll()
            pokes.showing().forEach(pokes::cancel)
            volume.restore()
            NotificationManagerCompat.from(context).cancelAll()
            work.cancelAll()
            withContext(Dispatchers.IO) {
                database.clearAllTables()
                photos.deleteAll()
                avatars.deleteAll()
            }
            sessions.clear()
        }
    }
