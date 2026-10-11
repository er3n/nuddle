package app.nuddle.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
interface TaskDao {
    @Upsert
    suspend fun upsert(tasks: List<TaskEntity>)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: String): TaskEntity?

    @Query("SELECT * FROM tasks")
    suspend fun all(): List<TaskEntity>

    @Query("UPDATE tasks SET status = :status WHERE id = :id")
    suspend fun setStatus(
        id: String,
        status: String,
    )
}

@Dao
interface PokeStepDao {
    @Query("SELECT * FROM poke_steps")
    suspend fun all(): List<PokeStepEntity>

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
