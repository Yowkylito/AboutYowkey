package dev.yowkey.portfolio.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WrapLinesTest {

    @Test
    fun itemsFillALineThenWrap() {
        // 40 + 8 + 50 = 98 fits in 100; the third item would make 98 + 8 + 30 = 136.
        assertEquals(listOf(0..1, 2..2), wrapLines(listOf(40, 50, 30), maxWidth = 100, spacing = 8))
        assertEquals(listOf(0..2), wrapLines(listOf(40, 50, 30), maxWidth = 136, spacing = 8), "exactly full still fits")
    }

    @Test
    fun anOversizedItemGetsALineOfItsOwn() {
        assertEquals(listOf(0..0, 1..1, 2..2), wrapLines(listOf(10, 500, 10), maxWidth = 100, spacing = 8))
    }

    @Test
    fun nothingToLayIsNoLines() {
        assertTrue(wrapLines(emptyList(), 100, 8).isEmpty())
        assertEquals(listOf(0..3), wrapLines(listOf(1, 1, 1, 1), Int.MAX_VALUE, 8), "an unbounded width is one line")
    }
}
