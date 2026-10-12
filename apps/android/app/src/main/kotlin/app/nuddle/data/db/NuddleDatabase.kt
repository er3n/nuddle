package app.nuddle.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Local store (docs/architecture.md): tasks, poke steps, invites and the command outbox. 2: `tasks.snoozesUsed`
 * (NUD-36). 3: `invites` (NUD-13). 4: `connections`, `tasks.createdAt` and `tasks.rawText` (NUD-14).
 */
@Database(
    entities = [TaskEntity::class, PokeStepEntity::class, OutboxEntity::class, InviteEntity::class, ConnectionEntity::class],
    version = 4,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)],
)
abstract class NuddleDatabase : RoomDatabase() {
    abstract fun tasks(): TaskDao

    abstract fun pokeSteps(): PokeStepDao

    abstract fun outbox(): OutboxDao

    abstract fun invites(): InviteDao

    abstract fun connections(): ConnectionDao
}
