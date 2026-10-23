package dev.yowkey.portfolio.admin

import androidx.compose.runtime.Immutable
import dev.yowkey.portfolio.model.MessageTopic
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

// What the worker's owner routes answer (`GET /admin/…`), field for field. Every field the app can live without has
// a default, so a worker that adds or drops one does not break the app.

/** `GET /admin/summary`: today's figures and the running totals, in the owner's day ([timeZone]). */
@Immutable
@Serializable
data class AdminSummary(
    val day: String,
    val timeZone: String = "Asia/Manila",
    val visits: VisitTotals = VisitTotals(),
    val clicks: List<LinkClicks> = emptyList(),
    val notes: NoteTotals = NoteTotals(),
    val messages: MessageTotals = MessageTotals(),
    val push: PushStatus = PushStatus(),
) {
    /** The clicks on [link] (`linkedin`, `github`), zero when the worker does not track it. */
    fun clicksOn(link: String): LinkClicks = clicks.firstOrNull { it.link == link } ?: LinkClicks(link)
}

@Immutable
@Serializable
data class VisitTotals(val total: Int = 0, val today: Int = 0, val since: String? = null)

@Immutable
@Serializable
data class LinkClicks(val link: String, val total: Int = 0, val today: Int = 0)

@Immutable
@Serializable
data class NoteTotals(val total: Int = 0, val today: Int = 0, val published: Int = 0)

/** [hiring]: messages about a job or freelance work that are not archived. */
@Immutable
@Serializable
data class MessageTotals(val total: Int = 0, val today: Int = 0, val unread: Int = 0, val hiring: Int = 0)

/** Whether the worker can send push ([configured]: it has the FCM service account) and to how many phones. */
@Immutable
@Serializable
data class PushStatus(val configured: Boolean = false, val devices: Int = 0)

/** One day of `GET /admin/days`, a day that has any data. */
@Immutable
@Serializable
data class DayStats(
    val day: String,
    val visits: Int = 0,
    val clicks: Map<String, Int> = emptyMap(),
    val notes: Int = 0,
    val messages: Int = 0,
    val countries: List<CountryCount> = emptyList(),
)

@Immutable
@Serializable
data class CountryCount(val country: String, val count: Int = 0)

/** A visitor's note, as the owner sees it (`GET /admin/feedback`). Times are ISO 8601 in UTC. */
@Immutable
@Serializable
data class AdminNote(
    val id: Long,
    @SerialName("created_at") val createdAt: String = "",
    val name: String? = null,
    val contact: String? = null,
    val rating: Int? = null,
    val message: String = "",
    /** D1 keeps it as 0 or 1; either that or a JSON boolean is read. */
    @Serializable(with = FlagSerializer::class) val consent: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("hidden_at") val hiddenAt: String? = null,
) {
    val isPublished: Boolean get() = publishedAt != null
    val isHidden: Boolean get() = hiddenAt != null

    /** Only a note whose visitor agreed may go on the page; the worker answers 409 otherwise. */
    val canPublish: Boolean get() = consent && !isPublished
}

/** A private message to the owner (`GET /admin/messages`). */
@Immutable
@Serializable
data class AdminMessage(
    val id: Long,
    @SerialName("created_at") val createdAt: String = "",
    val name: String? = null,
    val contact: String = "",
    val topic: String = MessageTopic.Other.wire,
    val message: String = "",
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("archived_at") val archivedAt: String? = null,
) {
    val isRead: Boolean get() = readAt != null
    val isArchived: Boolean get() = archivedAt != null

    /** About a job or freelance work: "who wants to hire me". */
    val isHiring: Boolean get() = MessageTopic.of(topic).let { it == MessageTopic.Job || it == MessageTopic.Freelance }

    /**
     * The topic's short label, as the worker's push titles have it ("New message · Job"); the visitor's form words
     * them for the visitor ([MessageTopic.label]).
     */
    val topicLabel: String
        get() = when (MessageTopic.of(topic)) {
            MessageTopic.Job -> "Job"
            MessageTopic.Freelance -> "Freelance"
            MessageTopic.Collaboration -> "Collaboration"
            else -> "Other"
        }

    /** Who sent it, as a heading: the name they gave, else their contact. */
    val sender: String get() = name?.takeIf { it.isNotBlank() } ?: contact.ifBlank { "Someone" }

    /** The contact when it is an email address, so Reply can open the mail app; null when it is anything else. */
    val replyEmail: String? get() = contact.trim().takeIf { EmailAddress.matches(it) }
}

