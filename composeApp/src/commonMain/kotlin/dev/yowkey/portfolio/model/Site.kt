package dev.yowkey.portfolio.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What `GET` and `POST /visits` of the site's backend (`worker/`) answer: visitors counted since [since], each at
 * most once a day. [Portfolio.api] names the backend; without one nothing is counted or shown.
 */
@Immutable
@Serializable
data class VisitCount(
    val total: Long,
    val today: Long = 0,
    /** The first counted day, `YYYY-MM-DD`; null until the first visit. */
    val since: String? = null,
) {
    /** "1,234 visits since Oct 2026"; null until something has been counted. */
    val summary: String?
        get() {
            if (total <= 0) return null
            val visits = "${groupDigits(total)} ${if (total == 1L) "visit" else "visits"}"
            val month = since?.let(::monthAndYear) ?: return visits
            return "$visits since $month"
        }
}

/** The rating the published notes add up to: how many carried one, and their mean to one decimal. */
@Immutable
@Serializable
data class RatingSummary(val count: Int = 0, val average: Double? = null) {
    /** "4.8 from 12 notes", for the star drawn beside it; null until a published note carries a rating. */
    val summary: String?
        get() {
            if (count <= 0 || average == null) return null
            return "${oneDecimal(average)} from $count ${if (count == 1) "note" else "notes"}"
        }
}

/** A note the owner has published, as `GET /notes` gives it: the visitor's words and name, never their contact. */
@Immutable
@Serializable
data class PublicNote(
    val id: Long,
    val name: String? = null,
    val rating: Int? = null,
    val message: String,
    /** The day it was published, `YYYY-MM-DD`. */
    val date: String? = null,
) {
    /** "Ada · Oct 2026", or "A visitor · Oct 2026" for a note left without a name. */
    val byLine: String
        get() = listOfNotNull(name?.takeIf { it.isNotBlank() } ?: ANONYMOUS, date?.let(::monthAndYear)).joinToString(" · ")

    companion object {
        const val ANONYMOUS = "A visitor"
    }
}

/** What `GET /notes` answers: the visits so far, the rating, and the latest published notes, newest first. */
@Immutable
@Serializable
data class SiteNotes(
    val visits: VisitCount,
    val ratings: RatingSummary = RatingSummary(),
    val notes: List<PublicNote> = emptyList(),
)

/** The hero's live line: the published notes' rating and the visit count, each only once it exists. */
@Immutable
data class LiveStats(val visits: VisitCount? = null, val ratings: RatingSummary? = null) {
    val ratingLine: String? get() = ratings?.summary
    val visitsLine: String? get() = visits?.summary
    val isEmpty: Boolean get() = ratingLine == null && visitsLine == null
}

/** 1234567 → "1,234,567". */
fun groupDigits(value: Long): String = value.toString().reversed().chunked(3).joinToString(",").reversed()

/** 4.75 → "4.8", 5.0 → "5.0": one decimal, the same on every platform. */
fun oneDecimal(value: Double): String {
    val tenths = (value * 10).roundToInt()
    return "${tenths / 10}.${abs(tenths % 10)}"
}

/** "2026-10-24" → "Oct 2026"; null for anything that is not such a date. */
fun monthAndYear(day: String): String? {
    val match = Regex("""(\d{4})-(\d{2})-\d{2}""").matchEntire(day) ?: return null
    val month = match.groupValues[2].toInt().takeIf { it in 1..12 } ?: return null
    return "${Months[month - 1]} ${match.groupValues[1]}"
}

private val Months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/**
 * A note for the owner, as the visitor types it. The rules are the backend's (`worker/src/handler.js`,
 * `validateFeedback`), so the form can say what is wrong before anything is sent; the backend checks again.
 */
@Immutable
@Serializable
data class FeedbackDraft(
    val name: String = "",
    /** Where a reply can go, if they want one: an email, or whatever else they choose to leave. Never shown. */
    val contact: String = "",
    /** Overall impression, 1 to 5; null for none. */
    val rating: Int? = null,
    val message: String = "",
    /** Whether the note may be shown on the site (with the name, never the contact) once the owner publishes it. */
    val consent: Boolean = false,
) {
    /** What stops this note from being sent, message first, as sentences for the visitor; empty when it can go. */
    val problems: List<String>
        get() = buildList {
            val text = message.trim()
            when {
                text.isEmpty() -> add("Write a message first.")
                text.length > MAX_MESSAGE -> add("Keep the message under $MAX_MESSAGE characters.")
            }
            if (name.trim().length > MAX_NAME) add("Keep the name under $MAX_NAME characters.")
            if (contact.trim().length > MAX_CONTACT) add("Keep the email under $MAX_CONTACT characters.")
            if (rating != null && rating !in 1..5) add("The rating is 1 to 5.")
        }

    /** Trimmed, as the backend stores it. */
    fun normalized(): FeedbackDraft = FeedbackDraft(name.trim(), contact.trim(), rating, message.trim(), consent)

    companion object {
        const val MAX_NAME = 80
        const val MAX_CONTACT = 160
        const val MAX_MESSAGE = 2000
    }
}

