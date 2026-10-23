package dev.yowkey.portfolio.app.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.yowkey.portfolio.R
import dev.yowkey.portfolio.admin.AdminNotification
import dev.yowkey.portfolio.admin.NotificationKind
import dev.yowkey.portfolio.app.MainActivity
import dev.yowkey.portfolio.app.OwnerLink
import dev.yowkey.portfolio.app.owner.OwnerStore
import java.time.Instant

/**
 * Puts an alert record up as a notification, the same way whether a push or the backup check brought it. The
 * notification id is the record's id, so the same record shown twice replaces itself instead of doubling. A tap opens
 * the admin view at the message, the note, or the day's report. On the lock screen only the kind shows ("New
 * message"), never a visitor's words.
 */
class AlertNotifier(private val context: Context, private val store: OwnerStore) {

    /** Whether the owner lets the app post notifications at all (the Android 13+ permission and the app's switch). */
    fun canNotify(): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun show(record: AdminNotification) {
        store.markShown(record.id)
        if (!canNotify()) return
        val channel = channelFor(record.kind)
        val tap = PendingIntent.getActivity(
            context,
            record.id.toInt(),
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setData(OwnerLink.forAlert(record).uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val publicTitle = when (record.kind) {
            NotificationKind.FEEDBACK -> "New note"
            NotificationKind.DAILY -> "Daily summary"
            else -> "New message"
        }
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_yowkey)
            .setColor(ContextCompat.getColor(context, R.color.yowkey_orange))
            .setContentTitle(record.title.ifBlank { publicTitle })
            .setContentText(record.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(record.body))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setWhen(timeOf(record))
            .setShowWhen(true)
            .setCategory(if (record.kind == NotificationKind.DAILY) NotificationCompat.CATEGORY_STATUS else NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(if (channel == CHANNEL_MESSAGES) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, channel)
                    .setSmallIcon(R.drawable.ic_stat_yowkey)
                    .setColor(ContextCompat.getColor(context, R.color.yowkey_orange))
                    .setContentTitle(publicTitle)
                    .build(),
            )
            .build()
        try {
            NotificationManagerCompat.from(context).notify(record.id.toInt(), notification)
        } catch (e: SecurityException) {
            // The permission was revoked between the check and the post: nothing to show, nothing to crash.
        }
    }

    fun cancelAll() {
        NotificationManagerCompat.from(context).cancelAll()
    }

    private fun timeOf(record: AdminNotification): Long =
        runCatching { Instant.parse(record.createdAt).toEpochMilli() }.getOrElse { System.currentTimeMillis() }

    companion object {
        const val CHANNEL_MESSAGES = "messages"
        const val CHANNEL_NOTES = "notes"
        const val CHANNEL_DAILY = "daily"

        fun channelFor(kind: String): String = when (kind) {
            NotificationKind.FEEDBACK -> CHANNEL_NOTES
            NotificationKind.DAILY -> CHANNEL_DAILY
            else -> CHANNEL_MESSAGES
        }

        /** Creates the three channels; safe to repeat (Android keeps the owner's own settings for each). */
        fun createChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannels(
                listOf(
                    channel(context, CHANNEL_MESSAGES, R.string.channel_messages, R.string.channel_messages_about, NotificationManager.IMPORTANCE_HIGH),
                    channel(context, CHANNEL_NOTES, R.string.channel_notes, R.string.channel_notes_about, NotificationManager.IMPORTANCE_DEFAULT),
                    channel(context, CHANNEL_DAILY, R.string.channel_daily, R.string.channel_daily_about, NotificationManager.IMPORTANCE_DEFAULT),
                ),
            )
        }

        private fun channel(context: Context, id: String, name: Int, about: Int, importance: Int) =
            NotificationChannel(id, context.getString(name), importance).apply {
                description = context.getString(about)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
    }
}
