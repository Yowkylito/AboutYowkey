package dev.yowkey.portfolio.data

import dev.yowkey.portfolio.model.FeedbackDraft
import dev.yowkey.portfolio.model.VisitCount
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
    fun aNoteGoesAsTrimmedJsonAndSucceedsOn201() = runTest {
        val transport = FakeTransport(HttpReply(201, """{"ok":true}"""))
        HttpSiteApi(transport, true).sendFeedback(api, FeedbackDraft(" Ada ", "", 5, " Hi "))
        val (method, url, body) = transport.calls.single()
        assertEquals("POST", method)
        assertEquals("https://api.example/feedback", url)
        assertEquals("""{"name":"Ada","rating":5,"message":"Hi"}""", body, "fields left at their default are left out")
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
}
