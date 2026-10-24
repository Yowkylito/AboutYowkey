package dev.yowkey.portfolio.ui

import dev.yowkey.portfolio.ui.BarScroll.Item
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BarScrollTest {

    @Test
    fun theHeroPassesAtItsShareOrOnceItIsGone() {
        assertFalse(BarScroll.heroPassed(firstIndex = 0, firstOffset = 0, heroSize = 1000), "at the top")
        assertFalse(BarScroll.heroPassed(firstIndex = 0, firstOffset = 499, heroSize = 1000))
        assertTrue(BarScroll.heroPassed(firstIndex = 0, firstOffset = 500, heroSize = 1000), "half of it gone")
        assertTrue(BarScroll.heroPassed(firstIndex = 3, firstOffset = 0, heroSize = 0), "the hero is not laid out at all")
        assertFalse(BarScroll.heroPassed(firstIndex = 0, firstOffset = 0, heroSize = 0), "nothing measured yet")
        assertTrue(BarScroll.heroPassed(firstIndex = 0, firstOffset = 300, heroSize = 1000, fraction = 0.3f))
    }

    /** Once the hero has laid its name out, the name itself decides: on a phone's stage it is gone long before half. */
    @Test
    fun theHeroPassesOnceItsNameIsUnderTheBar() {
        assertFalse(BarScroll.heroPassed(firstIndex = 0, firstOffset = 189, heroSize = 1000, nameEnd = 190), "the name's foot still shows")
        assertTrue(BarScroll.heroPassed(firstIndex = 0, firstOffset = 190, heroSize = 1000, nameEnd = 190), "the name is gone")
        assertFalse(BarScroll.heroPassed(firstIndex = 0, firstOffset = 600, heroSize = 1000, nameEnd = 700), "a name low in the row")
        assertTrue(BarScroll.heroPassed(firstIndex = 1, firstOffset = 0, heroSize = 0, nameEnd = 700), "the hero is gone")
    }

    @Test
    fun progressRunsFromTopToBottom() {
        // Ten rows of 100 px in a 300 px viewport.
        assertEquals(0f, progressAt(scroll = 0))
        assertEquals(1f, progressAt(scroll = 700), "the last row's bottom at the viewport's bottom")
        // Halfway: 3.5 rows above, 3.5 below.
        assertEquals(0.5f, progressAt(scroll = 350), 0.0001f)
    }

    @Test
    fun progressOnlyEverGrowsAsThePageScrollsDown() {
        // Rows of very different heights, so the estimate is not linear, but it must never step back.
        val sizes = listOf(900, 120, 80, 400, 60, 300, 1000, 50, 200)
        val viewport = 640
        val end = sizes.sum() - viewport
        var previous = -1f
        for (scroll in 0..end step 7) {
            val now = progressAt(scroll, sizes, viewport)
            assertTrue(now >= previous, "at $scroll px: $now after $previous")
            previous = now
        }
        assertEquals(1f, progressAt(end, sizes, viewport))
    }

    @Test
    fun anEmptyOrUnscrollableListReadsZero() {
        assertEquals(0f, BarScroll.progress(Item(0, 0, 0), Item(0, 0, 0), 0, 800, itemCount = 0))
        assertEquals(0f, BarScroll.progress(Item(0, 0, 100), Item(1, 100, 100), 0, 800, itemCount = 2), "it all fits")
    }

    /** Lays [sizes] out from the top, scrolled down [scroll] px in a [viewport] px window, and reads the progress. */
    private fun progressAt(scroll: Int, sizes: List<Int> = List(10) { 100 }, viewport: Int = 300): Float {
        val tops = sizes.runningFold(0) { top, size -> top + size }
        val visible = sizes.indices.filter { i -> tops[i] + sizes[i] > scroll && tops[i] < scroll + viewport }
        val first = visible.first()
        val last = visible.last()
        return BarScroll.progress(
            first = Item(first, tops[first] - scroll, sizes[first]),
            last = Item(last, tops[last] - scroll, sizes[last]),
            viewportStart = 0,
            viewportEnd = viewport,
            itemCount = sizes.size,
        )
    }
}
