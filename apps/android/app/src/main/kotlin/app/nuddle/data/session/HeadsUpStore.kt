package app.nuddle.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.nuddle.domain.HeadsUpKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Which tasks already had their "Zaten yaptın mı?" on this plan version (NUD-129): at most one each. */
interface HeadsUpStore {
    suspend fun shown(): Set<HeadsUpKey>

    /** Remembers [key] and forgets the tasks that are no longer in [keep], so the set stays small. */
    suspend fun markShown(
        key: HeadsUpKey,
        keep: Set<String>,
    )
}

/** In the session's DataStore, so signing out clears it. */
class DataStoreHeadsUpStore
    @Inject
    constructor(
        private val store: DataStore<Preferences>,
    ) : HeadsUpStore {
        private val shownKey = stringSetPreferencesKey("heads_up_shown")

        override suspend fun shown(): Set<HeadsUpKey> =
            store.data
                .first()[shownKey]
                .orEmpty()
                .mapNotNull(::read)
                .toSet()

        override suspend fun markShown(
            key: HeadsUpKey,
            keep: Set<String>,
        ) {
            store.edit { prefs ->
                val kept = prefs[shownKey].orEmpty().filter { entry -> read(entry)?.taskId in keep }
                prefs[shownKey] = kept.toSet() + "${key.taskId}$SEPARATOR${key.planVersion}"
            }
        }

        private companion object {
            const val SEPARATOR = "@"

            fun read(entry: String): HeadsUpKey? {
                val version = entry.substringAfterLast(SEPARATOR).toIntOrNull() ?: return null
                return HeadsUpKey(entry.substringBeforeLast(SEPARATOR), version)
            }
        }
    }
