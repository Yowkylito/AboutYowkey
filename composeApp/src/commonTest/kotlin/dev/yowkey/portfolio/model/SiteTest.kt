package dev.yowkey.portfolio.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun theRatingSummaryNeedsAPublishedRating() {
        assertNull(RatingSummary().summary)
        assertNull(RatingSummary(count = 3, average = null).summary)
        assertEquals("5.0 from 1 note", RatingSummary(1, 5.0).summary)
        assertEquals("4.8 from 12 notes", RatingSummary(12, 4.75).summary)
        assertEquals("4.5 from 2 notes", RatingSummary(2, 4.5).summary)
    }

    @Test
    fun oneDecimalRoundsTheSameEverywhere() {
        assertEquals(listOf("5.0", "4.8", "4.5", "4.0", "1.3", "0.1"), listOf(5.0, 4.75, 4.5, 3.96, 1.25, 0.05).map(::oneDecimal))
    }

    @Test
    fun aPublicNoteNamesItsVisitorOrNotAndItsMonth() {
        assertEquals("Ada · Oct 2026", PublicNote(1, "Ada", 5, "Great", "2026-10-24").byLine)
        assertEquals("A visitor · Oct 2026", PublicNote(2, null, null, "Fine", "2026-10-24").byLine)
        assertEquals("A visitor", PublicNote(3, "  ", 4, "Ok").byLine, "a blank name and no date")
    }

    @Test
    fun theNotesReplyDecodesWithRoomForMore() {
        val json = """{"visits":{"total":9,"today":2,"since":"2026-10-23"},"ratings":{"count":2,"average":4.5},
            "notes":[{"id":4,"name":null,"rating":null,"message":"No stars","date":"2026-10-25","extra":1}],"later":true}"""
        val notes = Json { ignoreUnknownKeys = true }.decodeFromString(SiteNotes.serializer(), json)
        assertEquals(VisitCount(9, 2, "2026-10-23"), notes.visits)
        assertEquals(RatingSummary(2, 4.5), notes.ratings)
        assertEquals(listOf(PublicNote(4, null, null, "No stars", "2026-10-25")), notes.notes)
    }

    @Test
    fun theLiveLineSaysOnlyWhatExists() {
        assertTrue(LiveStats().isEmpty)
        assertTrue(LiveStats(VisitCount(0), RatingSummary()).isEmpty)
        val both = LiveStats(VisitCount(12, since = "2026-10-23"), RatingSummary(3, 4.7))
        assertEquals("4.7 from 3 notes", both.ratingLine)
        assertEquals("12 visits since Oct 2026", both.visitsLine)
        assertNull(LiveStats(VisitCount(12)).ratingLine)
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
        assertEquals(emptyList(), FeedbackDraft("Ada", "ada@example.com", 5, "x".repeat(FeedbackDraft.MAX_MESSAGE), consent = true).problems)
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
    fun normalizedTrimsEveryFieldAndKeepsTheConsent() {
        assertEquals(FeedbackDraft("Ada", "a@b.c", 3, "Hi", consent = true), FeedbackDraft(" Ada ", " a@b.c ", 3, "\nHi\n", consent = true).normalized())
    }

    @Test
    fun ratingLabelsRunFromPoorToExcellent() {
        assertEquals(listOf("1 · Poor", "2 · Weak", "3 · Fine", "4 · Good", "5 · Excellent"), (1..5).map(FeedbackCopy::ratingLabel))
    }
}
