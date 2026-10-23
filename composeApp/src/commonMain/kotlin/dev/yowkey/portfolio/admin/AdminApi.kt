package dev.yowkey.portfolio.admin

import dev.yowkey.portfolio.data.HttpReply
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The worker's owner routes (`/admin/…`), for the owner's phone: the figures, the notes and messages and what can be
 * done to them, the alerts' records, the daily reports and the phone's push registration. Every call carries
 * `Authorization: Bearer <token>`, where the token is the worker's `ADMIN_TOKEN`, which the owner types in as the
 * password. One instance per token; signing in with another means a new one.
 *
 * @param baseUrl the worker, [dev.yowkey.portfolio.model.Portfolio.api] from the bundled content
 * @throws AdminSignedOutException from any call the worker answers 401: the token is wrong or was changed
 * @throws AdminApiException from any other failure, with a sentence for the owner
 */
class AdminApi(
    private val transport: AdminTransport,
    baseUrl: String,
    token: String,
) {
    private val base = baseUrl.trimEnd('/')
    private val headers = mapOf("Authorization" to "Bearer $token")

    suspend fun summary(): AdminSummary = get("/admin/summary", AdminSummary.serializer())

    /** Newest first, one per day that has any data. */
    suspend fun days(limit: Int = 30): List<DayStats> =
        get("/admin/days?limit=${limit.coerceIn(1, 366)}", Items.serializer(DayStats.serializer())).items

    /** Every note, newest first, published or not. */
    suspend fun notes(limit: Int = 100): List<AdminNote> =
        get("/admin/feedback?limit=${limit.coerceIn(1, 500)}", Items.serializer(AdminNote.serializer())).items

    /**
     * @throws AdminApiException with the worker's sentence: no such note (404), or no consent to publish, or a pin
     *   on a note that is not on the site, hidden from the inbox page meanwhile, say (409)
     */
    suspend fun act(note: Long, action: NoteAction) {
        send("POST", "/admin/feedback/$note", AdminJson.encodeToString(ActionBody.serializer(), ActionBody(action.wire)))
    }

    /** Every message, newest first, archived ones included. */
    suspend fun messages(limit: Int = 100): List<AdminMessage> =
        get("/admin/messages?limit=${limit.coerceIn(1, 500)}", Items.serializer(AdminMessage.serializer())).items

    suspend fun act(message: Long, action: MessageAction) {
        send("POST", "/admin/messages/$message", AdminJson.encodeToString(ActionBody.serializer(), ActionBody(action.wire)))
    }

    /**
     * The alerts' records. Without [after], the newest first; with it, only the newer ones, oldest first, the order
     * the backup check shows them in.
     */
    suspend fun notifications(after: Long? = null, limit: Int = 50): List<AdminNotification> {
        val query = buildString {
            append("?")
            if (after != null) append("after=").append(after).append("&")
            append("limit=").append(limit.coerceIn(1, 200))
        }
        return get("/admin/notifications$query", Items.serializer(AdminNotification.serializer())).items
    }

    /** The 23:59 reports, newest first. */
    suspend fun reports(limit: Int = 60): List<DailyReport> =
        get("/admin/reports?limit=${limit.coerceIn(1, 366)}", Items.serializer(DailyReport.serializer())).items

    /** Tells the worker where to push: this phone's FCM [token]. Safe to repeat. */
    suspend fun registerDevice(token: String) {
        send("POST", "/admin/devices", AdminJson.encodeToString(DeviceBody.serializer(), DeviceBody(token, "android")))
    }

    /** Stops the pushes to [token], on sign-out. */
    suspend fun unregisterDevice(token: String) {
        send("DELETE", "/admin/devices", AdminJson.encodeToString(DeviceBody.serializer(), DeviceBody(token)))
    }

    private suspend fun <T> get(path: String, serializer: KSerializer<T>): T {
        val reply = send("GET", path, null)
        return try {
            AdminJson.decodeFromString(serializer, reply.body)
        } catch (e: Exception) {
            throw AdminApiException("The server's answer could not be read. Update the app, or try again later.", reply.status, e)
        }
    }

    private suspend fun send(method: String, path: String, body: String?): HttpReply {
        val reply = try {
            transport.send(method, base + path, body, headers)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AdminApiException(OFFLINE, status = 0, cause = e)
        }
        if (reply.isSuccess) return reply
        if (reply.status == 401) throw AdminSignedOutException()
        throw AdminApiException(serverSentence(reply.body) ?: "The server answered HTTP ${reply.status}. Try again later.", reply.status)
    }

    /** The worker's `{ "error": "…" }`; null for anything else. */
    private fun serverSentence(body: String): String? = try {
        AdminJson.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }

    companion object {
        const val OFFLINE = "Can't reach the server. Check the connection and try again."
    }
}

/**
 * A failed owner call, with a sentence for the owner. [status] is the HTTP status, 0 when nothing came back.
 */
open class AdminApiException(message: String, val status: Int = 0, cause: Throwable? = null) : Exception(message, cause) {
    val isOffline: Boolean get() = status == 0
}

/** The worker refused the token (401): it is wrong, or `ADMIN_TOKEN` was changed. The app signs out. */
class AdminSignedOutException : AdminApiException("Signed out: the server no longer accepts this password.", 401)

internal val AdminJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}
