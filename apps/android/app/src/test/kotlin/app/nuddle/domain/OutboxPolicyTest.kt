package app.nuddle.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class OutboxPolicyTest {
    @Test
    fun `answers decide whether a queued command is kept`() {
        assertEquals(SendOutcome.SENT, OutboxPolicy.outcome(200))
        assertEquals(SendOutcome.RETRY, OutboxPolicy.outcome(null))
        assertEquals(SendOutcome.RETRY, OutboxPolicy.outcome(401))
        assertEquals(SendOutcome.RETRY, OutboxPolicy.outcome(429))
        assertEquals(SendOutcome.RETRY, OutboxPolicy.outcome(502))
        assertEquals(SendOutcome.DROP, OutboxPolicy.outcome(409))
        assertEquals(SendOutcome.DROP, OutboxPolicy.outcome(404))
    }
}
