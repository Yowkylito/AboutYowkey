package dev.yowkey.portfolio.admin

import dev.yowkey.portfolio.data.HttpReply
import dev.yowkey.portfolio.data.SiteApiException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminApiTest {

    private data class Call(val method: String, val url: String, val body: String?, val headers: Map<String, String>)

    private class FakeTransport(var reply: HttpReply = HttpReply(200, """{"ok":true}""")) : AdminTransport {
        val calls = mutableListOf<Call>()
        var failure: Exception? = null
        override suspend fun send(method: String, url: String, body: String?, headers: Map<String, String>): HttpReply {
            calls += Call(method, url, body, headers)
            failure?.let { throw it }
            return reply
        }
    }

    private val base = "https://api.example/"

    private fun api(transport: FakeTransport) = AdminApi(transport, base, "s3cret")

    @Test
    fun everyCallCarriesTheBearerTokenAndNoTrailingSlashDoubles() = runTest {
        val transport = FakeTransport(HttpReply(200, SUMMARY))
        api(transport).summary()
        val call = transport.calls.single()
        assertEquals(Call("GET", "https://api.example/admin/summary", null, mapOf("Authorization" to "Bearer s3cret")), call)
    }

    @Test
    fun theSummaryReadsAsTheContractWritesIt() = runTest {
        val summary = api(FakeTransport(HttpReply(200, SUMMARY))).summary()
        assertEquals(
            AdminSummary(
                day = "2026-10-23",
                timeZone = "Asia/Manila",
                visits = VisitTotals(total = 120, today = 8, since = "2026-10-23"),
                clicks = listOf(LinkClicks("linkedin", 14, 2), LinkClicks("github", 9, 1)),
                notes = NoteTotals(total = 6, today = 1, published = 3),
                messages = MessageTotals(total = 4, today = 1, unread = 2, hiring = 3),
                push = PushStatus(configured = true, devices = 1),
            ),
            summary,
        )
        assertEquals(LinkClicks("linkedin", 14, 2), summary.clicksOn("linkedin"))
        assertEquals(LinkClicks("x", 0, 0), summary.clicksOn("x"), "an untracked link is zero, not a crash")
    }

    @Test
    fun theDaysComeWithTheirClicksAndCountries() = runTest {
        val transport = FakeTransport(HttpReply(200, DAYS))
        val days = api(transport).days(limit = 30)
        assertEquals("https://api.example/admin/days?limit=30", transport.calls.single().url)
        assertEquals(
            listOf(
                DayStats("2026-10-23", 8, mapOf("linkedin" to 2, "github" to 1), 1, 1, listOf(CountryCount("PH", 6), CountryCount("US", 2))),
                DayStats("2026-10-21", 3),
            ),
            days,
        )
    }

    @Test
    fun limitsAreKeptInsideWhatTheWorkerTakes() = runTest {
        val transport = FakeTransport(HttpReply(200, """{"items":[]}"""))
        api(transport).days(limit = 1000)
        api(transport).notes(limit = 0)
        api(transport).messages(limit = 9999)
        api(transport).reports(limit = 60)
        assertEquals(
            listOf(
                "https://api.example/admin/days?limit=366",
                "https://api.example/admin/feedback?limit=1",
                "https://api.example/admin/messages?limit=500",
                "https://api.example/admin/reports?limit=60",
            ),
            transport.calls.map { it.url },
        )
    }

    @Test
    fun notesReadTheirConsentWhetherNumberOrBoolean() = runTest {
        val notes = api(FakeTransport(HttpReply(200, NOTES))).notes()
        assertEquals(
            AdminNote(12, "2026-10-23T08:15:00.000Z", "Ada", "ada@example.com", 5, "Great work", true, "2026-10-23T09:00:00.000Z", null),
            notes[0],
        )
        assertFalse(notes[1].consent, "0 is no")
        assertTrue(notes[2].consent, "true is yes")
        assertNull(notes[1].name)
        assertTrue(notes[0].isPublic)
        assertFalse(notes[1].isPublic, "no consent: private for good")
        assertFalse(notes[2].isPublic, "hidden")
        assertTrue(notes[2].isHidden)
    }

    @Test
    fun messagesReadWithTheirTopicAndState() = runTest {
        val messages = api(FakeTransport(HttpReply(200, MESSAGES))).messages()
        assertEquals(
            AdminMessage(34, "2026-10-23T10:00:00.000Z", "Grace", "grace@example.com", "job", "Want to work with us?", null, null),
            messages[0],
        )
        with(messages[0]) {
            assertTrue(isHiring)
            assertFalse(isRead)
            assertEquals("Job", topicLabel)
            assertEquals("Grace", sender)
            assertEquals("grace@example.com", replyEmail)
        }
        with(messages[1]) {
            assertFalse(isHiring)
            assertTrue(isRead)
            assertTrue(isArchived)
            assertEquals("@linus on Telegram", sender, "no name: the contact is the heading")
            assertNull(replyEmail, "not an email, so no mail app")
            assertEquals("Collaboration", topicLabel)
        }
    }

    @Test
    fun actionsPostTheirWordToTheRightRecord() = runTest {
        val transport = FakeTransport()
        api(transport).act(note = 12, action = NoteAction.Publish)
        api(transport).act(message = 34, action = MessageAction.Archive)
        assertEquals(
            listOf(
                Call("POST", "https://api.example/admin/feedback/12", """{"action":"publish"}""", mapOf("Authorization" to "Bearer s3cret")),
                Call("POST", "https://api.example/admin/messages/34", """{"action":"archive"}""", mapOf("Authorization" to "Bearer s3cret")),
            ),
            transport.calls,
        )
        assertEquals(
            listOf("publish", "unpublish", "hide", "unhide", "delete"),
            NoteAction.entries.map { it.wire },
        )
        assertEquals(listOf("read", "unread", "archive", "unarchive", "delete"), MessageAction.entries.map { it.wire })
    }

    @Test
    fun theWorkersSentenceIsTheError() = runTest {
        val conflict = FakeTransport(HttpReply(409, """{"error":"The visitor did not agree to this note being shown."}"""))
        val e = assertFailsWith<AdminApiException> { api(conflict).act(note = 12, action = NoteAction.Publish) }
        assertEquals("The visitor did not agree to this note being shown.", e.message)
        assertEquals(409, e.status)

        val missing = assertFailsWith<AdminApiException> {
            api(FakeTransport(HttpReply(404, """{"error":"No such message."}"""))).act(message = 1, action = MessageAction.Read)
        }
        assertEquals("No such message.", missing.message)

        val bare = assertFailsWith<AdminApiException> { api(FakeTransport(HttpReply(502, "<html>Bad gateway</html>"))).summary() }
        assertEquals("The server answered HTTP 502. Try again later.", bare.message)
    }

    @Test
    fun a401IsItsOwnSignedOutError() = runTest {
        val transport = FakeTransport(HttpReply(401, """{"error":"Unauthorized"}"""))
        assertFailsWith<AdminSignedOutException> { api(transport).summary() }
        assertFailsWith<AdminSignedOutException> { api(transport).messages() }
        assertFailsWith<AdminSignedOutException> { api(transport).act(note = 1, action = NoteAction.Hide) }
        assertFailsWith<AdminSignedOutException> { api(transport).registerDevice("t") }
        val other = assertFailsWith<AdminApiException> { api(FakeTransport(HttpReply(403, "{}"))).summary() }
        assertFalse(other is AdminSignedOutException, "only 401 signs out")
    }

    @Test
    fun noConnectionIsAnOfflineErrorAndAnUnreadableReplyAnother() = runTest {
        val down = FakeTransport().apply { failure = SiteApiException("No connection: timeout") }
        val offline = assertFailsWith<AdminApiException> { api(down).summary() }
        assertTrue(offline.isOffline)
        assertEquals(AdminApi.OFFLINE, offline.message)

        val garbled = assertFailsWith<AdminApiException> { api(FakeTransport(HttpReply(200, "[1,2]"))).summary() }
        assertFalse(garbled.isOffline)
    }

    @Test
    fun theRecordsReadNewestFirstOrAfterACursor() = runTest {
        val transport = FakeTransport(HttpReply(200, NOTIFICATIONS))
        val records = api(transport).notifications()
        api(transport).notifications(after = 11, limit = 50)
        assertEquals(
            listOf("https://api.example/admin/notifications?limit=50", "https://api.example/admin/notifications?after=11&limit=50"),
            transport.calls.map { it.url },
        )
        assertEquals(
            listOf(
                AdminNotification(13, "2026-10-23T15:59:00.000Z", "daily", "Today: 8 visits", "LinkedIn 2 · GitHub 1 · 1 note · 1 message", null),
                AdminNotification(12, "2026-10-23T10:00:00.000Z", "message", "New message · Job", "Grace: Want to work with us?", 34),
            ),
            records,
        )
    }

    @Test
    fun theReportsReadAsTheCronWritesThem() = runTest {
        val reports = api(FakeTransport(HttpReply(200, REPORTS))).reports()
        assertEquals(
            listOf(DailyReport("2026-10-23", 8, mapOf("linkedin" to 2, "github" to 1), 1, 1, "2026-10-23T15:59:00.000Z")),
            reports,
        )
    }

    @Test
    fun thePhoneRegistersAndUnregistersItsPushToken() = runTest {
        val transport = FakeTransport()
        api(transport).registerDevice("fcm-token")
        api(transport).unregisterDevice("fcm-token")
        assertEquals(
            listOf(
                Call("POST", "https://api.example/admin/devices", """{"token":"fcm-token","platform":"android"}""", mapOf("Authorization" to "Bearer s3cret")),
                Call("DELETE", "https://api.example/admin/devices", """{"token":"fcm-token"}""", mapOf("Authorization" to "Bearer s3cret")),
            ),
            transport.calls,
        )
    }

    @Test
    fun aPushsStringDataBecomesARecord() {
        assertEquals(
            AdminNotification(12, "", "message", "New message · Job", "Grace: Hi", 34),
            AdminNotification.fromPushData(mapOf("id" to "12", "kind" to "message", "title" to "New message · Job", "body" to "Grace: Hi", "ref_id" to "34")),
        )
        assertNull(AdminNotification.fromPushData(mapOf("kind" to "daily")), "no id, nothing to show")
        assertNull(AdminNotification.fromPushData(mapOf("id" to "13", "kind" to "daily", "ref_id" to ""))!!.refId)
        assertNull(AdminNotification.fromPushData(mapOf("id" to "13", "ref_id" to "null"))!!.refId)
    }

    @Test
    fun theTransportsPlainSendIsAHeaderlessSend() = runTest {
        val transport = FakeTransport()
        transport.send("GET", "https://api.example/visits", null)
        assertEquals(Call("GET", "https://api.example/visits", null, emptyMap()), transport.calls.single())
    }

    private companion object {
        const val SUMMARY = """{ "day": "2026-10-23", "timeZone": "Asia/Manila",
            "visits": { "total": 120, "today": 8, "since": "2026-10-23" },
            "clicks": [ { "link": "linkedin", "total": 14, "today": 2 }, { "link": "github", "total": 9, "today": 1 } ],
            "notes": { "total": 6, "today": 1, "published": 3 },
            "messages": { "total": 4, "today": 1, "unread": 2, "hiring": 3 },
            "push": { "configured": true, "devices": 1 }, "added": "later" }"""

        const val DAYS = """{ "items": [
            { "day": "2026-10-23", "visits": 8, "clicks": { "linkedin": 2, "github": 1 }, "notes": 1, "messages": 1,
              "countries": [ { "country": "PH", "count": 6 }, { "country": "US", "count": 2 } ] },
            { "day": "2026-10-21", "visits": 3, "clicks": {}, "notes": 0, "messages": 0, "countries": [] } ] }"""

        const val NOTES = """{ "items": [
            { "id": 12, "created_at": "2026-10-23T08:15:00.000Z", "name": "Ada", "contact": "ada@example.com", "rating": 5,
              "message": "Great work", "consent": 1, "published_at": "2026-10-23T09:00:00.000Z", "hidden_at": null },
            { "id": 11, "created_at": "2026-10-22T08:15:00.000Z", "name": null, "contact": null, "rating": null,
              "message": "Hi", "consent": 0, "published_at": null, "hidden_at": null },
            { "id": 10, "created_at": "2026-10-21T08:15:00.000Z", "name": "Bo", "contact": "", "rating": 3,
              "message": "Nice", "consent": true, "published_at": null, "hidden_at": "2026-10-22T01:00:00.000Z" } ] }"""

        const val MESSAGES = """{ "items": [
            { "id": 34, "created_at": "2026-10-23T10:00:00.000Z", "name": "Grace", "contact": "grace@example.com", "topic": "job",
              "message": "Want to work with us?", "read_at": null, "archived_at": null },
            { "id": 33, "created_at": "2026-10-22T10:00:00.000Z", "name": "", "contact": "@linus on Telegram", "topic": "collaboration",
              "message": "Let's build something", "read_at": "2026-10-22T11:00:00.000Z", "archived_at": "2026-10-22T12:00:00.000Z" } ] }"""

        const val NOTIFICATIONS = """{ "items": [
            { "id": 13, "created_at": "2026-10-23T15:59:00.000Z", "kind": "daily", "title": "Today: 8 visits",
              "body": "LinkedIn 2 · GitHub 1 · 1 note · 1 message", "ref_id": null },
            { "id": 12, "created_at": "2026-10-23T10:00:00.000Z", "kind": "message", "title": "New message · Job",
              "body": "Grace: Want to work with us?", "ref_id": 34 } ] }"""

        const val REPORTS = """{ "items": [ { "day": "2026-10-23", "visits": 8, "clicks": { "linkedin": 2, "github": 1 },
            "notes": 1, "messages": 1, "created_at": "2026-10-23T15:59:00.000Z" } ] }"""
    }
}
