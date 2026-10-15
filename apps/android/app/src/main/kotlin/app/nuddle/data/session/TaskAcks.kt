package app.nuddle.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.nuddle.api.model.AcknowledgeCommand
import app.nuddle.data.outbox.Outbox
import app.nuddle.domain.IdGenerator
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * The recipient's side of the sender's tick (NUD-68): this phone tells the server once that a task reached it, and once
 * that its person has seen it. The server logs each once too, so a repeat after reinstalling costs only a request.
 */
interface TaskAcks {
    /** Tasks to me that sync just stored. */
    suspend fun reached(taskIds: Collection<String>)

    /** Tasks to me that are on screen now: home's Bana tab or their detail. */
    suspend fun seen(taskIds: Collection<String>)
}

/** What was already told, in the session's DataStore so signing out clears it. */
class OutboxTaskAcks
    @Inject
    constructor(
        private val store: DataStore<Preferences>,
        private val outbox: Outbox,
        private val ids: IdGenerator,
    ) : TaskAcks {
        private val key = stringSetPreferencesKey("acked_tasks")

        override suspend fun reached(taskIds: Collection<String>) = tell(taskIds, seen = false)

        override suspend fun seen(taskIds: Collection<String>) = tell(taskIds, seen = true)

        private suspend fun tell(
            taskIds: Collection<String>,
            seen: Boolean,
        ) {
            if (taskIds.isEmpty()) return
            val told = store.data.first()[key] ?: emptySet()
            val marks = if (seen) listOf(SEEN) else listOf(REACHED)
            val fresh = taskIds.filter { id -> "${marks.first()}$id" !in told }.distinct()
            if (fresh.isEmpty()) return
            for (taskId in fresh) {
                val id = ids.next()
                outbox.enqueue(
                    "acknowledge",
                    id,
                    AcknowledgeCommand(idempotencyKey = id, taskId = taskId, seen = seen),
                    AcknowledgeCommand.serializer(),
                )
            }
            // Seen covers reached: the server logs both.
            val added = fresh.flatMap { id -> if (seen) listOf("$SEEN$id", "$REACHED$id") else listOf("$REACHED$id") }
            store.edit { it[key] = (it[key] ?: emptySet()) + added }
        }

        private companion object {
            const val REACHED = "reached:"
            const val SEEN = "seen:"
        }
    }
