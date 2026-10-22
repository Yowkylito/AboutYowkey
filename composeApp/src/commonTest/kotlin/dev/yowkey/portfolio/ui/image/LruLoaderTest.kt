package dev.yowkey.portfolio.ui.image

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LruLoaderTest {

    @Test
    fun cachesAndEvictsTheLeastRecentlyUsed() = runTest {
        val loads = mutableListOf<String>()
        val loader = LruLoader<String, String>(capacity = 2, dispatcher = StandardTestDispatcher(testScheduler)) {
            loads += it
            it.uppercase()
        }

        assertEquals("A", loader.get("a"))
        loader.get("b")
        loader.get("a") // touch: b is now the oldest
        loader.get("c") // evicts b
        assertEquals(listOf("a", "c"), loader.cachedKeys())

        loader.get("a")
        loader.get("b")
        assertEquals(listOf("a", "b", "c", "b"), loads, "a was served from cache; b had to load again")
    }
}
