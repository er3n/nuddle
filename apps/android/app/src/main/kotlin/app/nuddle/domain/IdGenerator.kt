package app.nuddle.domain

import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Idempotency keys are UUIDv7 (ADR 0006), timestamped from the injected clock (ADR 0009). */
class IdGenerator(
    private val clock: Clock,
) {
    fun next(): String = Uuid.generateV7NonMonotonicAt(clock.now()).toString()
}
