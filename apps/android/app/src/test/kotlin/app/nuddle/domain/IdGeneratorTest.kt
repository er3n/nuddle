package app.nuddle.domain

import app.nuddle.testing.FakeClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.uuid.Uuid

class IdGeneratorTest {
    @Test
    fun `keys are UUIDv7 stamped with the injected clock`() {
        val clock = FakeClock()
        val id = IdGenerator(clock).next()
        val uuid = Uuid.parse(id)
        val bytes = uuid.toByteArray()
        assertEquals(7, (bytes[6].toInt() shr 4) and 0xF)
        val millis = bytes.take(6).fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
        assertEquals(clock.now.toEpochMilliseconds(), millis)
    }

    @Test
    fun `two keys at the same instant differ`() {
        val ids = IdGenerator(FakeClock())
        assertNotEquals(ids.next(), ids.next())
    }
}
