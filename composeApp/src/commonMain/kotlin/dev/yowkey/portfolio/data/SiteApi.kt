package dev.yowkey.portfolio.data

import dev.yowkey.portfolio.model.FeedbackDraft
import dev.yowkey.portfolio.model.MessageDraft
import dev.yowkey.portfolio.model.Portfolio
import dev.yowkey.portfolio.model.SiteNotes
import dev.yowkey.portfolio.model.VisitCount
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.random.Random

/**
 * The site's own backend (`worker/`, a Cloudflare Worker over a D1 database): the visit count, the clicks on the
 * profile's LinkedIn and GitHub links, the notes left in the Contact section and the private messages.
 * Reached at [Portfolio.api]; when the content names none, the UI shows none of it and nothing here is called.
 */
interface SiteApi {
    /**
     * Counts this visit, once per visitor per day (the server tells repeats apart), and returns the running total.
     * @throws SiteApiException when the server cannot be reached or answers with an error
     */
    suspend fun recordVisit(api: String): VisitCount

    /**
     * The rating, star by star, and a page of the public notes, pinned first, then newest first: all of them, or only
     * [stars]'s, and of those only [relation]'s (a [dev.yowkey.portfolio.model.Relation]'s wire value; `other` takes
     * in the notes that say nothing too); the page starting [offset] notes in, for the next one. By offset, not
     * after an id, because the pinned notes break the order by id. [fresh] skips the browser's copy (the backend
     * lets it keep one for 15 seconds), for a list a visitor has just added a note to.
     * @throws SiteApiException when the server cannot be reached or answers with an error
     */
    suspend fun loadNotes(
        api: String,
        stars: Int? = null,
        relation: String? = null,
        offset: Int = 0,
        fresh: Boolean = false,
    ): SiteNotes

    /**
     * Puts [draft] in the owner's inbox.
     * @throws SiteApiException with a sentence for the visitor, when the draft is incomplete or the server refuses it
     */
    suspend fun sendFeedback(api: String, draft: FeedbackDraft)

    /**
     * Counts a click on the tracked profile link [link] (its `trackingKey`, such as "linkedin"); the server counts it
     * once per visitor per day. Nothing waits on it: the link has already opened.
     * @throws SiteApiException when the server cannot be reached or answers with an error
     */
    suspend fun trackClick(api: String, link: String)

    /**
     * Sends [draft] to the owner, privately: unlike a note it is never shown on the site.
     * @throws SiteApiException with a sentence for the visitor, when the draft is incomplete or the server refuses it
     */
    suspend fun sendMessage(api: String, draft: MessageDraft)
}

class SiteApiException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** One HTTP exchange, as each platform makes it: `fetch` on the web, `java.net.http` on the desktop. */
fun interface HttpTransport {
    /**
     * Sends [body] (JSON, when not null) and returns whatever came back, whatever its status.
     * @throws SiteApiException when nothing came back at all
     */
    suspend fun send(method: String, url: String, body: String?): HttpReply
}

data class HttpReply(val status: Int, val body: String) {
    val isSuccess: Boolean get() = status in 200..299
}

/**
 * A small POST the platform delivers on its own, even when the page goes away the moment after: the browser's
 * `navigator.sendBeacon`. The body goes as `text/plain`, which needs no CORS preflight, and the worker reads it as
 * JSON. Fire and forget, so there is no reply to read.
 */
fun interface Beacon {
    /** Queues [body] for [url]; false when the platform would not take it, and the caller sends it the usual way. */
    fun send(url: String, body: String): Boolean
}

/**
 * [SiteApi] over HTTP, as the worker speaks it.
 *
 * @param countsVisits whether this platform's visits and clicks count: the browser's do; the desktop window is the
 *   owner's own, so it only reads the total and counts no clicks
 * @param beacon how clicks leave, where the platform has one (the browser), so that one sent as a link opens is not
 *   lost with the page; without one they go through [transport] like everything else
 */
class HttpSiteApi(
    private val transport: HttpTransport,
    private val countsVisits: Boolean,
    private val beacon: Beacon? = null,
) : SiteApi {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun recordVisit(api: String): VisitCount {
        val reply = transport.send(if (countsVisits) "POST" else "GET", "$api/visits", null)
        if (!reply.isSuccess) throw SiteApiException("visits: HTTP ${reply.status}")
        return try {
            json.decodeFromString(VisitCount.serializer(), reply.body)
        } catch (e: Exception) {
            throw SiteApiException("visits: unreadable reply", e)
        }
    }

    override suspend fun loadNotes(api: String, stars: Int?, relation: String?, offset: Int, fresh: Boolean): SiteNotes {
        val query = listOfNotNull(
            stars?.let { "stars=$it" },
            relation?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { "relation=$it" },
            // Left out at 0: each first page keeps a single address, and so a single cached copy.
            offset.takeIf { it > 0 }?.let { "offset=$it" },
            // A new value each time, so no copy of an earlier answer, from this page load or the last, can match.
            if (fresh) "fresh=${Random.nextInt(1, Int.MAX_VALUE)}" else null,
        )
        val reply = transport.send("GET", "$api/notes" + query.joinToString("&", prefix = "?").takeIf { query.isNotEmpty() }.orEmpty(), null)
        if (!reply.isSuccess) throw SiteApiException("notes: HTTP ${reply.status}")
        return try {
            json.decodeFromString(SiteNotes.serializer(), reply.body)
        } catch (e: Exception) {
            throw SiteApiException("notes: unreadable reply", e)
        }
    }

    override suspend fun sendFeedback(api: String, draft: FeedbackDraft) {
        draft.problems.firstOrNull()?.let { throw SiteApiException(it) }
        val body = json.encodeToString(FeedbackDraft.serializer(), draft.normalized())
        val reply = transport.send("POST", "$api/feedback", body)
        if (reply.isSuccess) return
        throw SiteApiException(serverSentence(reply.body) ?: "The note could not be sent (HTTP ${reply.status}). Try again, or email me.")
    }

    override suspend fun trackClick(api: String, link: String) {
        if (!countsVisits) return
        val url = "$api/clicks"
        val body = buildJsonObject { put("link", link) }.toString()
        if (beacon?.send(url, body) == true) return
        val reply = transport.send("POST", url, body)
        if (!reply.isSuccess) throw SiteApiException("clicks: HTTP ${reply.status}")
    }

    override suspend fun sendMessage(api: String, draft: MessageDraft) {
        draft.problems.firstOrNull()?.let { throw SiteApiException(it) }
        val body = json.encodeToString(MessageDraft.serializer(), draft.normalized())
        val reply = transport.send("POST", "$api/messages", body)
        if (reply.isSuccess) return
        throw SiteApiException(serverSentence(reply.body) ?: "The message could not be sent (HTTP ${reply.status}). Try again, or email me.")
    }

    /** The worker's `{ "error": "…" }`, written for the visitor; null for anything else. */
    private fun serverSentence(body: String): String? = try {
        json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content
    } catch (e: Exception) {
        null
    }
}
