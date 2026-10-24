package dev.yowkey.portfolio.ui.sections

import dev.yowkey.portfolio.ui.sections.HeroMotion.ECHOES
import dev.yowkey.portfolio.ui.sections.HeroMotion.ECHO_LAG_MS
import dev.yowkey.portfolio.ui.sections.HeroMotion.END_MS
import dev.yowkey.portfolio.ui.sections.HeroMotion.SLIDE_MS
import dev.yowkey.portfolio.ui.sections.HeroMotion.STAGED_STEPS
import dev.yowkey.portfolio.ui.sections.HeroMotion.TEXT_AT_MS
import dev.yowkey.portfolio.ui.sections.HeroMotion.TEXT_MS
import dev.yowkey.portfolio.ui.sections.HeroMotion.TEXT_STAGGER_MS
import dev.yowkey.portfolio.ui.sections.HeroMotion.TEXT_STEPS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The entrance's timeline: one calm slide, a trail gone before it settles, the words as it does, and an end. */
class HeroMotionTest {

    private val millis = (0..END_MS).map { it.toFloat() }

    @Test
    fun theSlideEasesOutIntoPlaceAndNeverPastIt() {
        assertEquals(0f, HeroMotion.dash(-50f))
        assertEquals(0f, HeroMotion.dash(0f))
        assertTrue(HeroMotion.dash(120f) > 0.5f, "quick at first: ${HeroMotion.dash(120f)}")
        millis.zipWithNext().forEach { (a, b) ->
            assertTrue(HeroMotion.dash(b) >= HeroMotion.dash(a) - 1e-5f, "only ever forward")
            assertTrue(HeroMotion.dash(b) <= 1f + 1e-5f, "never past its place")
        }
        assertEquals(1f, HeroMotion.dash(SLIDE_MS.toFloat()))
        assertEquals(1f, HeroMotion.dash(END_MS.toFloat()))
    }

    /** The trail shows only while the figure moves quickly, and is gone, every echo of it, before it settles. */
    @Test
    fun theTrailIsShortAndGoneBeforeTheFigureSettles() {
        assertEquals(1f, HeroMotion.trail(0f))
        assertEquals(1f, HeroMotion.trail(HeroMotion.TRAIL_FADE_AT_MS.toFloat()))
        val gone = (HeroMotion.TRAIL_FADE_AT_MS + HeroMotion.TRAIL_FADE_MS).toFloat()
        assertEquals(0f, HeroMotion.trail(gone))
        assertTrue(gone <= TEXT_AT_MS, "gone before the words come")
        assertTrue(ECHOES in 2..3, "a few echoes")
        assertTrue(ECHOES * ECHO_LAG_MS <= 150, "and they keep close: the farthest is ${ECHOES * ECHO_LAG_MS} ms behind")
        millis.zipWithNext().forEach { (a, b) -> assertTrue(HeroMotion.trail(b) <= HeroMotion.trail(a), "only ever fading") }
    }

    @Test
    fun theWordsReadInOneStepAtATimeAsTheFigureSettles() {
        assertTrue(HeroMotion.dash(TEXT_AT_MS.toFloat()) > 0.985f, "settled, within a few px: ${HeroMotion.dash(TEXT_AT_MS.toFloat())}")
        (0 until STAGED_STEPS).forEach { step ->
            val starts = millis.first { HeroMotion.textProgress(it, step) > 0f } - 1f
            assertEquals((TEXT_AT_MS + step * TEXT_STAGGER_MS).toFloat(), starts, "step $step's turn")
            assertEquals(1f, HeroMotion.textProgress(starts + TEXT_MS, step), "and is in within $TEXT_MS ms")
        }
        assertTrue(TEXT_STAGGER_MS in 60..90 && TEXT_MS in 400..500)
        val plain = listOf(IntroStep.NAME, IntroStep.HEADLINE, IntroStep.PROOF, IntroStep.ACTIONS, IntroStep.SUMMARY)
        assertEquals((0 until TEXT_STEPS).toList(), plain.map { it.order(staged = false) }, "one step each, in reading order")
        assertEquals((0 until STAGED_STEPS).toList(), IntroStep.entries.map { it.order(staged = true) }, "the role too")
    }

    /** Nothing moves past the end, so a clock at [END_MS] is the still picture with every word shown. */
    @Test
    fun everythingIsAtRestByTheEndWhichIsWithinTheBudget() {
        assertTrue(SLIDE_MS <= END_MS)
        (0 until STAGED_STEPS).forEach { assertEquals(1f, HeroMotion.textProgress(END_MS.toFloat(), it)) }
        assertEquals(0f, HeroMotion.trail(END_MS.toFloat()))
        assertTrue(END_MS <= 1150, "the whole entrance takes about a second: $END_MS ms")
    }
}
