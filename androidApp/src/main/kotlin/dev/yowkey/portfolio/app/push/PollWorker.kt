package dev.yowkey.portfolio.app.push

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.yowkey.portfolio.admin.AdminApiException
import dev.yowkey.portfolio.admin.AdminSignedOutException
import dev.yowkey.portfolio.admin.planPoll
import dev.yowkey.portfolio.app.owner.OwnerSession
import dev.yowkey.portfolio.app.owner.OwnerStore
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit

/**
 * The backup for push: every 15 minutes while the owner is signed in (and the phone is online), asks the worker for
 * the alerts recorded since the last one handled and puts each up, oldest first, through the same [AlertNotifier] a
 * push uses, so a record both brought is shown once. Without a Firebase config it is the only way alerts arrive.
 */
class PollWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val koin = GlobalContext.getOrNull() ?: return Result.retry()
        val session = koin.get<OwnerSession>()
        val store = koin.get<OwnerStore>()
        val notifier = koin.get<AlertNotifier>()
        if (!session.signedIn.value) {
            cancel(applicationContext)
            return Result.success()
        }
        return try {
            val api = session.api()
            repeat(MAX_PAGES) {
                val cursor = store.cursor
                val fetched = if (cursor == null) api.notifications(limit = 1) else api.notifications(after = cursor, limit = PAGE)
                val plan = planPoll(cursor, fetched, store.shown.toSet())
                plan.show.forEach(notifier::show)
                store.cursor = plan.cursor
                if (cursor == null || fetched.size < PAGE) return Result.success()
            }
            Result.success()
        } catch (e: AdminSignedOutException) {
            session.expire()
            Result.success()
        } catch (e: AdminApiException) {
            Result.retry()
        }
    }

    companion object {
        private const val PERIODIC = "owner-alerts"
        private const val NOW = "owner-alerts-now"
        private const val PAGE = 50
        private const val MAX_PAGES = 4

        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Every 15 minutes (Android's shortest period) from now on; keeps the running schedule if there is one. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PollWorker>(15, TimeUnit.MINUTES).setConstraints(online).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** One check as soon as the phone is online (Settings → Check now). */
        fun runOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<PollWorker>().setConstraints(online).build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).apply {
                cancelUniqueWork(PERIODIC)
                cancelUniqueWork(NOW)
            }
        }
    }
}
