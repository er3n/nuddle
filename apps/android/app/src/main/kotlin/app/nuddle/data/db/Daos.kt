package app.nuddle.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Upsert
    suspend fun upsert(tasks: List<TaskEntity>)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: String): TaskEntity?

    @Query("SELECT * FROM tasks")
    suspend fun all(): List<TaskEntity>

    @Query("SELECT * FROM tasks")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("UPDATE tasks SET status = :status WHERE id = :id")
    suspend fun setStatus(
        id: String,
        status: String,
    )

    @Query("UPDATE tasks SET snoozesUsed = snoozesUsed + 1 WHERE id = :id")
    suspend fun countSnooze(id: String)
}

@Dao
interface InviteDao {
    @Upsert
    suspend fun upsert(invites: List<InviteEntity>)

    @Query("SELECT * FROM invites ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<InviteEntity>>

    @Query("SELECT * FROM invites WHERE id = :id")
    fun observe(id: String): Flow<InviteEntity?>
}

@Dao
interface ConnectionDao {
    @Upsert
    suspend fun upsert(connections: List<ConnectionEntity>)

    @Query("SELECT * FROM connections WHERE personId = :personId")
    suspend fun get(personId: String): ConnectionEntity?

    @Query("SELECT * FROM connections")
    fun observeAll(): Flow<List<ConnectionEntity>>
}

@Dao
interface PokeStepDao {
    @Query("SELECT * FROM poke_steps")
    suspend fun all(): List<PokeStepEntity>

    @Query("SELECT * FROM poke_steps")
    fun observeAll(): Flow<List<PokeStepEntity>>

    @Query("SELECT * FROM poke_steps WHERE taskId = :taskId")
    suspend fun forTask(taskId: String): List<PokeStepEntity>

    @Query("SELECT * FROM poke_steps WHERE taskId = :taskId AND planVersion = :planVersion AND seq = :seq")
    suspend fun get(
        taskId: String,
        planVersion: Int,
        seq: Int,
    ): PokeStepEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringExisting(steps: List<PokeStepEntity>)

    @Query("DELETE FROM poke_steps WHERE taskId = :taskId AND planVersion != :planVersion")
    suspend fun deleteOtherVersions(
        taskId: String,
        planVersion: Int,
    )

    /** Stores a plan; steps already known keep their `firedAt`, older versions of the task are dropped. */
    @Transaction
    suspend fun replacePlan(
        taskId: String,
        planVersion: Int,
        steps: List<PokeStepEntity>,
    ) {
        deleteOtherVersions(taskId, planVersion)
        insertIgnoringExisting(steps)
    }

    @Query(
        "UPDATE poke_steps SET firedAt = :firedAt WHERE taskId = :taskId AND planVersion = :planVersion AND seq = :seq",
    )
    suspend fun markFired(
        taskId: String,
        planVersion: Int,
        seq: Int,
        firedAt: Long,
    )
}

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(entry: OutboxEntity)

    @Query("SELECT * FROM outbox ORDER BY createdAt, idempotencyKey")
    suspend fun pending(): List<OutboxEntity>

    @Query("DELETE FROM outbox WHERE idempotencyKey = :key")
    suspend fun remove(key: String)

    @Query("UPDATE outbox SET attempts = attempts + 1 WHERE idempotencyKey = :key")
    suspend fun countAttempt(key: String)
}

@Dao
interface TaskEventDao {
    @Upsert
    suspend fun upsert(events: List<TaskEventEntity>)

    @Query("SELECT * FROM task_events WHERE taskId = :taskId ORDER BY at")
    suspend fun forTask(taskId: String): List<TaskEventEntity>

    @Query("SELECT * FROM task_events ORDER BY at")
    fun observeAll(): Flow<List<TaskEventEntity>>
}
