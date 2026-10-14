package app.nuddle.push

import app.nuddle.data.sync.Syncer
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** A push syncs before the service goes away; what does not finish in time is queued (NUD-62). */
class SyncOnPushTest {
    private var queued = 0

    @Test
    fun `a sync that finishes in time queues nothing`() =
        runTest {
            syncOnPush({ true }, 8_000) { queued++ }
            assertEquals(0, queued)
        }

    @Test
    fun `a failed sync is queued`() =
        runTest {
            syncOnPush({ false }, 8_000) { queued++ }
            assertEquals(1, queued)
        }

    @Test
    fun `a sync that runs out of time is queued`() =
        runTest {
            val stuck = Syncer { awaitCancellation() }
            syncOnPush(stuck, 8_000) { queued++ }
            assertEquals(1, queued)
        }
}
