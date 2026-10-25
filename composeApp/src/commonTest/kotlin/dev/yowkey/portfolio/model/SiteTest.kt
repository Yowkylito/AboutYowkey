package dev.yowkey.portfolio.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        assertEquals("5.0 from 1 rating", RatingSummary(1, 5.0).summary)
        assertEquals("4.8 from 12 ratings", RatingSummary(12, 4.75).summary)
        assertEquals("4.5 from 2 ratings", RatingSummary(2, 4.5).summary)
    }

    @Test
    fun oneDecimalRoundsTheSameEverywhere() {
        assertEquals(listOf("5.0", "4.8", "4.5", "4.0", "1.3", "0.1"), listOf(5.0, 4.75, 4.5, 3.96, 1.25, 0.05).map(::oneDecimal))
    }

    @Test
    fun aNotesCardNamesItsWriterDatesItInFullAndBadgesOnlyAKnownRelation() {
        val note = PublicNote(1, "Akkram", 5, "Solid", "2026-10-23", relation = "colleague", pinned = true)
        assertEquals("Akkram", note.displayName)
        assertEquals("Oct 23, 2026", note.dateLine)
        assertEquals(Relation.Colleague, note.badge)
        assertEquals(PublicNote.ANONYMOUS, PublicNote(2, " ", null, "Hi").displayName, "a blank name is no name")
        assertNull(PublicNote(2, null, null, "Hi").dateLine)
        assertNull(PublicNote(3, null, null, "Hi", "soon").dateLine, "an unreadable date is left out")
        assertEquals(Relation.Special, PublicNote(4, null, null, "Hi", relation = " Special ").badge)
        listOf(null, "other", "Other", "boss", "").forEach { assertNull(PublicNote(5, null, null, "Hi", relation = it).badge, "$it") }
    }

    @Test
    fun fullDateReadsOnlyADate() {
        assertEquals("Oct 23, 2026", fullDate("2026-10-23"))
        assertEquals("Jan 5, 2027", fullDate("2027-01-05"), "no leading zero on the day")
        listOf("2026-13-01", "2026-00-01", "2026-1-01", "2026-10-23T10:00", "yesterday", "").forEach { assertNull(fullDate(it), it) }
    }

    @Test
    fun theRelationsAreTheBackendsFourWhateverTheirCase() {
        assertEquals(listOf("colleague", "friend", "special", "other"), Relation.entries.map { it.wire })
        assertEquals(listOf("Colleague", "Friend", "Special someone", "Other"), Relation.entries.map { it.label })
        assertEquals(listOf("Colleagues", "Friends", "Special someone", "Others"), Relation.entries.map { it.plural })
        assertEquals(Relation.Friend, Relation.of(" FRIEND "))
        assertEquals(Relation.Colleague, Relation.of("Colleague"))
        listOf(null, "", " ", "friends", "boss").forEach { assertNull(Relation.of(it), "$it") }
    }

    @Test
    fun theSheetsWordsCountTheirNotesAndRatings() {
        assertEquals("Show the 5-star notes", FeedbackCopy.openSheet(5))
        assertEquals("1-star notes", FeedbackCopy.sheetTitle(1))
        assertEquals("4 ratings · 1 note to read", FeedbackCopy.sheetSummary(4, 1))
        assertEquals("1 rating · 0 notes to read", FeedbackCopy.sheetSummary(1, 0))
        assertEquals(
            "+1 private 5-star rating: sent before notes were public, so only its stars are here.",
            FeedbackCopy.privateInSheet(1, 5),
        )
        assertEquals(
            "+3 private 4-star ratings: sent before notes were public, so only their stars are here.",
            FeedbackCopy.privateInSheet(3, 4),
        )
        assertEquals("No notes with 5 stars yet.", FeedbackCopy.noneInSheet(null, 5))
        assertEquals("No notes from colleagues with 1 star yet.", FeedbackCopy.noneInSheet(Relation.Colleague, 1))
        assertEquals("No notes from a special someone with 4 stars yet.", FeedbackCopy.noneInSheet(Relation.Special, 4))
    }

    @Test
    fun theRatingCountsItsPrivateWordsStarByStar() {
        val ratings = RatingSummary(4, 5.0, mapOf("5" to 4), keptPrivate = 3, privateStars = mapOf("5" to 3))
        assertEquals(3, ratings.privateCountOf(5))
        assertEquals(0, ratings.privateCountOf(2))
        val notes = SiteNotes(VisitCount(1), ratings, relations = mapOf("colleague" to 1, "other" to 0))
        assertEquals(1, notes.countOf(Relation.Colleague))
        assertEquals(0, notes.countOf(Relation.Friend), "a relation the reply leaves out has none")
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
    fun theProofHoldsOnlyWhatExists() {
        assertTrue(LiveStats().isEmpty, "no answer yet")
        assertTrue(LiveStats(VisitCount(0), RatingSummary()).isEmpty, "answers with nothing in them")
        assertTrue(LiveStats(VisitCount(0), RatingSummary(count = 2, average = null)).isEmpty, "a count without an average is no rating")

        val both = LiveStats(VisitCount(1200, since = "2026-10-23"), RatingSummary(6, 4.7))
        assertEquals(4.7, both.average)
        assertEquals(6, both.ratingCount)
        assertEquals(1200L, both.visitTotal)
        assertTrue(both.hasRating && both.hasVisits && !both.isEmpty)

        val visitsOnly = LiveStats(VisitCount(1200), RatingSummary())
        assertNull(visitsOnly.average)
        assertEquals(0, visitsOnly.ratingCount)
        assertTrue(visitsOnly.hasVisits && !visitsOnly.hasRating)

        val ratingOnly = LiveStats(VisitCount(0), RatingSummary(5, 5.0))
        assertTrue(ratingOnly.hasRating && !ratingOnly.hasVisits)
        assertEquals(0L, LiveStats(VisitCount(-3)).visitTotal, "never fewer than none")
    }

    /** Too few to count as proof shows nothing: one 5.0 or 73 visits would argue against the page. */
    @Test
    fun theProofWaitsUntilItsNumbersAreBigEnough() {
        val early = LiveStats(VisitCount(LiveStats.MIN_VISITS - 1), RatingSummary(LiveStats.MIN_RATINGS - 1, 5.0))
        assertTrue(early.isEmpty, "4 ratings and 999 visits: the bar says where the owner is instead")
        assertNull(early.average)
        assertEquals(0, early.ratingCount)
        val enough = LiveStats(VisitCount(LiveStats.MIN_VISITS), RatingSummary(LiveStats.MIN_RATINGS, 4.6))
        assertTrue(enough.hasRating && enough.hasVisits)
    }

    @Test
    fun theProofIsSettledOnceBothAnswersAreIn() {
        assertFalse(LiveStats().settled)
        assertFalse(LiveStats(visits = VisitCount(5)).settled, "the notes are still on their way")
        assertFalse(LiveStats(ratings = RatingSummary(1, 5.0)).settled, "the visits are still on their way")
        assertTrue(LiveStats(VisitCount(0), RatingSummary()).settled, "answered, if with nothing")
    }

    @Test
    fun theProofsNumbersCountUpFromZero() {
        assertEquals("0.0", ProofCopy.average(4.8, 0f))
        assertEquals("2.4", ProofCopy.average(4.8, 0.5f))
        assertEquals("4.8", ProofCopy.average(4.75))
        assertEquals("5.0", ProofCopy.average(5.0, 1f))
        assertEquals("5.0", ProofCopy.average(5.0, 3f), "never past the value")
        assertEquals("0", ProofCopy.visits(1234, 0f))
        assertEquals("617", ProofCopy.visits(1234, 0.5f))
        assertEquals("1,234", ProofCopy.visits(1234))
        assertEquals("1,234,567", ProofCopy.visits(1234567, 1f))
        assertEquals("0", ProofCopy.visits(35, -1f), "never under 0")
        val steps = (0..100).map { ProofCopy.visits(35, it / 100f).toLong() }
        assertEquals(steps.sorted(), steps, "it only ever counts up")
    }

    @Test
    fun theProofsWordsAgreeWithTheirNumbers() {
        assertEquals("· 1 rating", ProofCopy.ratingCount(1))
        assertEquals("· 14 ratings", ProofCopy.ratingCount(14))
        assertEquals("visit", ProofCopy.visitsWord(1))
        assertEquals("visits", ProofCopy.visitsWord(35))
    }

    @Test
    fun theProofIsReadAsOneSentence() {
        assertEquals(
            "Rated 4.8 out of 5 from 14 ratings, 1,234 visits. Read the reviews.",
            ProofCopy.label(LiveStats(VisitCount(1234), RatingSummary(14, 4.75))),
        )
        assertEquals("Rated 4.0 out of 5 from 6 ratings. Read the reviews.", ProofCopy.label(LiveStats(VisitCount(35), RatingSummary(6, 4.0))))
        assertEquals("1,001 visits. See the notes.", ProofCopy.label(LiveStats(VisitCount(1001), RatingSummary(1, 5.0))))
        assertNull(ProofCopy.label(LiveStats(VisitCount(35), RatingSummary(1, 5.0))), "too little to say")
        assertNull(ProofCopy.label(LiveStats(VisitCount(0), RatingSummary())))
        assertNull(ProofCopy.label(LiveStats()))
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

    /** The backend's order: the relation is checked after the rating. Not saying is fine; anything unknown is not. */
    @Test
    fun aDraftsRelationIsOneOfTheFourOrNone() {
        assertEquals(emptyList(), FeedbackDraft(message = "x", relation = " Friend ").problems)
        assertEquals(emptyList(), FeedbackDraft(message = "x", relation = "  ").problems)
        assertEquals(listOf("Pick one of the ways you know me."), FeedbackDraft(message = "x", relation = "boss").problems)
        assertEquals(
            listOf("Write a message first.", "The rating is 1 to 5.", "Pick one of the ways you know me."),
            FeedbackDraft(rating = 6, message = "", relation = "family").problems,
        )
    }

    @Test
    fun normalizedTrimsEveryFieldAndKeepsTheConsent() {
        assertEquals(FeedbackDraft("Ada", "a@b.c", 3, "Hi", consent = true), FeedbackDraft(" Ada ", " a@b.c ", 3, "\nHi\n", consent = true).normalized())
        assertEquals("colleague", FeedbackDraft(message = "Hi", relation = " Colleague ").normalized().relation, "the relation lowercase")
        assertEquals("", FeedbackDraft(message = "Hi", relation = " ").normalized().relation)
    }

    @Test
    fun ratingLabelsRunFromPoorToExcellent() {
        assertEquals(listOf("1 · Poor", "2 · Weak", "3 · Fine", "4 · Good", "5 · Excellent"), (1..5).map(FeedbackCopy::ratingLabel))
    }

    @Test
    fun aMessageNeedsWordsAndAWayToReplyAndKeepsToTheLimits() {
        assertEquals(emptyList(), MessageDraft(contact = "ada@example.com", message = "Hi").problems)
        assertEquals(emptyList(), MessageDraft("Ada", "@ada on LinkedIn", "freelance", "x".repeat(MessageDraft.MAX_MESSAGE)).problems)
        assertEquals(listOf("Leave an email or another way to reach you."), MessageDraft(contact = "  ", message = "Hi").problems)
        assertEquals(listOf("Write a message first."), MessageDraft(contact = "a@b.c", message = " \n ").problems)
        assertEquals(
            listOf("Keep the message under ${MessageDraft.MAX_MESSAGE} characters."),
            MessageDraft(contact = "a@b.c", message = "x".repeat(MessageDraft.MAX_MESSAGE + 1)).problems,
        )
        assertEquals(listOf("Pick one of the topics."), MessageDraft(contact = "a@b.c", topic = "spam", message = "Hi").problems)
        assertEquals(emptyList(), MessageDraft(contact = "a@b.c", topic = " ", message = "Hi").problems, "a blank topic is none")
    }

    /** Every sentence the worker can answer with, in its order: the message, the contact, the name, then the topic. */
    @Test
    fun aMessagesProblemsComeInTheBackendsOrder() {
        val nothing = MessageDraft("n".repeat(MessageDraft.MAX_NAME + 1), "", "nope", "")
        assertEquals(
            listOf(
                "Write a message first.",
                "Leave an email or another way to reach you.",
                "Keep the name under ${MessageDraft.MAX_NAME} characters.",
                "Pick one of the topics.",
            ),
            nothing.problems,
        )
        val tooMuch = MessageDraft("n".repeat(81), "c".repeat(MessageDraft.MAX_CONTACT + 1), "job", "x".repeat(2001))
        assertEquals(
            listOf("Keep the message under 2000 characters.", "Keep the email under 160 characters.", "Keep the name under 80 characters."),
            tooMuch.problems,
        )
        assertEquals(
            listOf("Keep the message under 2000 characters.", "Leave an email or another way to reach you."),
            MessageDraft(message = "x".repeat(2001)).problems,
        )
    }

    @Test
    fun aNormalizedMessageIsTrimmedAndFilesNoTopicAsOther() {
        assertEquals(MessageDraft("Ada", "a@b.c", "job", "Hi"), MessageDraft(" Ada ", " a@b.c ", " job ", "\nHi\n").normalized())
        assertEquals("other", MessageDraft(contact = "a@b.c", message = "Hi").normalized().topic)
    }

    @Test
    fun theTopicsAreTheBackendsFourWithTheFormsLabels() {
        assertEquals(listOf("job", "freelance", "collaboration", "other"), MessageTopic.entries.map { it.wire })
        assertEquals(listOf("A job", "A freelance project", "Collaboration", "Something else"), MessageTopic.entries.map { it.label })
        assertEquals(MessageTopic.Collaboration, MessageTopic.of("collaboration"))
        listOf(null, "", "Job", "hiring").forEach { assertNull(MessageTopic.of(it), "$it") }
    }

    @Test
    fun onlyLinkedInAndGitHubAreTrackedByTheirLabel() {
        assertEquals("linkedin", Link("LinkedIn", "https://www.linkedin.com/in/ada").trackingKey)
        assertEquals("github", Link(" GitHub ", "https://github.com/ada").trackingKey)
        listOf("Email", "Source on GitHub", "Résumé").forEach { assertNull(Link(it, "https://example.com").trackingKey, it) }
    }
}
