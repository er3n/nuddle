package app.nuddle.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

/** Local store (docs/architecture.md): tasks, poke steps and the command outbox. 2: `tasks.snoozesUsed` (NUD-36). */
@Database(
    entities = [TaskEntity::class, PokeStepEntity::class, OutboxEntity::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class NuddleDatabase : RoomDatabase() {
    abstract fun tasks(): TaskDao

    abstract fun pokeSteps(): PokeStepDao

    abstract fun outbox(): OutboxDao
}
