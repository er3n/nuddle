package app.nuddle.data.session

import app.nuddle.domain.QuietHours
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/** A Supabase Auth session obtained through our API (ADR 0014). `personId` is the JWT `sub`. */
data class StoredSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Instant,
    val personId: String,
    val registered: Boolean,
)

/** What the device keeps about the signed-in person between launches. */
interface SessionStore {
    val session: Flow<StoredSession?>
    val displayName: Flow<String?>

    suspend fun current(): StoredSession?

    suspend fun save(session: StoredSession)

    suspend fun markRegistered(displayName: String)

    /** `me` from register or sync: name and quiet hours (re-checked before every poke). */
    suspend fun saveMe(
        displayName: String,
        quietHours: QuietHours,
    )

    suspend fun quietHours(): QuietHours?

    suspend fun deviceId(): String?

    suspend fun saveDeviceId(deviceId: String)

    suspend fun syncCursor(): String?

    suspend fun saveSyncCursor(cursor: String)

    suspend fun clear()
}
