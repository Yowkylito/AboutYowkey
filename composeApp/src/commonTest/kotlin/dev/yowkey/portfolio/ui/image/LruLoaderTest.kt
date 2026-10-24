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

    /** A peek answers from the cache alone, at once, and counts as a use: a peeked entry is the last to go. */
    @Test
    fun peeksWithoutLoading() = runTest {
        val loads = mutableListOf<String>()
        val loader = LruLoader<String, String>(capacity = 2, dispatcher = StandardTestDispatcher(testScheduler)) {
            loads += it
            it.uppercase()
        }

        assertEquals(null, loader.peek("a"), "not cached: nothing, and nothing loaded")
        loader.get("a")
        loader.get("b")
        assertEquals("A", loader.peek("a"))
        loader.get("c") // evicts b, the peek having touched a
        assertEquals(listOf("a", "c"), loader.cachedKeys())
        assertEquals(listOf("a", "b", "c"), loads)
    }

    /** As the viewer cache weighs them: a still is big, a clip (file + one frame) is a fraction of it. */
    @Test
    fun weighsEntriesAgainstOneBudget() = runTest {
        val weights = mapOf("still-1" to 6L, "still-2" to 6L, "clip" to 2L, "huge" to 30L)
        val loader = LruLoader<String, String>(
            capacity = 14,
            dispatcher = StandardTestDispatcher(testScheduler),
            weigh = { weights.getValue(it) },
        ) { it }

        loader.get("still-1")
        loader.get("clip")
        loader.get("still-2")
        assertEquals(14L, loader.cachedWeight(), "6 + 2 + 6 fits exactly")
        loader.get("clip") // touch
        loader.get("still-1") // touch, from cache
        assertEquals(listOf("still-2", "clip", "still-1"), loader.cachedKeys())

        loader.get("huge")
        assertEquals(listOf("huge"), loader.cachedKeys(), "over budget alone, but kept: its caller is drawing it")
        assertEquals(30L, loader.cachedWeight())
    }
}
