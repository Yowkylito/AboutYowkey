package dev.yowkey.portfolio.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SiteTest {

    @Test
    fun theVisitSummaryGroupsDigitsAndNamesTheFirstMonth() {
        assertNull(VisitCount(0).summary, "nothing counted, nothing said")
        assertEquals("1 visit", VisitCount(1).summary)
        assertEquals("1,234 visits since Oct 2026", VisitCount(1234, 7, "2026-10-24").summary)
        assertEquals("1,234,567 visits", VisitCount(1234567, since = "someday").summary, "an unreadable date is left out")
        assertEquals("999 visits since Jan 2027", VisitCount(999, since = "2027-01-01").summary)
    }

    @Test
    fun digitsAreGroupedInThrees() {
        val values = listOf(0L, 12L, 123L, 1234L, 12345L, 123456L, 1234567L)
        assertEquals(listOf("0", "12", "123", "1,234", "12,345", "123,456", "1,234,567"), values.map(::groupDigits))
    }

    @Test
    fun monthAndYearReadsOnlyADate() {
        assertEquals("Dec 2026", monthAndYear("2026-12-05"))
        listOf("2026-13-01", "2026-00-01", "2026-1-01", "yesterday", "").forEach { assertNull(monthAndYear(it), it) }
    }

    @Test
    fun aDraftNeedsAMessageAndKeepsToTheLimits() {
        assertEquals(emptyList(), FeedbackDraft(message = "Hi").problems)
        assertEquals(emptyList(), FeedbackDraft("Ada", "ada@example.com", 5, "x".repeat(FeedbackDraft.MAX_MESSAGE)).problems)
        assertEquals(listOf("Write a message first."), FeedbackDraft(message = "  \n ").problems)
        assertEquals(
            listOf("Keep the message under ${FeedbackDraft.MAX_MESSAGE} characters."),
            FeedbackDraft(message = "x".repeat(FeedbackDraft.MAX_MESSAGE + 1)).problems,
        )
        val everything = FeedbackDraft("n".repeat(FeedbackDraft.MAX_NAME + 1), "c".repeat(FeedbackDraft.MAX_CONTACT + 1), 6, "")
        assertEquals(
            listOf(
                "Write a message first.",
                "Keep the name under ${FeedbackDraft.MAX_NAME} characters.",
                "Keep the email under ${FeedbackDraft.MAX_CONTACT} characters.",
                "The rating is 1 to 5.",
            ),
            everything.problems,
            "message first: it is what the form shows",
        )
        assertEquals(listOf("The rating is 1 to 5."), FeedbackDraft(rating = 0, message = "x").problems)
    }

    @Test
    fun normalizedTrimsEveryField() {
        assertEquals(FeedbackDraft("Ada", "a@b.c", 3, "Hi"), FeedbackDraft(" Ada ", " a@b.c ", 3, "\nHi\n").normalized())
    }

    @Test
    fun ratingLabelsRunFromPoorToExcellent() {
        assertEquals(listOf("1 · Poor", "2 · Weak", "3 · Fine", "4 · Good", "5 · Excellent"), (1..5).map(FeedbackCopy::ratingLabel))
    }
}