private val EmailAddress = Regex("^[^\\s@<>]+@[^\\s@<>]+\\.[^\\s@<>]+$")

/** One alert the worker recorded (`GET /admin/notifications`), and the data of the push it sent for it. */
@Immutable
@Serializable
data class AdminNotification(
    val id: Long,
    @SerialName("created_at") val createdAt: String = "",
    val kind: String = NotificationKind.MESSAGE,
    val title: String = "",
    val body: String = "",
    /** The message's or the note's id, for [NotificationKind.MESSAGE] and [NotificationKind.FEEDBACK]. */
    @SerialName("ref_id") val refId: Long? = null,
) {
    companion object {
        /**
         * Reads a push's data, which FCM carries as strings (`id`, `kind`, `title`, `body`, `ref_id`); null without
         * a usable id.
         */
        fun fromPushData(data: Map<String, String>): AdminNotification? {
            val id = data["id"]?.trim()?.toLongOrNull() ?: return null
            return AdminNotification(
                id = id,
                kind = data["kind"]?.trim().orEmpty().ifEmpty { NotificationKind.MESSAGE },
                title = data["title"].orEmpty(),
                body = data["body"].orEmpty(),
                refId = data["ref_id"]?.trim()?.toLongOrNull(),
            )
        }
    }
}

object NotificationKind {
    const val FEEDBACK = "feedback"
    const val MESSAGE = "message"
    const val DAILY = "daily"
}

/** One row the 23:59 report wrote (`GET /admin/reports`). */
@Immutable
@Serializable
data class DailyReport(
    val day: String,
    val visits: Int = 0,
    val clicks: Map<String, Int> = emptyMap(),
    val notes: Int = 0,
    val messages: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
)

/** What `POST /admin/feedback/:id` can do to a note; [wire] is the body's `action`. */
enum class NoteAction(val wire: String) {
    Publish("publish"),
    Unpublish("unpublish"),
    Hide("hide"),
    Unhide("unhide"),
    Delete("delete"),
}

/** What `POST /admin/messages/:id` can do to a message; [wire] is the body's `action`. */
enum class MessageAction(val wire: String) {
    Read("read"),
    Unread("unread"),
    Archive("archive"),
    Unarchive("unarchive"),
    Delete("delete"),
}

/** Every list route answers `{ "items": [...] }`. */
@Serializable
internal data class Items<T>(val items: List<T> = emptyList())

@Serializable
internal data class ActionBody(val action: String)

@Serializable
internal data class DeviceBody(val token: String, val platform: String? = null)

/** A yes/no that may arrive as a JSON boolean, a number (SQLite's 0/1) or a string; anything else is no. */
internal object FlagSerializer : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("dev.yowkey.portfolio.admin.Flag", PrimitiveKind.BOOLEAN)

    override fun deserialize(decoder: Decoder): Boolean {
        val input = decoder as? JsonDecoder ?: return decoder.decodeBoolean()
        val value = input.decodeJsonElement() as? JsonPrimitive ?: return false
        value.booleanOrNull?.let { return it }
        value.longOrNull?.let { return it != 0L }
        return value.content.trim().lowercase() in setOf("yes", "on", "true", "1")
    }

    override fun serialize(encoder: Encoder, value: Boolean) = encoder.encodeBoolean(value)
}
