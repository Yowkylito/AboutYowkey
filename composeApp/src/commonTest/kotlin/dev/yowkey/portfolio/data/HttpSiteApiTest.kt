package dev.yowkey.portfolio.data

import dev.yowkey.portfolio.model.FeedbackDraft
import dev.yowkey.portfolio.model.MessageDraft
import dev.yowkey.portfolio.model.PublicNote
import dev.yowkey.portfolio.model.RatingSummary
import dev.yowkey.portfolio.model.SiteNotes
import dev.yowkey.portfolio.model.VisitCount
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
    fun theNotesAskForAStarAPageAfterAndPastTheCacheAndReadTheRatingStarByStar() = runTest {
        val reply = """{"visits":{"total":3},"ratings":{"count":4,"average":4.5,"stars":{"1":0,"2":0,"3":1,"4":0,"5":3},"private":1},""" +
            """"notes":[{"id":9,"rating":5,"message":"Great"}],"total":3,"more":true}"""
        val transport = FakeTransport(HttpReply(200, reply))
        val notes = HttpSiteApi(transport, true).loadNotes(api, stars = 5, before = 12)
        assertEquals("https://api.example/notes?stars=5&before=12", transport.calls.single().second)
        assertEquals(3, notes.ratings.countOf(5))
        assertEquals(0.75f, notes.ratings.shareOf(5))
        assertEquals("25%", notes.ratings.percentOf(3))
        assertEquals(1, notes.ratings.keptPrivate)
        assertEquals(3, notes.total)
        assertEquals(true, notes.more)

        HttpSiteApi(transport, true).loadNotes(api, fresh = true)
        assertTrue(Regex("""https://api\.example/notes\?fresh=\d+""").matches(transport.calls.last().second), transport.calls.last().second)
    }

    @Test
    fun aNoteIsAlwaysSentAsPublic() = runTest {
        val transport = FakeTransport(HttpReply(201, """{"ok":true}"""))
        HttpSiteApi(transport, true).sendFeedback(api, FeedbackDraft(message = "Hi"))
        assertEquals("""{"message":"Hi","consent":true}""", transport.calls.single().third, "the form says it is public; the default is sent too")
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
