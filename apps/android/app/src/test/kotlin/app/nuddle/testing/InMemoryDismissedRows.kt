package app.nuddle.testing

import app.nuddle.data.session.DismissedRows
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class InMemoryDismissedRows : DismissedRows {
    private val ids = MutableStateFlow<Set<String>>(emptySet())
    override val dismissed: Flow<Set<String>> = ids

    override suspend fun dismiss(taskId: String) {
        ids.value += taskId
    }
}
