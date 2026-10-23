package dev.yowkey.portfolio.data

import dev.yowkey.portfolio.model.FeedbackDraft
import dev.yowkey.portfolio.model.Portfolio
import dev.yowkey.portfolio.model.SiteNotes
import dev.yowkey.portfolio.model.VisitCount
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The site's own backend (`worker/`, a Cloudflare Worker over a D1 database): the visit count in the footer and
 * the notes left in the Contact section. Reached at [Portfolio.api]; when the content names none, the UI shows
 * neither and nothing here is called.
 */
interface SiteApi {
    /**
     * Counts this visit, once per visitor per day (the server tells repeats apart), and returns the running total.
     * @throws SiteApiException when the server cannot be reached or answers with an error
     */
    suspend fun recordVisit(api: String): VisitCount

    /**
     * The published notes and the rating they add up to, for the page.
     * @throws SiteApiException when the server cannot be reached or answers with an error
     */
    suspend fun loadNotes(api: String): SiteNotes

    /**
     * Puts [draft] in the owner's inbox.
     * @throws SiteApiException with a sentence for the visitor, when the draft is incomplete or the server refuses it
     */
    suspend fun sendFeedback(api: String, draft: FeedbackDraft)
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
 * [SiteApi] over HTTP, as the worker speaks it.
 *
 * @param countsVisits whether this platform's visits count: the browser's do; the desktop window is the owner's
 *   own, so it only reads the total
 */
class HttpSiteApi(private val transport: HttpTransport, private val countsVisits: Boolean) : SiteApi {

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

    override suspend fun loadNotes(api: String): SiteNotes {
        val reply = transport.send("GET", "$api/notes", null)
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

    /** The worker's `{ "error": "…" }`, written for the visitor; null for anything else. */
    private fun serverSentence(body: String): String? = try {
        json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content
    } catch (e: Exception) {
        null
    }
}
