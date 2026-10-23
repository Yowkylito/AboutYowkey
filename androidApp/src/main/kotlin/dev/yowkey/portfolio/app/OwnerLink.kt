package dev.yowkey.portfolio.app

import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import dev.yowkey.portfolio.admin.AdminNotification
import dev.yowkey.portfolio.admin.NotificationKind

/**
 * A place in the admin view, as the launcher shortcut and the notifications name it: `yowkey-owner://dashboard`,
 * `yowkey-owner://messages/34`, `yowkey-owner://notes/12`, `yowkey-owner://records/daily`. Only explicit intents to
 * MainActivity carry one (there is no intent filter for the scheme); any of them still needs the owner signed in.
 */
sealed interface OwnerLink {
    data object Dashboard : OwnerLink
    data class Messages(val id: Long? = null) : OwnerLink
    data class Notes(val id: Long? = null) : OwnerLink
    data class Records(val daily: Boolean = false) : OwnerLink

    val uri: Uri
        get() = when (this) {
            Dashboard -> "$SCHEME://dashboard"
            is Messages -> "$SCHEME://messages" + (id?.let { "/$it" } ?: "")
            is Notes -> "$SCHEME://notes" + (id?.let { "/$it" } ?: "")
            is Records -> "$SCHEME://records/" + if (daily) "daily" else "alerts"
        }.toUri()

    companion object {
        const val SCHEME = "yowkey-owner"

        fun from(intent: Intent?): OwnerLink? = intent?.data?.let(::parse)

        fun parse(uri: Uri): OwnerLink? {
            if (uri.scheme != SCHEME) return null
            val id = uri.pathSegments.firstOrNull()?.toLongOrNull()
            return when (uri.host) {
                "messages" -> Messages(id)
                "notes" -> Notes(id)
                "records" -> Records(daily = uri.pathSegments.firstOrNull() == "daily")
                else -> Dashboard
            }
        }

        /** Where an alert's tap goes: the message itself, the note in the list, or the day's report. */
        fun forAlert(record: AdminNotification): OwnerLink = when (record.kind) {
            NotificationKind.MESSAGE -> Messages(record.refId)
            NotificationKind.FEEDBACK -> Notes(record.refId)
            NotificationKind.DAILY -> Records(daily = true)
            else -> Records()
        }
    }
}
