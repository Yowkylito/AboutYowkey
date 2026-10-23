package dev.yowkey.portfolio.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

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
    /** The footer's line, "1,234 visits since Oct 2026"; null until something has been counted. */
    val summary: String?
        get() {
            if (total <= 0) return null
            val visits = "${groupDigits(total)} ${if (total == 1L) "visit" else "visits"}"
            val month = since?.let(::monthAndYear) ?: return visits
            return "$visits since $month"
        }
}

/** 1234567 → "1,234,567". */
fun groupDigits(value: Long): String = value.toString().reversed().chunked(3).joinToString(",").reversed()

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
    /** Where a reply can go, if they want one: an email, or whatever else they choose to leave. */
    val contact: String = "",
    /** Overall impression, 1 to 5; null for none. */
    val rating: Int? = null,
    val message: String = "",
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
    fun normalized(): FeedbackDraft = FeedbackDraft(name.trim(), contact.trim(), rating, message.trim())

    companion object {
        const val MAX_NAME = 80
        const val MAX_CONTACT = 160
        const val MAX_MESSAGE = 2000
    }
}

/** The words of the note form, shared by the app and the static page so both say the same. */
object FeedbackCopy {
    const val TITLE = "Leave a note"
    const val INTRO = "Hiring, a bug on this site, or a thought on my work: I read every note, and none is published."
    const val NAME = "Name"
    const val CONTACT = "Email"
    const val RATING = "Overall impression"
    const val MESSAGE = "Message"
    const val SEND = "Send note"
    const val SENDING = "Sending…"
    const val THANKS = "Thanks, your note is in."
    const val ANOTHER = "Write another"

    /** "5 · Excellent" down to "1 · Poor": the static page's options, the app's star names. */
    fun ratingLabel(rating: Int): String = "$rating · ${Impressions[rating - 1]}"

    private val Impressions = listOf("Poor", "Weak", "Fine", "Good", "Excellent")
}
