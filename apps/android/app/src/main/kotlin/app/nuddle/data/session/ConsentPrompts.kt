package app.nuddle.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Invites whose consent screen (0f) already opened by itself. It opens once per invite; after "Şimdi değil" the
 * invite waits in the bell (docs/ux/screens.md 0f, TODO(open-questions#33)).
 */
interface ConsentPrompts {
    suspend fun shown(inviteId: String): Boolean

    suspend fun markShown(inviteId: String)
}

/** In the session's DataStore, so signing out clears it. */
class DataStoreConsentPrompts
    @Inject
    constructor(
        private val store: DataStore<Preferences>,
    ) : ConsentPrompts {
        private val key = stringSetPreferencesKey("consent_prompted")

        override suspend fun shown(inviteId: String): Boolean = inviteId in (store.data.first()[key] ?: emptySet())

        override suspend fun markShown(inviteId: String) {
            store.edit { it[key] = (it[key] ?: emptySet()) + inviteId }
        }
    }
