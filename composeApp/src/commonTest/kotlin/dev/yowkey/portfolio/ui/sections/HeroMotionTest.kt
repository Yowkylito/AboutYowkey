package dev.yowkey.portfolio.ui.sections

import dev.yowkey.portfolio.ui.sections.HeroMotion.DASH_MS
import dev.yowkey.portfolio.ui.sections.HeroMotion.ECHOES
import dev.yowkey.portfolio.ui.sections.HeroMotion.ECHO_LAG_MS
import dev.yowkey.portfolio.ui.sections.HeroMotion.END_MS
import dev.yowkey.portfolio.ui.sections.HeroMotion.IMPACT_AT_MS
import dev.yowkey.portfolio.ui.sections.HeroMotion.IMPACT_MS
import dev.yowkey.portfolio.ui.sections.HeroMotion.TEXT_AT_MS
import dev.yowkey.portfolio.ui.sections.HeroMotion.TEXT_MS
import dev.yowkey.portfolio.ui.sections.HeroMotion.TEXT_STAGGER_MS
import dev.yowkey.portfolio.ui.sections.HeroMotion.TEXT_STEPS
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The entrance's timeline: a dash that lands before the words come, a swell that runs out along the trail, and an end. */
class HeroMotionTest {

    private val millis = (0..END_MS).map { it.toFloat() }

    @Test
    fun theDashIsFastestAtOnceThenLandsRunsOnALittleAndSettles() {
        assertEquals(0f, HeroMotion.dash(-50f))
        assertEquals(0f, HeroMotion.dash(0f))
        assertEquals(1f, HeroMotion.speed(1f), 0.01f, "fastest at the start")
        assertTrue(HeroMotion.dash(120f) > 0.5f, "half way in within 120 ms: ${HeroMotion.dash(120f)}")
        val landing = millis.first { HeroMotion.dash(it) >= 1f }
        assertTrue(landing > IMPACT_AT_MS && landing <= TEXT_AT_MS, "lands as the swell peaks, before the words: $landing ms")
        val furthest = millis.maxOf { HeroMotion.dash(it) }
        assertTrue(furthest in 1.01f..1.04f, "runs on a little past its place: $furthest")
        millis.filter { it >= 850f }.forEach { assertTrue(abs(HeroMotion.dash(it) - 1f) < 0.004f, "settled by 850 ms") }
        assertEquals(1f, HeroMotion.dash(DASH_MS.toFloat()))
        assertEquals(0f, HeroMotion.speed(DASH_MS.toFloat()))
        millis.zipWithNext().filter { (a, _) -> a > 0f && a < landing }.forEach { (a, b) ->
            assertTrue(HeroMotion.dash(b) > HeroMotion.dash(a), "only ever forward until it lands")
            assertTrue(HeroMotion.speed(b) <= HeroMotion.speed(a), "and only ever slowing")
        }
    }

    @Test
    fun theLandingsSwellRunsOutAlongTheTrailAndIsQuietByTheEnd() {
        val peaks = (0..ECHOES).map { echo -> millis.maxBy { HeroMotion.landing(it, echo) } }
        peaks.zipWithNext().forEach { (front, behind) ->
            assertEquals(HeroMotion.RIPPLE_STEP_MS.toFloat(), behind - front, 1f, "each echo a ripple step after the one in front")
        }
        assertEquals(1f, HeroMotion.landing(peaks[0]), 0.01f, "the figure hears it in full")
        (0..ECHOES).forEach { echo ->
            assertEquals(0f, HeroMotion.landing(IMPACT_AT_MS.toFloat(), echo), "quiet before the landing")
            assertEquals(0f, HeroMotion.landing(END_MS.toFloat(), echo), "quiet at the end")
        }
        assertEquals(0f, HeroMotion.swell(0f))
        assertEquals(1f, HeroMotion.swell(HeroMotion.ATTACK))
        assertEquals(0f, HeroMotion.swell(1f))
    }

    @Test
    fun theWordsReadInOneStepAtATimeOnceTheFigureHasLanded() {
        val landing = millis.first { HeroMotion.dash(it) >= 1f }
        (0 until TEXT_STEPS).forEach { step ->
            val starts = millis.first { HeroMotion.textProgress(it, step) > 0f } - 1f
            assertEquals((TEXT_AT_MS + step * TEXT_STAGGER_MS).toFloat(), starts, "step $step's turn")
            assertTrue(starts >= landing, "step $step waits for the landing")
            assertEquals(1f, HeroMotion.textProgress(starts + TEXT_MS, step), "and is in within $TEXT_MS ms")
        }
        val plain = listOf(IntroStep.NAME, IntroStep.HEADLINE, IntroStep.PROOF, IntroStep.ACTIONS, IntroStep.SUMMARY)
        assertEquals((0 until TEXT_STEPS).toList(), plain.map { it.order(staged = false) }, "one step each, in reading order")
        assertEquals((0 until HeroMotion.STAGED_STEPS).toList(), IntroStep.entries.map { it.order(staged = true) }, "the role too")
    }

    /**
     * On the stage, the trail draws back after the landing has peaked, and the name waits until it is half way back;
     * then the role, then the rest below, one stagger apart.
     */
    @Test
    fun stagedTheTrailDrawsBackBeforeTheNameAndRoleReadIn() {
        val peak = millis.maxBy { HeroMotion.landing(it) }
        assertTrue(HeroMotion.DRAW_BACK_AT_MS > peak, "the impact first: peak at $peak ms")
        assertEquals(0f, HeroMotion.drawBack(HeroMotion.DRAW_BACK_AT_MS.toFloat()))
        assertEquals(1f, HeroMotion.drawBack((HeroMotion.DRAW_BACK_AT_MS + HeroMotion.DRAW_BACK_MS).toFloat()))
        val name = HeroMotion.textAt(IntroStep.NAME.order(true), staged = true).toFloat()
        assertTrue(HeroMotion.drawBack(name) in 0.3f..0.6f, "the name comes as the trail clears its place: ${HeroMotion.drawBack(name)}")
        IntroStep.entries.zipWithNext().forEach { (a, b) ->
            assertEquals(
                HeroMotion.NAME_STAGGER_MS,
                HeroMotion.textAt(b.order(true), staged = true) - HeroMotion.textAt(a.order(true), staged = true),
                "$b follows $a",
            )
        }
        assertTrue(HeroMotion.NAME_STAGGER_MS in 60..90)
    }

    /** Nothing moves past the end, so a clock at [END_MS] is the still picture with every word shown. */
    @Test
    fun everythingIsAtRestByTheEndWhichIsWithinTheBudget() {
        assertTrue(DASH_MS + ECHOES * ECHO_LAG_MS <= END_MS, "the farthest echo is home")
        assertTrue(IMPACT_AT_MS + IMPACT_MS <= END_MS, "the swell is spent")
        assertTrue(HeroMotion.DRAW_BACK_AT_MS + HeroMotion.DRAW_BACK_MS <= END_MS, "the trail is back")
        (0 until TEXT_STEPS).forEach { assertEquals(1f, HeroMotion.textProgress(END_MS.toFloat(), it, staged = false)) }
        (0 until HeroMotion.STAGED_STEPS).forEach { assertEquals(1f, HeroMotion.textProgress(END_MS.toFloat(), it, staged = true)) }
        assertTrue(END_MS in 900..1400, "the whole entrance takes about a second and a third: $END_MS ms")
    }
}
