package app.nuddle.alarm

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import app.nuddle.data.LocalWipe
import app.nuddle.data.db.NuddleDatabase
import app.nuddle.data.outbox.WorkManagerScheduling
import app.nuddle.data.photo.PhotoFiles
import app.nuddle.data.session.SessionStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * After Çıkış yap (7h) or Hesabımı sil (7i): alarms first, so nothing rings while the rest goes, then shown
 * notifications, queued work, the database, unsent photos and the session (which also clears the setup and consent
 * marks kept with it).
 */
class AndroidLocalWipe
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val alarms: PokeAlarms,
        private val work: WorkManagerScheduling,
        private val database: NuddleDatabase,
        private val photos: PhotoFiles,
        private val sessions: SessionStore,
    ) : LocalWipe {
        override suspend fun wipe() {
            alarms.stopAll()
            NotificationManagerCompat.from(context).cancelAll()
            work.cancelAll()
            withContext(Dispatchers.IO) {
                database.clearAllTables()
                photos.deleteAll()
            }
            sessions.clear()
        }
    }
