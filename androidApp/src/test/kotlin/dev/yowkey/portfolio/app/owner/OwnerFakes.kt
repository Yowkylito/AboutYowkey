package dev.yowkey.portfolio.app.owner

import dev.yowkey.portfolio.admin.AdminApi
import dev.yowkey.portfolio.admin.AdminSignedOutException
import dev.yowkey.portfolio.admin.AdminSummary
import dev.yowkey.portfolio.admin.AdminTransport
import dev.yowkey.portfolio.data.HttpReply
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.coroutines.ContinuationInterceptor

private const val BASE = "https://worker.test"

/**
 * The worker's owner routes, faked: one unread message, canned answers, a log of what was asked, and a gate that can
 * hold the summary back after it has been read, as a slow network does.
 */
class FakeWorker : AdminTransport {
    /** "GET /admin/summary", "POST /admin/messages/1", …, in the order they were sent. */
    val requests = mutableListOf<String>()

    var unread = 1

    /** While set, a summary is read at once but answered only when this completes. */
    var summaryGate: CompletableDeferred<Unit>? = null

    /** The dispatcher each request was sent from; the answer is decoded there too. */
    val dispatchers = mutableListOf<ContinuationInterceptor?>()

    override suspend fun send(method: String, url: String, body: String?, headers: Map<String, String>): HttpReply {
        val path = url.removePrefix(BASE)
        requests += "$method $path"
        dispatchers += currentCoroutineContext()[ContinuationInterceptor]
        return when {
            path == "/admin/summary" -> {
                val answer = """{"day":"2026-10-24","messages":{"total":1,"unread":$unread}}"""
                summaryGate?.await()
                HttpReply(200, answer)
            }
            path.startsWith("/admin/messages?") -> HttpReply(200, """{"items":[$MESSAGE]}""".replace("READ", if (unread > 0) "null" else "\"2026-10-24T02:00:00Z\""))
            method == "POST" && path.startsWith("/admin/messages/") -> {
                if (body.orEmpty().contains("\"read\"")) unread = 0
                HttpReply(200, "{}")
            }
            path.startsWith("/admin/days") || path.startsWith("/admin/feedback") ||
                path.startsWith("/admin/notifications") || path.startsWith("/admin/reports") -> HttpReply(200, """{"items":[]}""")
            else -> HttpReply(404, """{"error":"No such route."}""")
        }
    }

    private companion object {
        const val MESSAGE =
            """{"id":1,"created_at":"2026-10-24T01:00:00Z","name":"Ana","contact":"ana@example.com","topic":"job","message":"Hello","read_at":READ}"""
    }
}

/** A session over [worker] that keeps its "token" in memory. */
class FakeAccess(private val worker: FakeWorker, signedIn: Boolean = true) : OwnerAccess {
    override val signedIn = MutableStateFlow(signedIn)

    /** The dispatcher each call that opens or writes the kept token ran on (the Keystore, on the phone). */
    val tokenDispatchers = mutableListOf<ContinuationInterceptor?>()

    override suspend fun api(): AdminApi {
        tokenDispatchers += currentCoroutineContext()[ContinuationInterceptor]
        if (!signedIn.value) throw AdminSignedOutException()
        return AdminApi(worker, BASE, "test-token")
    }

    override suspend fun signIn(password: String): AdminSummary {
        tokenDispatchers += currentCoroutineContext()[ContinuationInterceptor]
        val summary = AdminApi(worker, BASE, password).summary()
        signedIn.value = true
        return summary
    }

    override suspend fun signOut() {
        tokenDispatchers += currentCoroutineContext()[ContinuationInterceptor]
        signedIn.value = false
    }

    override fun expire() {
        signedIn.value = false
    }
}

class FakeDevice : OwnerDevice {
    var checks = 0
    var asked = false

    override fun pushInfo() = PushInfo(firebase = false, registered = false, notificationsAllowed = true)
    override fun shouldAskForNotifications() = !asked
    override fun askedForNotifications() {
        asked = true
    }

    override fun checkNow() {
        checks++
    }
}
