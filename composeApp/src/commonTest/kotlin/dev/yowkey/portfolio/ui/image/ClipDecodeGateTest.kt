package dev.yowkey.portfolio.ui.image

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClipDecodeGateTest {

    @Test
    fun onlyTheFirstCallerInAFrameMayDecode() {
        val gate = ClipDecodeGate()
        assertTrue(gate.tryAcquire(1_000))
        assertFalse(gate.tryAcquire(1_000), "a second clip in the same frame waits")
        assertTrue(gate.tryAcquire(1_016), "and decodes on the next one")
        assertFalse(gate.tryAcquire(1_016))
        assertTrue(gate.tryAcquire(0), "any other frame time is a new frame, even an earlier one (another window's clock)")
    }
}
