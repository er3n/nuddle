package app.nuddle.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/** Local store (docs/architecture.md): tasks, poke steps and the command outbox. */
@Database(entities = [TaskEntity::class, PokeStepEntity::class, OutboxEntity::class], version = 1)
abstract class NuddleDatabase : RoomDatabase() {
    abstract fun tasks(): TaskDao

    abstract fun pokeSteps(): PokeStepDao

    abstract fun outbox(): OutboxDao
}
