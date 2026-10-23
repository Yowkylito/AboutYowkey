package dev.yowkey.portfolio.app.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dev.yowkey.portfolio.admin.AdminNotification
import dev.yowkey.portfolio.app.owner.OwnerSession
import org.koin.core.context.GlobalContext
import java.time.Instant

/**
 * The worker's pushes: data-only, high priority, `{ id, kind, title, body, ref_id }` as strings. Each becomes the
 * same notification the backup check would put up for that record. Ignored while signed out.
 */
class PushService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val koin = GlobalContext.getOrNull() ?: return
        if (!koin.get<OwnerSession>().signedIn.value) return
        val record = AdminNotification.fromPushData(message.data) ?: return
        val sent = message.sentTime.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).toString() }.orEmpty()
        koin.get<AlertNotifier>().show(record.copy(createdAt = sent))
    }

    /** FCM 26's callback for a new or refreshed token. */
    override fun onRegistered(token: String) = tellTheWorker(token)

    /** The older callback for the same; FCM may still call it, and telling the worker twice is harmless. */
    @Deprecated("FCM 26 calls onRegistered", ReplaceWith("onRegistered(token)"))
    override fun onNewToken(token: String) = tellTheWorker(token)

    private fun tellTheWorker(token: String) {
        val koin = GlobalContext.getOrNull() ?: return
        if (koin.get<OwnerSession>().signedIn.value) PushRegistration.register(applicationContext, token)
    }
}
