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
            val quietStart = quietStartKey
            val quietEnd = quietEndKey
            val quietZone = quietZoneKey
            val phone = stringPreferencesKey("phone")
            val useConnections = booleanPreferencesKey("use_connections_for_my_tasks")
            val notifyMeForOthers = booleanPreferencesKey("notify_me_for_others_tasks")
            val avatarPath = stringPreferencesKey("avatar_path")
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

        override val profile: Flow<Profile?> =
            store.data.map { prefs ->
                Profile(
                    displayName = prefs[Keys.displayName] ?: return@map null,
                    phone = prefs[Keys.phone] ?: return@map null,
                    quietHours = readQuietHours(prefs) ?: return@map null,
                    useConnectionsForMyTasks = prefs[Keys.useConnections] ?: true,
                    notifyMeForOthersTasks = prefs[Keys.notifyMeForOthers] ?: true,
                    avatarPath = prefs[Keys.avatarPath],
                )
            }

        override suspend fun saveMe(profile: Profile) {
            store.edit {
                it[Keys.displayName] = profile.displayName
                it[Keys.phone] = profile.phone
                it[Keys.quietStart] = profile.quietHours.start.toString()
                it[Keys.quietEnd] = profile.quietHours.end.toString()
                it[Keys.quietZone] = profile.quietHours.timeZone.id
                it[Keys.useConnections] = profile.useConnectionsForMyTasks
                it[Keys.notifyMeForOthers] = profile.notifyMeForOthersTasks
                profile.avatarPath?.let { path -> it[Keys.avatarPath] = path } ?: it.remove(Keys.avatarPath)
            }
        }

        override suspend fun quietHours(): QuietHours? = readQuietHours(store.data.first())

        override suspend fun deviceId(): String? = store.data.first()[Keys.deviceId]

        override suspend fun saveDeviceId(deviceId: String) {
            store.edit { it[Keys.deviceId] = deviceId }
        }

        override suspend fun syncCursor(): String? = store.data.first()[Keys.syncCursor]

        override suspend fun saveSyncCursor(cursor: String?) {
            store.edit { if (cursor == null) it.remove(Keys.syncCursor) else it[Keys.syncCursor] = cursor }
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

private val quietStartKey = stringPreferencesKey("quiet_start")
private val quietEndKey = stringPreferencesKey("quiet_end")
private val quietZoneKey = stringPreferencesKey("quiet_zone")

private fun readQuietHours(prefs: Preferences): QuietHours? {
    val start = prefs[quietStartKey] ?: return null
    val end = prefs[quietEndKey] ?: return null
    val zone = prefs[quietZoneKey] ?: return null
    return QuietHours.parse(start, end, zone)
}
