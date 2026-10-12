package app.nuddle.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Whether setup (2c to 2e) was finished or skipped through on this install. Sign-out clears it with the session. */
interface SetupStore {
    suspend fun done(): Boolean

    suspend fun markDone()
}

/** In the session's DataStore, so [SessionStore.clear] also clears it. */
class DataStoreSetupStore
    @Inject
    constructor(
        private val store: DataStore<Preferences>,
    ) : SetupStore {
        private val key = booleanPreferencesKey("setup_done")

        override suspend fun done(): Boolean = store.data.first()[key] ?: false

        override suspend fun markDone() {
            store.edit { it[key] = true }
        }
    }
