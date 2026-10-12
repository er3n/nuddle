package app.nuddle.data

import app.nuddle.data.db.TaskDao
import app.nuddle.data.db.TaskEventDao
import app.nuddle.data.session.MissedRingStore
import app.nuddle.data.session.SessionStore
import app.nuddle.domain.MissedRingRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.time.Instant

/** The newest poke of mine that did not ring: found by the server (`poke_lost`) or on this phone. */
class MissedRings
    @Inject
    constructor(
        sessions: SessionStore,
        tasks: TaskDao,
        events: TaskEventDao,
        private val store: MissedRingStore,
    ) {
        /** The newest loss, whether or not it was fixed since: what 2h says. */
        val latest: Flow<Instant?> =
            combine(sessions.session.map { it?.personId }.filterNotNull(), tasks.observeAll(), events.observeAll(), store.state) {
                me,
                all,
                allEvents,
                local,
                ->
                // The sender of a task syncs its events too; only my own phone's losses count.
                val mine = all.filter { it.recipientId == me }.map { it.id }.toSet()
                val fromServer =
                    allEvents
                        .filter { it.type == EventTypes.POKE_LOST && it.taskId in mine }
                        .mapNotNull { it.plannedAt }
                        .maxOrNull()
                listOfNotNull(fromServer, local.lostAt).maxOrNull()
            }

        /** The loss the bell warns about (2g), until a poke rings again or the brand steps were done. */
        val warning: Flow<Instant?> =
            combine(latest, store.state) { lost, local -> MissedRingRules.warning(lost, local.rangAt, local.fixedAt) }
    }
