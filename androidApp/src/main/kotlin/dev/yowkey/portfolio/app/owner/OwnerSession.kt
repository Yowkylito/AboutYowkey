package dev.yowkey.portfolio.app.owner

import android.content.Context
import dev.yowkey.portfolio.admin.AdminApi
import dev.yowkey.portfolio.admin.AdminApiException
import dev.yowkey.portfolio.admin.AdminSignedOutException
import dev.yowkey.portfolio.admin.AdminSummary
import dev.yowkey.portfolio.admin.AdminTransport
import dev.yowkey.portfolio.app.push.AlertNotifier
import dev.yowkey.portfolio.app.push.PollWorker
import dev.yowkey.portfolio.app.push.PushRegistration
import dev.yowkey.portfolio.data.PortfolioRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeout

/**
 * The owner being signed in, and everything that goes with it: the backup check scheduled, the phone registered
 * for push, the alerts on screen. The server is [dev.yowkey.portfolio.model.Portfolio.api] from the bundled
 * portfolio.json, the same worker the guest view talks to.
 */
class OwnerSession(
    private val context: Context,
    private val store: OwnerStore,
    private val transport: AdminTransport,
    private val repository: PortfolioRepository,
    private val notifier: AlertNotifier,
) {
    val signedIn: StateFlow<Boolean> = store.signedIn

    @Volatile
    private var base: String? = null

    /** The owner's routes, with the kept token. @throws AdminSignedOutException when there is none */
    suspend fun api(): AdminApi {
        val token = store.token() ?: throw AdminSignedOutException()
        return AdminApi(transport, baseUrl(), token)
    }

    /**
     * Checks [password] (the worker's `ADMIN_TOKEN`) with `GET /admin/summary` and, when the worker takes it, keeps it.
     * @throws AdminSignedOutException when the worker refuses it
     * @throws AdminApiException when the worker cannot be reached or fails
     */
    suspend fun signIn(password: String): AdminSummary {
        val token = password.trim()
        val api = AdminApi(transport, baseUrl(), token)
        val summary = api.summary()
        // Where the alerts stand now, so the backup check only shows what comes after signing in. If this fails,
        // the check's own first run does it.
        store.cursor = try {
            api.notifications(limit = 1).maxOfOrNull { it.id } ?: 0
        } catch (e: AdminApiException) {
            null
        }
        store.saveToken(token)
        PollWorker.schedule(context)
        PushRegistration.register(context)
        return summary
    }

    /**
     * Forgets the owner, then tells the worker to stop pushing here (best effort). In that order: the request can
     * hang on a dead network for as long as its socket does, since a timeout cancels the coroutine but not the
     * blocking read under it, and until the forgetting the token is still on the phone. So what the request needs
     * (the push token, and the owner's routes with the token already in hand) is taken first, the forgetting is in
     * a `finally`, and a sign-out cancelled at any point, or a process killed while the worker is asked, leaves no
     * token behind.
     */
    suspend fun signOut() {
        val pushToken = store.pushToken
        val worker: AdminApi? = try {
            if (pushToken == null) {
                null
            } else {
                try {
                    api()
                } catch (e: AdminApiException) {
                    null // no token to ask with, or no server named: there is nobody to tell
                }
            }
        } finally {
            forget()
        }
        if (worker == null || pushToken == null) return
        try {
            withTimeout(5_000) { worker.unregisterDevice(pushToken) }
        } catch (e: TimeoutCancellationException) {
            // Offline: FCM has dropped the token already, and the worker deletes tokens FCM calls unregistered.
        } catch (e: AdminApiException) {
            // Same.
        }
    }

    /** The worker stopped accepting the token (it was changed): sign out here, without asking it anything. */
    fun expire() = forget()

    private fun forget() {
        PollWorker.cancel(context)
        PushRegistration.forget(context)
        notifier.cancelAll()
        store.signOut()
    }

    private suspend fun baseUrl(): String {
        base?.let { return it }
        val api = try {
            repository.load().api
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        return api?.trimEnd('/')?.also { base = it }
            ?: throw AdminApiException("This build names no server: portfolio.json has no \"api\".", status = -1)
    }
}
