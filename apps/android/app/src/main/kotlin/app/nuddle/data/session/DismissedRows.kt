package app.nuddle.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Tasks whose "iptal etti" row on home was closed with its ✕ (NUD-67). Only this phone forgets the row; the task
 * stays cancelled and the row would go by itself after a day anyway (open-questions#36).
 */
interface DismissedRows {
    val dismissed: Flow<Set<String>>

    suspend fun dismiss(taskId: String)
}

/** In the session's DataStore, so signing out clears it. */
class DataStoreDismissedRows
    @Inject
    constructor(
        private val store: DataStore<Preferences>,
    ) : DismissedRows {
        private val key = stringSetPreferencesKey("dismissed_rows")

        override val dismissed: Flow<Set<String>> = store.data.map { it[key] ?: emptySet() }

        override suspend fun dismiss(taskId: String) {
            store.edit { it[key] = (it[key] ?: emptySet()) + taskId }
        }
    }