/**
 * The words of the note form and the published notes, shared by the app and the static page so both say the same.
 * Hiring goes to the private [MessageCopy] form; a note is the public one, and says so.
 */
object FeedbackCopy {
    const val TITLE = "Leave a note"
    const val PUBLIC = "Public"
    const val INTRO = "A bug on this site, or a thought on my work: I read every note. Tick the box and, once I have " +
        "read it, it may appear here with your name; your email never does."
    const val NAME = "Name"
    const val CONTACT = "Email"
    const val RATING = "Overall impression"
    const val MESSAGE = "Message"
    const val CONSENT = "You may show this note on the site"
    const val SEND = "Send note"
    const val SENDING = "Sending…"
    const val THANKS = "Thanks, your note is in."
    const val ANOTHER = "Write another"
    const val NOTES_TITLE = "Notes from visitors"

    /** "5 · Excellent" down to "1 · Poor": the static page's options, the app's star names. */
    fun ratingLabel(rating: Int): String = "$rating · ${Impressions[rating - 1]}"

    private val Impressions = listOf("Poor", "Weak", "Fine", "Good", "Excellent")
}

/**
 * The profile links whose clicks the backend counts, by their label lowercased; the worker's `TRACKED_LINKS` names
 * the same ones and turns any other away.
 */
val TrackedLinks: Set<String> = setOf("linkedin", "github")

/** The name the backend counts this link's clicks under ("linkedin" for "LinkedIn"); null for a link it does not count. */
val Link.trackingKey: String?
    get() = label.trim().lowercase().takeIf { it in TrackedLinks }

/** What a private message is about, as the form's chips offer it; [wire] is the value the backend stores. */
enum class MessageTopic(val wire: String, val label: String) {
    Job("job", "A job"),
    Freelance("freelance", "A freelance project"),
    Collaboration("collaboration", "Collaboration"),
    Other("other", "Something else"),
    ;

    companion object {
        /** The topic stored as [wire]; null for anything else, so a value this app does not know is never guessed at. */
        fun of(wire: String?): MessageTopic? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * A private message for the owner, as the visitor types it. Unlike a note it is never shown on the site, so it asks
 * for a way to reply. The rules are the backend's (`POST /messages` in `worker/src/handler.js`), so the form can say
 * what is wrong before anything is sent; the backend checks again.
 */
@Immutable
@Serializable
data class MessageDraft(
    val name: String = "",
    /** Where the reply goes: an email, or whatever else they choose to leave. Required, and only the owner sees it. */
    val contact: String = "",
    /** One of [MessageTopic]'s wire values; empty for none, which the backend files as [MessageTopic.Other]. */
    val topic: String = "",
    val message: String = "",
) {
    /**
     * What stops this message from being sent, as sentences for the visitor; empty when it can go. The message, then
     * the contact, then the name, then the topic, in the backend's order (`validateMessage`), so both name the same
     * problem first.
     */
    val problems: List<String>
        get() = buildList {
            val text = message.trim()
            val reply = contact.trim()
            if (text.isEmpty()) add("Write a message first.")
            else if (text.length > MAX_MESSAGE) add("Keep the message under $MAX_MESSAGE characters.")
            if (reply.isEmpty()) add("Leave an email or another way to reach you.")
            else if (reply.length > MAX_CONTACT) add("Keep the email under $MAX_CONTACT characters.")
            if (name.trim().length > MAX_NAME) add("Keep the name under $MAX_NAME characters.")
            if (topic.isNotBlank() && MessageTopic.of(topic.trim()) == null) add("Pick one of the topics.")
        }

    /** Trimmed, with no topic filed as [MessageTopic.Other], as the backend stores it. */
    fun normalized(): MessageDraft =
        MessageDraft(name.trim(), contact.trim(), topic.trim().ifEmpty { MessageTopic.Other.wire }, message.trim())

    companion object {
        const val MAX_NAME = 80
        const val MAX_CONTACT = 160
        const val MAX_MESSAGE = 2000
    }
}

/**
 * The words of the private message form, shared by the app and the static page. Next to the note form, each says
 * plainly who reads it: a message only the owner, a note perhaps everyone.
 */
object MessageCopy {
    const val TITLE = "Message me"
    const val PRIVATE = "Private"
    const val INTRO = "A job, a freelance project, or anything you would rather not post: only I read this, nothing " +
        "from it is ever shown on the site, and I reply to the contact you leave."
    const val NAME = "Name"
    const val CONTACT = "How to reach you"
    /** After "Required: " in the app and "(required: …)" on the static page. */
    const val CONTACT_HINT = "an email, or another way to reply"
    const val TOPIC = "What it's about"
    const val MESSAGE = "Message"
    const val SEND = "Send message"
    const val SENDING = "Sending…"
    const val THANKS = "Thanks, your message is in. I'll reply to the contact you left."
    const val ANOTHER = "Send another"
}
