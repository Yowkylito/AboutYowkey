package dev.yowkey.portfolio.ui.navigation

import androidx.compose.ui.input.key.Key
import dev.yowkey.portfolio.ui.navigation.ScrollRules.Move
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScrollRulesTest {

    private val screen = 1000f
    private val line = 72f

    private fun move(key: Key, shift: Boolean = false, ctrl: Boolean = false, alt: Boolean = false, meta: Boolean = false) =
        ScrollRules.move(key, shift, ctrl, alt, meta, screen, line)

    @Test
    fun spaceAndThePageKeysMoveMostOfAScreen() {
        assertEquals(Move.By(850f), move(Key.Spacebar))
        assertEquals(Move.By(-850f), move(Key.Spacebar, shift = true))
        assertEquals(Move.By(850f), move(Key.PageDown))
        assertEquals(Move.By(-850f), move(Key.PageUp))
        assertEquals(Move.By(850f), move(Key.PageDown, shift = true), "Shift changes nothing for the page keys")
    }

    @Test
    fun homeAndEndGoToTheEndsWithOrWithoutCtrlAsCmdArrowsDo() {
        assertEquals(Move.Top, move(Key.MoveHome))
        assertEquals(Move.Bottom, move(Key.MoveEnd))
        assertEquals(Move.Top, move(Key.MoveHome, ctrl = true))
        assertEquals(Move.Bottom, move(Key.MoveEnd, ctrl = true))
        assertEquals(Move.Top, move(Key.DirectionUp, meta = true))
        assertEquals(Move.Bottom, move(Key.DirectionDown, meta = true))
    }

    @Test
    fun theArrowsMoveALine() {
        assertEquals(Move.By(72f), move(Key.DirectionDown))
        assertEquals(Move.By(-72f), move(Key.DirectionUp))
    }

    @Test
    fun shortcutsAndOtherKeysAreLeftAlone() {
        listOf(Key.Spacebar, Key.PageDown, Key.PageUp, Key.MoveHome, Key.MoveEnd, Key.DirectionDown, Key.DirectionUp)
            .forEach { key -> assertNull(move(key, alt = true), "Alt+$key") }
        listOf(Key.Spacebar, Key.PageDown, Key.PageUp).forEach { key ->
            assertNull(move(key, ctrl = true), "Ctrl+$key")
            assertNull(move(key, meta = true), "Cmd+$key")
        }
        assertNull(move(Key.DirectionDown, ctrl = true))
        assertNull(move(Key.DirectionUp, ctrl = true))
        listOf(Key.Enter, Key.Tab, Key.Escape, Key.A, Key.DirectionLeft, Key.DirectionRight)
            .forEach { key -> assertNull(move(key), "$key") }
    }

    @Test
    fun aNearMeasuredJumpGlidesTheWholeWay() {
        val down = ScrollRules.jump(600f, measured = true, screenPx = screen)
        assertFalse(down.cutFirst)
        assertEquals(600f, down.glidePx)
        val up = ScrollRules.jump(-1500f, measured = true, screenPx = screen)
        assertFalse(up.cutFirst, "exactly a full glide still glides")
        assertEquals(-1500f, up.glidePx)
    }

    @Test
    fun aFarOrEstimatedJumpCutsFirstAndGlidesAtMostAFullGlide() {
        val far = ScrollRules.jump(12_000f, measured = true, screenPx = screen)
        assertTrue(far.cutFirst)
        assertEquals(1500f, far.glidePx)
        assertEquals(ScrollRules.MAX_GLIDE_MILLIS, far.millis)
        val farUp = ScrollRules.jump(-12_000f, measured = false, screenPx = screen)
        assertTrue(farUp.cutFirst)
        assertEquals(-1500f, farUp.glidePx)
        val guessed = ScrollRules.jump(900f, measured = false, screenPx = screen)
        assertTrue(guessed.cutFirst, "an estimate is never glided blind: the cut makes the glide exact")
        assertEquals(900f, guessed.glidePx)
    }

    @Test
    fun aJumpOfNothingDoesNothing() {
        val none = ScrollRules.jump(0f, measured = true, screenPx = screen)
        assertFalse(none.cutFirst)
        assertEquals(0f, none.glidePx)
        val unmeasured = ScrollRules.jump(500f, measured = true, screenPx = 0f)
        assertTrue(unmeasured.cutFirst, "before the list has a size, a jump can only cut")
        assertEquals(0f, unmeasured.glidePx)
    }

    @Test
    fun glidesTakeLongerTheFurtherTheyGoButNeverSnapOrCrawl() {
        assertEquals(ScrollRules.MIN_GLIDE_MILLIS, ScrollRules.glideMillis(0f, screen))
        assertEquals(ScrollRules.MAX_GLIDE_MILLIS, ScrollRules.glideMillis(1500f, screen))
        assertEquals(ScrollRules.MAX_GLIDE_MILLIS, ScrollRules.glideMillis(40_000f, screen))
        assertEquals(400, ScrollRules.glideMillis(375f, screen), "a quarter of a full glide: half the extra time")
        val times = listOf(50f, 200f, 600f, 1000f, 1400f).map { ScrollRules.glideMillis(it, screen) }
        assertEquals(times.sorted(), times)
        assertTrue(times.all { it in ScrollRules.MIN_GLIDE_MILLIS..ScrollRules.MAX_GLIDE_MILLIS })
        assertEquals(ScrollRules.glideMillis(600f, screen), ScrollRules.glideMillis(-600f, screen))
        assertEquals(ScrollRules.MIN_GLIDE_MILLIS, ScrollRules.glideMillis(600f, 0f))
    }

    @Test
    fun anOffScreenRowsDistanceIsCountedInAverageRows() {
        assertEquals(1700f, ScrollRules.estimateDistance(index = 6, firstIndex = 2, firstScrolledPx = 300, averageRowPx = 500f))
        assertEquals(-2900f, ScrollRules.estimateDistance(index = 0, firstIndex = 4, firstScrolledPx = 300, averageRowPx = 650f))
    }

    @Test
    fun scrolledScreensIsExactOnTheFirstRowAndEstimatedPastIt() {
        assertEquals(0.4f, ScrollRules.scrolledScreens(firstIndex = 0, firstScrolledPx = 400, averageRowPx = 900f, screenPx = 1000))
        assertEquals(2.2f, ScrollRules.scrolledScreens(firstIndex = 3, firstScrolledPx = 400, averageRowPx = 600f, screenPx = 1000))
        assertEquals(0f, ScrollRules.scrolledScreens(firstIndex = 3, firstScrolledPx = 400, averageRowPx = 600f, screenPx = 0))
    }

    @Test
    fun backToTopShowsPastAScreenAndAHalfAndHidesOnlyNearTheTop() {
        assertFalse(ScrollRules.backToTopShows(0f, showing = false))
        assertFalse(ScrollRules.backToTopShows(1.4f, showing = false))
        assertTrue(ScrollRules.backToTopShows(1.6f, showing = false))
        assertTrue(ScrollRules.backToTopShows(1.2f, showing = true), "once shown, it stays down to a screen")
        assertFalse(ScrollRules.backToTopShows(0.9f, showing = true))
        assertFalse(ScrollRules.backToTopShows(1.2f, showing = false), "between the two, it stays as it was")
    }
}
