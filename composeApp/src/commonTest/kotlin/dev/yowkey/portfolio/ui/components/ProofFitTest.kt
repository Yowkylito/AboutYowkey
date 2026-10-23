package dev.yowkey.portfolio.ui.components

import dev.yowkey.portfolio.ui.components.ProofPart.Average
import dev.yowkey.portfolio.ui.components.ProofPart.Divider
import dev.yowkey.portfolio.ui.components.ProofPart.Eye
import dev.yowkey.portfolio.ui.components.ProofPart.RatingCount
import dev.yowkey.portfolio.ui.components.ProofPart.Star
import dev.yowkey.portfolio.ui.components.ProofPart.Stars
import dev.yowkey.portfolio.ui.components.ProofPart.Visits
import dev.yowkey.portfolio.ui.components.ProofPart.VisitsWord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProofFitTest {

    // Roughly the chip's widths in dp: the muted words are the widest pieces, then the four extra stars.
    private val widths = mapOf(
        Stars to 71, Star to 13, Average to 28, RatingCount to 72, Divider to 21, Eye to 15, Visits to 40, VisitsWord to 41,
    )
    private val width: (ProofPart) -> Int = { widths.getValue(it) }

    @Test
    fun theFormsNarrowByDroppingTheWordsThenFourStars() {
        assertEquals(
            listOf(
                listOf(Stars, Average, RatingCount, Divider, Eye, Visits, VisitsWord),
                listOf(Stars, Average, Divider, Eye, Visits),
                listOf(Star, Average, Divider, Eye, Visits),
            ),
            ProofFit.forms(hasRating = true, hasVisits = true),
        )
    }

    @Test
    fun oneSegmentHasNoHairline() {
        assertEquals(
            listOf(listOf(Stars, Average, RatingCount), listOf(Stars, Average), listOf(Star, Average)),
            ProofFit.forms(hasRating = true, hasVisits = false),
        )
        assertEquals(
            listOf(listOf(Eye, Visits, VisitsWord), listOf(Eye, Visits)),
            ProofFit.forms(hasRating = false, hasVisits = true),
            "the visits have no stars to give up, so two forms",
        )
        assertTrue(ProofFit.forms(hasRating = false, hasVisits = false).isEmpty())
    }

    @Test
    fun theWidestFormThatFitsIsShown() {
        val forms = ProofFit.forms(hasRating = true, hasVisits = true)
        assertEquals(forms[0], ProofFit.fit(forms, 400, width), "a wide bar: everything")
        assertEquals(forms[0], ProofFit.fit(forms, 288, width), "exactly as wide as everything")
        assertEquals(forms[1], ProofFit.fit(forms, 287, width), "a pixel short: the words go first")
        assertEquals(forms[1], ProofFit.fit(forms, 175, width))
        assertEquals(forms[2], ProofFit.fit(forms, 174, width), "then four of the stars")
        assertEquals(forms[2], ProofFit.fit(forms, 40, width), "nothing fits: the shortest, to be clipped")
        assertTrue(ProofFit.fit(emptyList(), 400, width).isEmpty())
    }
}
