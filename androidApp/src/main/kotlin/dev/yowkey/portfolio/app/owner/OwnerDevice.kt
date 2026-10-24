package dev.yowkey.portfolio.app.owner

import android.content.Context
import dev.yowkey.portfolio.app.push.AlertNotifier
import dev.yowkey.portfolio.app.push.PollWorker
import dev.yowkey.portfolio.app.push.PushRegistration

/**
 * The phone's side of the alerts, as Settings and the first sign-in see it: whether push can work here, whether the
 * owner lets notifications show, and the backup check run on demand. It is kept apart from [OwnerViewModel] so the
 * admin view's state does not reach into WorkManager, Firebase and the system's notification settings, and so the
 * view model runs in JVM tests with a fake.
 */
interface OwnerDevice {
    /** This phone's side of push, read now. */
    fun pushInfo(): PushInfo

    /** Whether the notification permission (Android 13+) is missing and has never been asked for. */
    fun shouldAskForNotifications(): Boolean

    /** Notes that the permission was asked for, so the first sign-in asks once only. */
    fun askedForNotifications()

    /** Runs the backup check once, as soon as the phone is online. */
    fun checkNow()
}

/** [OwnerDevice] on the phone: [PushRegistration], [AlertNotifier], [OwnerStore] and [PollWorker]. */
class AndroidOwnerDevice(
    private val context: Context,
    private val store: OwnerStore,
    private val notifier: AlertNotifier,
) : OwnerDevice {

    override fun pushInfo(): PushInfo = PushInfo(
        firebase = PushRegistration.isAvailable(context),
        registered = store.pushToken != null,
        notificationsAllowed = notifier.canNotify(),
    )

    override fun shouldAskForNotifications(): Boolean = !store.askedForNotifications && !notifier.canNotify()

    override fun askedForNotifications() {
        store.askedForNotifications = true
    }

    override fun checkNow() = PollWorker.runOnce(context)
}
