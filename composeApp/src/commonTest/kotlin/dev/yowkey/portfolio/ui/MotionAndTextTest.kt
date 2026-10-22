package dev.yowkey.portfolio.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import dev.yowkey.portfolio.ui.components.RevealTracker
import dev.yowkey.portfolio.ui.components.emphasize
import dev.yowkey.portfolio.ui.image.CarouselGeometry
import dev.yowkey.portfolio.ui.theme.EmphasisStyles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MotionAndTextTest {

    @Test
    fun rowsEnteringTogetherStaggerUpToTheCap() {
        val tracker = RevealTracker(batchWindowMillis = 34, maxSlots = 4)
        val slots = (0 until 6).map { tracker.reveal("row-$it", frameMillis = 1_000) }
        assertEquals(listOf(0, 1, 2, 3, 3, 3), slots)
    }

    @Test
    fun aRowArrivingAloneHasNoDelay() {
        val tracker = RevealTracker(batchWindowMillis = 34)
        tracker.reveal("a", frameMillis = 1_000)
        tracker.reveal("b", frameMillis = 1_016)
        assertEquals(0, tracker.reveal("c", frameMillis = 1_400))
    }

    @Test
    fun seenRowsAreRememberedByKey() {
        val tracker = RevealTracker()
        assertFalse(tracker.hasSeen("projects-a"))
        tracker.reveal("projects-a", 0)
        assertTrue(tracker.hasSeen("projects-a"))
        assertFalse(tracker.hasSeen("projects-b"))
    }

    @Test
    fun emphasizeStylesWinsAndFigures() {
        val strong = SpanStyle(color = Color.Red, fontWeight = FontWeight.Bold)
        val figure = SpanStyle(fontWeight = FontWeight.SemiBold)
        val text = emphasize("53% of 413 commits", EmphasisStyles(strong, figure))
        assertEquals("53% of 413 commits", text.text)
        val spans = text.spanStyles.map { text.text.substring(it.start, it.end) to it.item }
        assertEquals(listOf("53%" to strong, "413" to figure), spans)
    }

    @Test
    fun carouselHeightComesFromTheTallestShot() {
        val phone = 739f / 1600
        val wide = 16f / 9
        val mixed = CarouselGeometry.of(336f, listOf(wide, phone, wide))
        // Every shot fits the same slide, so paging never changes the height.
        listOf(wide, phone).forEach { aspect ->
            val (w, h) = mixed.frameSize(aspect)
            assertTrue(w <= mixed.pageWidth + 0.5f && h <= mixed.slideHeight + 0.5f, "$aspect: $w x $h in $mixed")
        }
        // The phone is the tallest shot, so it fills the slide height.
        assertEquals(mixed.slideHeight, mixed.frameSize(phone).second, 0.01f)
    }

    @Test
    fun aMixedCarouselKeepsItsLandscapeShotLarge() {
        val wide = 16f / 9
        val mixed = CarouselGeometry.of(336f, listOf(720f / 1600, wide))
        val (w, h) = mixed.frameSize(wide)
        assertTrue(w >= 336f * 0.85f, "$mixed")
        assertTrue(mixed.slideHeight <= h * CarouselGeometry.MixedHeightRatio + 0.5f, "$mixed")
    }

    @Test
    fun phoneOnlyCarouselsLeaveRoomForNeighbours() {
        val geometry = CarouselGeometry.of(336f, List(4) { 720f / 1560 })
        assertTrue(geometry.pageWidth < 336f * 0.5f, "$geometry")
        assertTrue(geometry.slideHeight <= CarouselGeometry.MaxSlideHeight)
    }

    @Test
    fun aSingleLandscapeShotTakesTheFullWidth() {
        val geometry = CarouselGeometry.of(300f, listOf(4f / 3))
        assertEquals(300f, geometry.pageWidth)
        assertEquals(300f * 3 / 4 + CarouselGeometry.BrowserBar, geometry.slideHeight, 0.01f)
    }
}
