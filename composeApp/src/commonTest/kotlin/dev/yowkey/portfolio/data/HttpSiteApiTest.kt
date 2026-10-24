package dev.yowkey.portfolio.data

import dev.yowkey.portfolio.model.FeedbackDraft
import dev.yowkey.portfolio.model.MessageDraft
import dev.yowkey.portfolio.model.PublicNote
import dev.yowkey.portfolio.model.RatingSummary
import dev.yowkey.portfolio.model.Relation
import dev.yowkey.portfolio.model.SiteNotes
import dev.yowkey.portfolio.model.VisitCount
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HttpSiteApiTest {

    private class FakeTransport(var reply: HttpReply) : HttpTransport {
        val calls = mutableListOf<Triple<String, String, String?>>()
        override suspend fun send(method: String, url: String, body: String?): HttpReply {
            calls += Triple(method, url, body)
            return reply
        }
    }

    private val api = "https://api.example"

    @Test
    fun aBrowserCountsItsVisitAndTheDesktopOnlyReads() = runTest {
        val transport = FakeTransport(HttpReply(200, """{"total": 42, "today": 3, "since": "2026-10-24", "extra": true}"""))
        assertEquals(VisitCount(42, 3, "2026-10-24"), HttpSiteApi(transport, countsVisits = true).recordVisit(api))
        assertEquals(Triple("POST", "https://api.example/visits", null), transport.calls.single())

        assertEquals(VisitCount(42, 3, "2026-10-24"), HttpSiteApi(transport, countsVisits = false).recordVisit(api))
        assertEquals("GET", transport.calls.last().first)
    }

    @Test
    fun aBadVisitReplyIsAnException() = runTest {
        assertFailsWith<SiteApiException> { HttpSiteApi(FakeTransport(HttpReply(500, "boom")), true).recordVisit(api) }
        assertFailsWith<SiteApiException> { HttpSiteApi(FakeTransport(HttpReply(200, "<html>")), true).recordVisit(api) }
    }

    @Test
    fun thePublishedNotesAreReadWithAPlainGet() = runTest {
        val transport = FakeTransport(
            HttpReply(200, """{"visits":{"total":3},"ratings":{"count":1,"average":5},"notes":[{"id":7,"name":"Ada","rating":5,"message":"Great","date":"2026-10-24"}]}"""),
        )
        val notes = HttpSiteApi(transport, true).loadNotes(api)
        assertEquals(Triple("GET", "https://api.example/notes", null), transport.calls.single())
        assertEquals(SiteNotes(VisitCount(3), RatingSummary(1, 5.0), listOf(PublicNote(7, "Ada", 5, "Great", "2026-10-24"))), notes)
        assertFailsWith<SiteApiException> { HttpSiteApi(FakeTransport(HttpReply(503, "down")), true).loadNotes(api) }
        assertFailsWith<SiteApiException> { HttpSiteApi(FakeTransport(HttpReply(200, "[]")), true).loadNotes(api) }
    }

    @Test
    fun theNotesAskForAStarARelationAPageInAndPastTheCacheAndReadTheRatingStarByStar() = runTest {
        val reply = """{"visits":{"total":3},"ratings":{"count":4,"average":4.5,"stars":{"1":0,"2":0,"3":1,"4":0,"5":3},"private":1},""" +
            """"notes":[{"id":9,"rating":5,"message":"Great"}],"total":3,"more":true}"""
        val transport = FakeTransport(HttpReply(200, reply))
        val notes = HttpSiteApi(transport, true).loadNotes(api, stars = 5, relation = "colleague", offset = 12)
        assertEquals("https://api.example/notes?stars=5&relation=colleague&offset=12", transport.calls.single().second)
        assertEquals(3, notes.ratings.countOf(5))
        assertEquals(0.75f, notes.ratings.shareOf(5))
        assertEquals("25%", notes.ratings.percentOf(3))
        assertEquals(1, notes.ratings.keptPrivate)
        assertEquals(3, notes.total)
        assertEquals(true, notes.more)

        HttpSiteApi(transport, true).loadNotes(api, stars = 4, relation = " Friend ", offset = 0)
        assertEquals("https://api.example/notes?stars=4&relation=friend", transport.calls.last().second, "a first page names no offset; the relation goes lowercase")
        HttpSiteApi(transport, true).loadNotes(api, relation = " ", offset = 24)
        assertEquals("https://api.example/notes?offset=24", transport.calls.last().second, "a blank relation is none")
        HttpSiteApi(transport, true).loadNotes(api, fresh = true)
        assertTrue(Regex("""https://api\.example/notes\?fresh=\d+""").matches(transport.calls.last().second), transport.calls.last().second)
        HttpSiteApi(transport, true).loadNotes(api, stars = 5, relation = "other", offset = 12, fresh = true)
        val last = transport.calls.last().second
        assertTrue(Regex("""https://api\.example/notes\?stars=5&relation=other&offset=12&fresh=\d+""").matches(last), last)
    }

    /** The contract's reply: each note's relation and pin, the private ratings star by star, the sorter's counts. */
    @Test
    fun theNotesReadTheirRelationPinAndTheSortersCounts() = runTest {
        val reply = """{"visits":{"total":3},"ratings":{"count":4,"average":5,"stars":{"1":0,"2":0,"3":0,"4":0,"5":4},"private":3,""" +
            """"privateStars":{"1":0,"2":0,"3":0,"4":0,"5":3}},"notes":[{"id":7,"name":"Akkram","rating":5,"message":"Solid",""" +
            """"date":"2026-10-23","relation":"colleague","pinned":true},{"id":6,"message":"Hi","relation":null}],"total":2,"more":false,""" +
            """"relations":{"colleague":1,"friend":0,"special":0,"other":1}}"""
        val notes = HttpSiteApi(FakeTransport(HttpReply(200, reply)), true).loadNotes(api, stars = 5)
        assertEquals(PublicNote(7, "Akkram", 5, "Solid", "2026-10-23", relation = "colleague", pinned = true), notes.notes.first())
        assertEquals(PublicNote(6, message = "Hi"), notes.notes.last(), "an older note: no relation, not pinned")
        assertEquals(3, notes.ratings.privateCountOf(5))
        assertEquals(0, notes.ratings.privateCountOf(4))
        assertEquals(1, notes.countOf(Relation.Colleague))
        assertEquals(0, notes.countOf(Relation.Special))
        assertEquals(1, notes.countOf(Relation.Other))
        val bare = HttpSiteApi(FakeTransport(HttpReply(200, """{"visits":{"total":1}}""")), true).loadNotes(api)
        assertEquals(0, bare.countOf(Relation.Friend), "a reply without counts counts none")
        assertEquals(0, bare.ratings.privateCountOf(5))
    }

    @Test
    fun aNoteIsAlwaysSentAsPublic() = runTest {
        val transport = FakeTransport(HttpReply(201, """{"ok":true}"""))
        HttpSiteApi(transport, true).sendFeedback(api, FeedbackDraft(message = "Hi"))
        assertEquals("""{"message":"Hi","consent":true}""", transport.calls.single().third, "the form says it is public; the default is sent too")
    }

    @Test
    fun aNoteSaysHowItsWriterKnowsTheOwnerInLowercaseOrNotAtAll() = runTest {
        val transport = FakeTransport(HttpReply(201, """{"ok":true}"""))
        HttpSiteApi(transport, true).sendFeedback(api, FeedbackDraft(message = "Hi", relation = " Friend "))
        assertEquals("""{"message":"Hi","consent":true,"relation":"friend"}""", transport.calls.single().third)
        HttpSiteApi(transport, true).sendFeedback(api, FeedbackDraft(message = "Hi", relation = "  "))
        assertEquals("""{"message":"Hi","consent":true}""", transport.calls.last().third, "not saying is left out")
        val e = assertFailsWith<SiteApiException> { HttpSiteApi(transport, true).sendFeedback(api, FeedbackDraft(message = "Hi", relation = "boss")) }
        assertEquals("Pick one of the ways you know me.", e.message)
        assertEquals(2, transport.calls.size, "an unknown relation never leaves")
    }

    @Test
    fun aNoteGoesAsTrimmedJsonAndSucceedsOn201() = runTest {
        val transport = FakeTransport(HttpReply(201, """{"ok":true}"""))
        HttpSiteApi(transport, true).sendFeedback(api, FeedbackDraft(" Ada ", "", 5, " Hi ", consent = true))
        val (method, url, body) = transport.calls.single()
        assertEquals("POST", method)
        assertEquals("https://api.example/feedback", url)
        assertEquals("""{"name":"Ada","rating":5,"message":"Hi","consent":true}""", body, "fields left at their default are left out")
    }

    @Test
    fun theServersSentenceIsTheExceptionsMessage() = runTest {
        val refused = FakeTransport(HttpReply(429, """{"error":"That is plenty for one day, thank you. Try again tomorrow."}"""))
        val e = assertFailsWith<SiteApiException> { HttpSiteApi(refused, true).sendFeedback(api, FeedbackDraft(message = "x")) }
        assertEquals("That is plenty for one day, thank you. Try again tomorrow.", e.message)

        val gateway = FakeTransport(HttpReply(502, "Bad gateway"))
        val plain = assertFailsWith<SiteApiException> { HttpSiteApi(gateway, true).sendFeedback(api, FeedbackDraft(message = "x")) }
        assertEquals("The note could not be sent (HTTP 502). Try again, or email me.", plain.message)
    }

    /** What the browser's fetch says when it cannot connect, as `FetchTransport` hands it on. */
    private class Unreachable : HttpTransport {
        override suspend fun send(method: String, url: String, body: String?): HttpReply =
            throw SiteApiException("No connection: TypeError: Failed to fetch")
    }

    /** A server that takes the request and never answers; the browser's fetch has no timeout of its own. */
    private class Silent : HttpTransport {
        override suspend fun send(method: String, url: String, body: String?): HttpReply = awaitCancellation()
    }

    /** The form shows the exception's message, so it is the form's sentence (the static page's too), not the browser's. */
    @Test
    fun aNoteOrMessageThatCannotLeaveSaysSoInTheFormsWords() = runTest {
        val note = assertFailsWith<SiteApiException> { HttpSiteApi(Unreachable(), true).sendFeedback(api, FeedbackDraft(message = "x")) }
        assertEquals("The note could not be sent. Check the connection and try again.", note.message)
        val message = assertFailsWith<SiteApiException> {
            HttpSiteApi(Unreachable(), true).sendMessage(api, MessageDraft(contact = "a@b.c", message = "x"))
        }
        assertEquals("The message could not be sent. Check the connection and try again.", message.message)
    }

    /** Else "Sending…" (and its locked button) or "Loading…" (and "Show more") would wait for good. */
    @Test
    fun aServerThatNeverAnswersIsGivenUpOn() = runTest {
        val note = assertFailsWith<SiteApiException> { HttpSiteApi(Silent(), true).sendFeedback(api, FeedbackDraft(message = "x")) }
        assertEquals("The note could not be sent. Check the connection and try again.", note.message)
        assertEquals(20_000, currentTime, "20 s: past the desktop's and the phone's own timeouts, which say it first there")
        assertFailsWith<SiteApiException> { HttpSiteApi(Silent(), true).sendMessage(api, MessageDraft(contact = "a@b.c", message = "x")) }
        assertFailsWith<SiteApiException> { HttpSiteApi(Silent(), true).loadNotes(api) }
        assertFailsWith<SiteApiException> { HttpSiteApi(Silent(), true).recordVisit(api) }
        assertFailsWith<SiteApiException> { HttpSiteApi(Silent(), true).trackClick(api, "github") }
    }

    @Test
    fun anIncompleteDraftNeverLeaves() = runTest {
        val transport = FakeTransport(HttpReply(201, "{}"))
        val e = assertFailsWith<SiteApiException> { HttpSiteApi(transport, true).sendFeedback(api, FeedbackDraft(message = " ")) }
        assertEquals("Write a message first.", e.message)
        assertEquals(emptyList(), transport.calls)
    }

    private class FakeBeacon(var accepts: Boolean = true) : Beacon {
        val sent = mutableListOf<Pair<String, String>>()
        override fun send(url: String, body: String): Boolean {
            sent += url to body
            return accepts
        }
    }

    @Test
    fun aClickIsPostedAsItsLinksName() = runTest {
        val transport = FakeTransport(HttpReply(200, """{"ok":true,"counted":true}"""))
        HttpSiteApi(transport, countsVisits = true).trackClick(api, "linkedin")
        assertEquals(Triple("POST", "https://api.example/clicks", """{"link":"linkedin"}"""), transport.calls.single())
    }

    @Test
    fun theDesktopCountsNoClicks() = runTest {
        val transport = FakeTransport(HttpReply(200, "{}"))
        val beacon = FakeBeacon()
        HttpSiteApi(transport, countsVisits = false, beacon = beacon).trackClick(api, "github")
        assertEquals(emptyList(), transport.calls, "the owner's own window is not a visitor")
        assertEquals(emptyList(), beacon.sent)
    }

    /** In the browser the click leaves as a beacon, so a link that takes the tab away cannot cancel it. */
    @Test
    fun aClickGoesByBeaconWhereThereIsOneAndByPostWhenItIsRefused() = runTest {
        val transport = FakeTransport(HttpReply(200, "{}"))
        val beacon = FakeBeacon()
        HttpSiteApi(transport, countsVisits = true, beacon = beacon).trackClick(api, "github")
        assertEquals(listOf("https://api.example/clicks" to """{"link":"github"}"""), beacon.sent)
        assertEquals(emptyList(), transport.calls, "nothing goes twice")

        beacon.accepts = false
        HttpSiteApi(transport, countsVisits = true, beacon = beacon).trackClick(api, "github")
        assertEquals(Triple("POST", "https://api.example/clicks", """{"link":"github"}"""), transport.calls.single())
    }

    @Test
    fun aRefusedClickIsAnException() = runTest {
        val refused = FakeTransport(HttpReply(400, """{"error":"Not a tracked link."}"""))
        assertFailsWith<SiteApiException> { HttpSiteApi(refused, true).trackClick(api, "linkedin") }
    }

    @Test
    fun aMessageGoesAsTrimmedJsonWithItsTopicAndSucceedsOn201() = runTest {
        val transport = FakeTransport(HttpReply(201, """{"ok":true}"""))
        HttpSiteApi(transport, true).sendMessage(api, MessageDraft(" Ada ", " ada@example.com ", "job", " Hiring? "))
        val (method, url, body) = transport.calls.single()
        assertEquals("POST", method)
        assertEquals("https://api.example/messages", url)
        assertEquals("""{"name":"Ada","contact":"ada@example.com","topic":"job","message":"Hiring?"}""", body)

        HttpSiteApi(transport, true).sendMessage(api, MessageDraft(contact = "@ada", message = "Hi"))
        assertEquals("""{"contact":"@ada","topic":"other","message":"Hi"}""", transport.calls.last().third, "no topic is filed as other")
    }

    @Test
    fun theDesktopSendsMessagesToo() = runTest {
        val transport = FakeTransport(HttpReply(201, "{}"))
        HttpSiteApi(transport, countsVisits = false).sendMessage(api, MessageDraft(contact = "a@b.c", message = "Hi"))
        assertEquals("https://api.example/messages", transport.calls.single().second)
    }

    @Test
    fun anIncompleteMessageNeverLeaves() = runTest {
        val transport = FakeTransport(HttpReply(201, "{}"))
        val noContact = assertFailsWith<SiteApiException> { HttpSiteApi(transport, true).sendMessage(api, MessageDraft(message = "Hi")) }
        assertEquals("Leave an email or another way to reach you.", noContact.message)
        val noWords = assertFailsWith<SiteApiException> { HttpSiteApi(transport, true).sendMessage(api, MessageDraft(contact = "a@b.c")) }
        assertEquals("Write a message first.", noWords.message)
        val badTopic = assertFailsWith<SiteApiException> {
            HttpSiteApi(transport, true).sendMessage(api, MessageDraft(contact = "a@b.c", topic = "spam", message = "Hi"))
        }
        assertEquals("Pick one of the topics.", badTopic.message)
        assertEquals(emptyList(), transport.calls)
    }

    @Test
    fun aRefusedMessageCarriesTheServersSentence() = runTest {
        val draft = MessageDraft(contact = "a@b.c", message = "Fourth")
        val limited = FakeTransport(HttpReply(429, """{"error":"That is plenty for one day, thank you. Try again tomorrow."}"""))
        val e = assertFailsWith<SiteApiException> { HttpSiteApi(limited, true).sendMessage(api, draft) }
        assertEquals("That is plenty for one day, thank you. Try again tomorrow.", e.message)

        val gateway = FakeTransport(HttpReply(502, "Bad gateway"))
        val plain = assertFailsWith<SiteApiException> { HttpSiteApi(gateway, true).sendMessage(api, draft) }
        assertEquals("The message could not be sent (HTTP 502). Try again, or email me.", plain.message)
    }
}
