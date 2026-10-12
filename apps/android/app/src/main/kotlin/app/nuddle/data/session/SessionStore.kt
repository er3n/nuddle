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

/** `me` from register or sync: what profile (5a) shows and quiet hours (re-checked before every poke). */
data class Profile(
    val displayName: String,
    val phone: String,
    val quietHours: QuietHours,
    /** "Benim işlerimde tanıdıklarımı kullanabilirsin" */
    val useConnectionsForMyTasks: Boolean,
    /** "Başkalarının işleri için bana haber verilebilir" */
    val notifyMeForOthersTasks: Boolean,
)

/** What the device keeps about the signed-in person between launches. */
interface SessionStore {
    val session: Flow<StoredSession?>
    val displayName: Flow<String?>

    suspend fun current(): StoredSession?

    suspend fun save(session: StoredSession)

    suspend fun markRegistered(displayName: String)

    /** Null until the first `me` arrives. */
    val profile: Flow<Profile?>

    suspend fun saveMe(profile: Profile)

    suspend fun quietHours(): QuietHours?

    suspend fun deviceId(): String?

    suspend fun saveDeviceId(deviceId: String)

    suspend fun syncCursor(): String?

    suspend fun saveSyncCursor(cursor: String)

    suspend fun clear()
}
