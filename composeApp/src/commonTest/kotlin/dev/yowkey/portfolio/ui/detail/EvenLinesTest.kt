package dev.yowkey.portfolio.ui.detail

import kotlin.test.Test
import kotlin.test.assertEquals

class EvenLinesTest {

    private fun sizes(count: Int, columns: Int) = evenLines(List(count) { it }, columns).map { it.size }

    @Test
    fun aBandThatFitsIsOneLine() {
        assertEquals(listOf(3), sizes(3, columns = 3))
        assertEquals(listOf(1), sizes(1, columns = 2))
    }

    @Test
    fun aWrappedBandSplitsEvenlyLongerLinesFirst() {
        assertEquals(listOf(2, 2), sizes(4, columns = 3), "not 3 and a stray 1")
        assertEquals(listOf(3, 2, 2), sizes(7, columns = 3))
        assertEquals(listOf(2, 2, 1), sizes(5, columns = 2))
        assertEquals(listOf(1, 1, 1), sizes(3, columns = 1))
    }

    @Test
    fun theModulesKeepTheirOrder() {
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), evenLines(listOf("a", "b", "c", "d"), 3))
    }
}
