package dev.yowkey.portfolio.ui.sections

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** When the name and role stand on the stage, how big, and what of the role; never under the figure, never cut. */
class HeroStageTest {

    private val name = "Rodelito Tangonan"
    private val role = "Android Developer at DigiPlus since 2024"

    /** Every character half an em wide, so the arithmetic below can be checked by hand. */
    private val halfEm = TextWidth { text, size, _ -> text.length * size * 0.5f }

    @Test
    fun aPhonesStageHoldsTheNameAndTheWholeRole() {
        val plan = assertNotNull(HeroStage.plan(name, role, 372f, 168f, 1f, SystemFontWidth), "a 412dp phone")
        assertEquals(HeroStage.Plan(30f, 15f, role, wholeRole = true), plan)
        val iphone = assertNotNull(HeroStage.plan(name, role, 350f, 168f, 1f, SystemFontWidth), "a 390dp phone")
        assertEquals(HeroStage.Plan(30f, 14f, role, wholeRole = true), iphone, "the role a size down rather than cut")
        assertEquals(36f, assertNotNull(HeroStage.plan(name, role, 480f, 208f, 1f, SystemFontWidth)).nameSp, "a tablet's stage")
    }

    @Test
    fun aNarrowStageOrHugeTextKeepsTheNameUnderIt() {
        assertNull(HeroStage.plan(name, role, 320f, 168f, 1f, SystemFontWidth), "a 360dp phone")
        assertNull(HeroStage.plan(name, role, 372f, 168f, 1.3f, SystemFontWidth), "a 412dp phone at 1.3x text")
        assertNull(HeroStage.plan("Bartholomew Featherstonehaugh-Cholmondeley", role, 372f, 168f, 1f, SystemFontWidth), "a long word")
        assertNull(HeroStage.plan(name, "", 372f, 168f, 1f, SystemFontWidth), "no role")
    }

    /** A whole role wins over a bigger name; past that, the longest shorter form that fits, never a broken word. */
    @Test
    fun theRoleIsWholeIfItCanBeAndElseItsLongestFormThatFits() {
        // A 130dp box: at 15dp (7.5dp a letter) "Android Developer", "at DigiPlus since", "2024" is 3 lines, one too
        // many, and at 14dp too; "Android Developer" then "at DigiPlus" is 2. The name then has room at 30sp only.
        val stage = 130f + HeroStage.FIGURE_INSET_DP + 168f * HeroStage.FIGURE_MAX_WIDTH + HeroStage.GAP_TO_FIGURE_DP + HeroStage.PAD_DP
        val plan = assertNotNull(HeroStage.plan("Ada Lovelace", role, stage, 168f, 1f, halfEm))
        assertEquals(HeroStage.Plan(30f, 15f, "Android Developer at DigiPlus", wholeRole = false), plan)
        assertEquals(3, HeroStage.lines(role, 15f, false, 130f, halfEm))
        assertEquals(3, HeroStage.lines(role, 14f, false, 130f, halfEm))
        assertEquals(2, HeroStage.lines("Android Developer at DigiPlus", 15f, false, 130f, halfEm))
        assertNull(HeroStage.lines("Supercalifragilistic", 15f, false, 130f, halfEm), "a word wider than the box")
        assertEquals(1, HeroStage.lines("Ada", 30f, true, 130f, halfEm))
    }

    /**
     * The box is the stage less the figure's box (as wide as any figure is drawn), a gap and the inset: whatever the
     * photo, the name never meets it.
     */
    @Test
    fun theBoxStopsAGapShortOfAnyFigure() {
        listOf(320f, 372f, 480f).forEach { width ->
            listOf(168f, 208f).forEach { height ->
                val boxEnd = HeroStage.PAD_DP + HeroStage.boxWidth(width, height)
                // The selfie's 0.845, a square crop, a head-and-shoulders 1.05 and 1.1, and wider still.
                listOf(0.6f, 0.845f, 1f, 1.05f, 1.1f, 1.4f).forEach { aspect ->
                    val (figureWidth, figureHeight) = HeroStage.figureSize(height, aspect)
                    val figureStart = width - HeroStage.FIGURE_INSET_DP - figureWidth
                    assertTrue(figureStart - boxEnd >= HeroStage.GAP_TO_FIGURE_DP - 0.01f, "$aspect on $width x $height")
                    assertTrue(figureHeight <= height + 0.01f && figureWidth <= height * HeroStage.FIGURE_MAX_WIDTH + 0.01f)
                    assertEquals(aspect, figureWidth / figureHeight, 0.001f, "never stretched")
                }
            }
        }
        assertEquals(168f, HeroStage.figureSize(168f, 0.845f).second, "a narrow cut-out stands the stage's full height")
        val headshot = HeroStage.figureSize(168f, 1.1f).second
        assertTrue(headshot < 168f && 168f - headshot < 168f * HeroStage.STAGE_TOP, "a wide one smaller, its head still over the stage's top")
        assertTrue(HeroStage.boxHeight(168f) + 2 * HeroStage.V_PAD_DP <= 168f * (1 - HeroStage.STAGE_TOP) + 0.01f, "inside the tint")
    }

    /**
     * The static page's estimate is never narrower than the fonts it stands for: the widest that Chromium measured, in
     * ems, of system-ui (SF Pro), Arial and Helvetica at the sizes used: bold at 30px for the name (Arial's), medium at
     * 14px for the role (SF Pro's text cut).
     */
    @Test
    fun theEstimateIsAtLeastAsWideAsTheUsualFonts() {
        mapOf("Tangonan" to 4.704f, "Rodelito" to 4f, "Rodelito Tangonan" to 8.981f).forEach { (words, measured) ->
            assertTrue(SystemFontWidth.width(words, 1f, bold = true) >= measured, "$words: ${SystemFontWidth.width(words, 1f, true)} em")
        }
        mapOf(
            "Android Developer at" to 9.956f, "DigiPlus since 2024" to 9.342f, "Android Developer at DigiPlus" to 14.059f,
            "Android Developer" to 8.771f, "Engineer at Acme" to 8.268f,
        ).forEach { (words, measured) ->
            assertTrue(SystemFontWidth.width(words, 1f, bold = false) >= measured, "$words: ${SystemFontWidth.width(words, 1f, false)} em")
        }
    }

    @Test
    fun theRoleIsTheHeadlinesLeadWithItsSpacingEvenedOut() {
        assertEquals(role, HeroStage.roleOf("Android Developer at DigiPlus since 2024 · Kotlin, Jetpack Compose"))
        assertEquals("Engineer at Acme", HeroStage.roleOf("  Engineer   at Acme  "))
    }
}
