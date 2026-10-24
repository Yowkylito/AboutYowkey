package dev.yowkey.portfolio.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The debug build's stand-in: any app can send the extra, so only a local worker is ever taken (and gets the token). */
class DebugApiTest {

    @Test
    fun aLocalStandInIsTakenAsItsOrigin() {
        assertEquals("http://10.0.2.2:8787", DebugApi.standIn("http://10.0.2.2:8787"))
        assertEquals("http://localhost:8787", DebugApi.standIn(" http://localhost:8787/ "))
        assertEquals("http://127.0.0.1", DebugApi.standIn("http://127.0.0.1"))
    }

    @Test
    fun anyOtherServerIsRefused() {
        listOf(
            "https://collector.example",
            "http://collector.example:8787",
            "https://10.0.2.2:8787",
            "http://10.0.2.2.collector.example",
            "http://localhost@collector.example",
            "http://localhost:8787@collector.example",
            "http://localhost.collector.example",
            "http://localhost:8787/elsewhere",
            "http://[::1]:8787",
            "javascript:alert(1)",
            "",
        ).forEach { assertNull(DebugApi.standIn(it), it) }
    }
}
