package dev.yowkey.portfolio.ui

import dev.yowkey.portfolio.ui.CoverPlayback.VisibleRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoverPlaybackTest {

    @Test
    fun theMostVisibleCoversPlayUpToTheCap() {
        val rows = listOf(
            VisibleRow(listOf("top-a", "top-b"), visibleFraction = 0.2f),
            VisibleRow(listOf("mid-a", "mid-b"), visibleFraction = 1f),
            VisibleRow(listOf("low-a"), visibleFraction = 0.5f),
        )
        assertEquals(setOf("mid-a", "mid-b"), CoverPlayback.select(rows, max = 2))
        assertEquals(setOf("mid-a"), CoverPlayback.select(rows, max = 1), "one cover: the first of the most visible row")
        assertEquals(setOf("mid-a", "mid-b", "low-a"), CoverPlayback.select(rows, max = 3))
    }

    @Test
    fun offScreenRowsAndRowsWithoutClipsNeverPlay() {
        val rows = listOf(VisibleRow(listOf("gone"), 0f), VisibleRow(emptyList(), 1f), VisibleRow(listOf("seen"), 0.01f))
        assertEquals(setOf("seen"), CoverPlayback.select(rows))
        assertTrue(CoverPlayback.select(emptyList()).isEmpty())
    }

    @Test
    fun tiesKeepPageOrder() {
        val rows = listOf(VisibleRow(listOf("first"), 1f), VisibleRow(listOf("second"), 1f), VisibleRow(listOf("third"), 1f))
        assertEquals(setOf("first", "second"), CoverPlayback.select(rows, max = 2))
    }

    @Test
    fun visibleFractionClipsToTheViewport() {
        assertEquals(1f, CoverPlayback.visibleFraction(offset = 100, size = 200, viewportStart = 0, viewportEnd = 800))
        assertEquals(0.5f, CoverPlayback.visibleFraction(offset = -100, size = 200, viewportStart = 0, viewportEnd = 800))
        assertEquals(0.25f, CoverPlayback.visibleFraction(offset = 750, size = 200, viewportStart = 0, viewportEnd = 800))
        assertEquals(0f, CoverPlayback.visibleFraction(offset = 900, size = 200, viewportStart = 0, viewportEnd = 800))
        assertEquals(0f, CoverPlayback.visibleFraction(offset = 0, size = 0, viewportStart = 0, viewportEnd = 800))
        assertEquals(0.4f, CoverPlayback.visibleFraction(offset = 0, size = 2000, viewportStart = 0, viewportEnd = 800))
    }
}
