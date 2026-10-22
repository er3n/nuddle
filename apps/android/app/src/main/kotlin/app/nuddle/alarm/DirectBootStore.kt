package app.nuddle.alarm

import android.content.Context
import androidx.core.os.UserManagerCompat
import app.nuddle.domain.AlarmKey
import app.nuddle.domain.LockedFire
import app.nuddle.domain.LockedPlan
import app.nuddle.domain.LockedStep
import app.nuddle.domain.PokeLevel
import app.nuddle.domain.PokeStep
import app.nuddle.domain.QuietHours
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Instant

/** Whether the person has unlocked the phone since it started; before that only device-protected storage is readable. */
fun Context.userUnlocked(): Boolean = UserManagerCompat.isUserUnlocked(this)

/**
 * The armed pokes and the pokes that rang before the first unlock, in device-protected storage (docs/ux/platform.md,
 * Direct boot; NUD-125). Readable right after a reboot, so it holds only ids, times and levels.
 */
interface DirectBootStore {
    suspend fun plan(): LockedPlan

    /** Replaces the armed steps; the pokes that rang before the unlock stay until [takeFires]. */
    suspend fun savePlan(plan: LockedPlan)

    suspend fun fires(): List<LockedFire>

    suspend fun recordFire(fire: LockedFire)

    /** The pokes that rang before the unlock, removed so they are handed over once. */
    suspend fun takeFires(): List<LockedFire>

    /** Signing out: nothing of this person stays readable before an unlock. */
    suspend fun clear()

    /** Keeps nothing: the default where tests build a class without caring about direct boot. */
    object None : DirectBootStore {
        override suspend fun plan() = LockedPlan()

        override suspend fun savePlan(plan: LockedPlan) = Unit

        override suspend fun fires(): List<LockedFire> = emptyList()

        override suspend fun recordFire(fire: LockedFire) = Unit

        override suspend fun takeFires(): List<LockedFire> = emptyList()

        override suspend fun clear() = Unit
    }
}

@Serializable
private data class StoredStep(
    val taskId: String,
    val planVersion: Int,
    val seq: Int,
    val plannedAt: Long,
    val level: String,
    val moment: Boolean,
)

@Serializable
private data class StoredQuietHours(
    val start: String,
    val end: String,
    val timeZone: String,
)

@Serializable
private data class StoredFire(
    val taskId: String,
    val planVersion: Int,
    val seq: Int,
    val firedAt: Long,
    val level: String,
)

@Serializable
private data class Stored(
    val steps: List<StoredStep> = emptyList(),
    val quietHours: StoredQuietHours? = null,
    val fires: List<StoredFire> = emptyList(),
)

/** One small JSON file under the device-protected `files` folder, written whole each time. */
@Singleton
class FileDirectBootStore internal constructor(
    private val file: File,
) : DirectBootStore {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(File(context.createDeviceProtectedStorageContext().filesDir, FILE_NAME))

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()

    override suspend fun plan(): LockedPlan = read().toPlan()

    override suspend fun savePlan(plan: LockedPlan) =
        update {
            it.copy(steps = plan.steps.map(::toStored), quietHours = plan.quietHours?.toStored())
        }

    override suspend fun fires(): List<LockedFire> = read().fires.mapNotNull { it.toFire() }

    override suspend fun recordFire(fire: LockedFire) =
        update { stored ->
            val fired = StoredFire(fire.key.taskId, fire.key.planVersion, fire.key.seq, fire.firedAt.toEpochMilliseconds(), fire.level.name)
            stored.copy(fires = stored.fires + fired)
        }

    override suspend fun takeFires(): List<LockedFire> =
        lock.withLock {
            val stored = readUnlocked()
            if (stored.fires.isNotEmpty()) write(stored.copy(fires = emptyList()))
            stored.fires.mapNotNull { it.toFire() }
        }

    override suspend fun clear() {
        lock.withLock { withContext(Dispatchers.IO) { file.delete() } }
    }

    private suspend fun read(): Stored = lock.withLock { readUnlocked() }

    private suspend fun update(change: (Stored) -> Stored) {
        lock.withLock { write(change(readUnlocked())) }
    }

    /** A missing or unreadable file is an empty one: the worst case is a poke that does not ring before the unlock. */
    private suspend fun readUnlocked(): Stored =
        withContext(Dispatchers.IO) {
            try {
                if (file.exists()) json.decodeFromString(Stored.serializer(), file.readText()) else Stored()
            } catch (_: IOException) {
                Stored()
            } catch (_: SerializationException) {
                Stored()
            } catch (_: IllegalArgumentException) {
                Stored()
            }
        }

    private suspend fun write(stored: Stored) {
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            // Written next to it and renamed, so a reboot halfway through never leaves half a file.
            val next = File(file.parentFile, "$FILE_NAME.tmp")
            next.writeText(json.encodeToString(Stored.serializer(), stored))
            next.renameTo(file)
        }
    }

    private companion object {
        const val FILE_NAME = "direct-boot-pokes.json"
    }
}

private fun Stored.toPlan() =
    LockedPlan(
        steps = steps.mapNotNull { it.toLocked() },
        quietHours = quietHours?.let { runCatching { QuietHours.parse(it.start, it.end, it.timeZone) }.getOrNull() },
    )

private fun toStored(locked: LockedStep) =
    StoredStep(
        taskId = locked.step.taskId,
        planVersion = locked.step.planVersion,
        seq = locked.step.seq,
        plannedAt = locked.step.plannedAt.toEpochMilliseconds(),
        level = locked.step.level.name,
        moment = locked.moment,
    )

private fun QuietHours.toStored() = StoredQuietHours(start.toString(), end.toString(), timeZone.id)

private fun StoredStep.toLocked(): LockedStep? {
    val level = PokeLevel.entries.firstOrNull { it.name == level } ?: return null
    return LockedStep(PokeStep(taskId, planVersion, seq, Instant.fromEpochMilliseconds(plannedAt), level), moment)
}

private fun StoredFire.toFire(): LockedFire? {
    val level = PokeLevel.entries.firstOrNull { it.name == level } ?: return null
    return LockedFire(AlarmKey(taskId, planVersion, seq), Instant.fromEpochMilliseconds(firedAt), level)
}
