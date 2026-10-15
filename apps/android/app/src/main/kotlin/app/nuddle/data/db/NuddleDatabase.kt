package app.nuddle.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Local store (docs/architecture.md): tasks, poke steps, invites and the command outbox. 2: `tasks.snoozesUsed`
 * (NUD-36). 3: `invites` (NUD-13). 4: `connections`, `tasks.createdAt` and `tasks.rawText` (NUD-14).
 * 5: `tasks.photoSentAt` and `tasks.reRequestNote` (NUD-15). 6: `task_events` and `tasks.statusChangedAt` (NUD-16).
 * 7: `connections.connectedAt` and `connections.notifyMe` (NUD-17). 8: `queued_writes` (NUD-19).
 * 11: `connections.avatarPath` (NUD-72). 12: `tasks.repeatEvery`, `repeatDays`, `repeatTime` (NUD-77).
 */
@Database(
    entities = [
        TaskEntity::class, PokeStepEntity::class, OutboxEntity::class, InviteEntity::class, ConnectionEntity::class,
        TaskEventEntity::class, QueuedWriteEntity::class,
    ],
    version = 12,
    autoMigrations = [
        AutoMigration(
            from = 1,
            to = 2,
        ), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6), AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8), AutoMigration(from = 8, to = 9), AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11), AutoMigration(from = 11, to = 12),
    ],
)
abstract class NuddleDatabase : RoomDatabase() {
    abstract fun tasks(): TaskDao

    abstract fun pokeSteps(): PokeStepDao

    abstract fun outbox(): OutboxDao

    abstract fun invites(): InviteDao

    abstract fun connections(): ConnectionDao

    abstract fun taskEvents(): TaskEventDao

    abstract fun queuedWrites(): QueuedWriteDao
}
