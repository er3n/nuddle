package app.nuddle.testing

import app.nuddle.data.session.TaskAcks

/** Records what the phone would tell the sender (NUD-68). */
class RecordingTaskAcks : TaskAcks {
    val reached = mutableListOf<String>()
    val seen = mutableListOf<String>()

    override suspend fun reached(taskIds: Collection<String>) {
        reached += taskIds
    }

    override suspend fun seen(taskIds: Collection<String>) {
        seen += taskIds
    }
}
