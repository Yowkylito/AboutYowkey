package dev.yowkey.portfolio.app.push

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import dev.yowkey.portfolio.admin.AdminApiException
import dev.yowkey.portfolio.admin.AdminSignedOutException
import dev.yowkey.portfolio.app.owner.OwnerSession
import dev.yowkey.portfolio.app.owner.OwnerStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.core.context.GlobalContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * This phone's FCM registration. Push exists only when the build had a Firebase config (androidApp/
 * google-services.json, gitignored); without one there is no [FirebaseApp], nothing here calls Firebase, and the
 * backup check alone brings the alerts.
 */
object PushRegistration {

    private const val WORK = "owner-push-register"

    /** Whether this build can receive pushes at all. */
    fun isAvailable(context: Context): Boolean = runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

    /** Tells the worker this phone's push token, now or once online; with [token] null it asks FCM for it. */
    fun register(context: Context, token: String? = null) {
        if (!isAvailable(context)) return
        val request = OneTimeWorkRequestBuilder<RegisterWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(RegisterWorker.TOKEN to token))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request)
    }

    /** On sign-out: no registration in flight, and FCM drops the token, so no push can reach this phone. */
    fun forget(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
        if (isAvailable(context)) runCatching { FirebaseMessaging.getInstance().unregister() }
    }

    /**
     * The current token. FCM 26 deprecates reading it in favour of `register()` plus [PushService.onRegistered],
     * which the service also handles; the read stays because it answers right here, whatever the callback does.
     */
    @Suppress("DEPRECATION")
    internal suspend fun currentToken(): String = FirebaseMessaging.getInstance().token.await()

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            val error = task.exception
            if (task.isSuccessful) continuation.resume(task.result) else continuation.resumeWithException(error ?: IllegalStateException("FCM gave no token"))
        }
    }
}

/** Sends this phone's FCM token to `POST /admin/devices`; retried with backoff until the worker has it. */
class RegisterWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val koin = GlobalContext.getOrNull() ?: return Result.retry()
        val session = koin.get<OwnerSession>()
        val store = koin.get<OwnerStore>()
        if (!session.signedIn.value || !PushRegistration.isAvailable(applicationContext)) return Result.success()
        return try {
            val token = inputData.getString(TOKEN) ?: PushRegistration.currentToken()
            session.api().registerDevice(token)
            store.pushToken = token
            Result.success()
        } catch (e: AdminSignedOutException) {
            session.expire()
            Result.success()
        } catch (e: AdminApiException) {
            Result.retry()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // FCM could not hand out a token (no Play services, no network yet): try again later.
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val TOKEN = "token"
    }
}
