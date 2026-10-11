package app.nuddle.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.nuddle.domain.QuietHours
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Instant

/**
 * Preferences DataStore in app-private storage. Backup and device transfer exclude it
 * (res/xml/data_extraction_rules.xml), so tokens never leave the device.
 */
@Singleton
class DataStoreSessionStore
    @Inject
    constructor(
        private val store: DataStore<Preferences>,
    ) : SessionStore {
        private object Keys {
            val accessToken = stringPreferencesKey("access_token")
            val refreshToken = stringPreferencesKey("refresh_token")
            val expiresAt = longPreferencesKey("expires_at")
            val personId = stringPreferencesKey("person_id")
            val registered = booleanPreferencesKey("registered")
            val displayName = stringPreferencesKey("display_name")
            val quietStart = stringPreferencesKey("quiet_start")
            val quietEnd = stringPreferencesKey("quiet_end")
            val quietZone = stringPreferencesKey("quiet_zone")
            val deviceId = stringPreferencesKey("device_id")
            val syncCursor = stringPreferencesKey("sync_cursor")
        }

        override val session: Flow<StoredSession?> = store.data.map(::readSession)

        override val displayName: Flow<String?> = store.data.map { it[Keys.displayName] }

        override suspend fun current(): StoredSession? = session.first()

        override suspend fun save(session: StoredSession) {
            store.edit {
                it[Keys.accessToken] = session.accessToken
                it[Keys.refreshToken] = session.refreshToken
                it[Keys.expiresAt] = session.expiresAt.toEpochMilliseconds()
                it[Keys.personId] = session.personId
                it[Keys.registered] = session.registered
            }
        }

        override suspend fun markRegistered(displayName: String) {
            store.edit {
                it[Keys.registered] = true
                it[Keys.displayName] = displayName
            }
        }

        override suspend fun saveMe(
            displayName: String,
            quietHours: QuietHours,
        ) {
            store.edit {
                it[Keys.displayName] = displayName
                it[Keys.quietStart] = quietHours.start.toString()
                it[Keys.quietEnd] = quietHours.end.toString()
                it[Keys.quietZone] = quietHours.timeZone.id
            }
        }

        override suspend fun quietHours(): QuietHours? {
            val prefs = store.data.first()
            val start = prefs[Keys.quietStart] ?: return null
            val end = prefs[Keys.quietEnd] ?: return null
            val zone = prefs[Keys.quietZone] ?: return null
            return QuietHours.parse(start, end, zone)
        }

        override suspend fun deviceId(): String? = store.data.first()[Keys.deviceId]

        override suspend fun saveDeviceId(deviceId: String) {
            store.edit { it[Keys.deviceId] = deviceId }
        }

        override suspend fun syncCursor(): String? = store.data.first()[Keys.syncCursor]

        override suspend fun saveSyncCursor(cursor: String) {
            store.edit { it[Keys.syncCursor] = cursor }
        }

        override suspend fun clear() {
            store.edit { it.clear() }
        }

        private fun readSession(prefs: Preferences): StoredSession? {
            val access = prefs[Keys.accessToken] ?: return null
            val refresh = prefs[Keys.refreshToken] ?: return null
            val expires = prefs[Keys.expiresAt] ?: return null
            val person = prefs[Keys.personId] ?: return null
            return StoredSession(
                access,
                refresh,
                Instant.fromEpochMilliseconds(expires),
                person,
                prefs[Keys.registered] ?: false,
            )
        }
    }
