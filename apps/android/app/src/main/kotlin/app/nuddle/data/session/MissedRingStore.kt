package app.nuddle.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.time.Instant

/** What this phone itself knows about pokes that did not ring (2g, 2h). Losses the server found come through sync. */
data class MissedRingState(
    /** The newest poke found lost here, after a force stop. */
    val lostAt: Instant? = null,
    /** When a poke last rang on this phone. */
    val rangAt: Instant? = null,
    /** When every brand step (2h) was last done. */
    val fixedAt: Instant? = null,
    /** Brand step id to when its settings page was last opened. */
    val opened: Map<String, Instant> = emptyMap(),
)

interface MissedRingStore {
    val state: Flow<MissedRingState>

    /** Keeps the newest loss. */
    suspend fun lost(plannedAt: Instant)

    suspend fun rang(at: Instant)

    suspend fun fixed(at: Instant)

    suspend fun opened(
        stepId: String,
        at: Instant,
    )

    /** The force stop handled last, so the same one is not counted again on the next launch. */
    suspend fun handledStop(): Instant?

    suspend fun markStopHandled(at: Instant)

    /** When the hourly alarm check last ran (NUD-125), so a loss is looked for only since then. */
    suspend fun lastAlarmCheck(): Instant?

    suspend fun markAlarmCheck(at: Instant)
}

/** In the session's DataStore, so signing out clears it. */
class DataStoreMissedRingStore
    @Inject
    constructor(
        private val store: DataStore<Preferences>,
    ) : MissedRingStore {
        private object Keys {
            val lostAt = longPreferencesKey("missed_ring_lost_at")
            val rangAt = longPreferencesKey("missed_ring_rang_at")
            val fixedAt = longPreferencesKey("missed_ring_fixed_at")
            val opened = stringSetPreferencesKey("missed_ring_opened")
            val handledStop = longPreferencesKey("missed_ring_handled_stop")
            val alarmCheck = longPreferencesKey("missed_ring_alarm_check")
        }

        override val state: Flow<MissedRingState> =
            store.data.map { prefs ->
                MissedRingState(
                    lostAt = prefs[Keys.lostAt]?.let(Instant::fromEpochMilliseconds),
                    rangAt = prefs[Keys.rangAt]?.let(Instant::fromEpochMilliseconds),
                    fixedAt = prefs[Keys.fixedAt]?.let(Instant::fromEpochMilliseconds),
                    opened = readOpened(prefs[Keys.opened].orEmpty()),
                )
            }

        override suspend fun lost(plannedAt: Instant) {
            store.edit { it[Keys.lostAt] = maxOf(it[Keys.lostAt] ?: 0, plannedAt.toEpochMilliseconds()) }
        }

        override suspend fun rang(at: Instant) {
            store.edit { it[Keys.rangAt] = at.toEpochMilliseconds() }
        }

        override suspend fun fixed(at: Instant) {
            store.edit { it[Keys.fixedAt] = at.toEpochMilliseconds() }
        }

        override suspend fun opened(
            stepId: String,
            at: Instant,
        ) {
            store.edit { prefs ->
                val others = prefs[Keys.opened].orEmpty().filterNot { it.substringBeforeLast(SEPARATOR) == stepId }
                prefs[Keys.opened] = others.toSet() + "$stepId$SEPARATOR${at.toEpochMilliseconds()}"
            }
        }

        override suspend fun handledStop(): Instant? = store.data.first()[Keys.handledStop]?.let(Instant::fromEpochMilliseconds)

        override suspend fun markStopHandled(at: Instant) {
            store.edit { it[Keys.handledStop] = at.toEpochMilliseconds() }
        }

        override suspend fun lastAlarmCheck(): Instant? = store.data.first()[Keys.alarmCheck]?.let(Instant::fromEpochMilliseconds)

        override suspend fun markAlarmCheck(at: Instant) {
            store.edit { it[Keys.alarmCheck] = at.toEpochMilliseconds() }
        }

        private companion object {
            const val SEPARATOR = "@"

            fun readOpened(entries: Set<String>): Map<String, Instant> =
                entries
                    .mapNotNull { entry ->
                        val at = entry.substringAfterLast(SEPARATOR).toLongOrNull() ?: return@mapNotNull null
                        entry.substringBeforeLast(SEPARATOR) to Instant.fromEpochMilliseconds(at)
                    }.toMap()
        }
    }
