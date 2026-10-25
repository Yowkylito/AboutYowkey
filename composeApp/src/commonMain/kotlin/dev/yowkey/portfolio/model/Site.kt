package dev.yowkey.portfolio.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

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

/**
 * The rating every note the owner has not hidden adds up to: how many carried one, their mean to one decimal, and
 * how many gave each number of stars (`"1"` to `"5"`, as the backend's JSON has them). [keptPrivate] of them came
 * with words their writers kept private (notes left before notes were public), so the page shows fewer notes than
 * it counts ratings, and says why.
 */
@Immutable
@Serializable
data class RatingSummary(
    val count: Int = 0,
    val average: Double? = null,
    val stars: Map<String, Int> = emptyMap(),
    @SerialName("private") val keptPrivate: Int = 0,
    /** [keptPrivate] star by star, `"1"` to `"5"`: what each star's sheet says it cannot show. */
    val privateStars: Map<String, Int> = emptyMap(),
) {
    /** "4.8 from 12 ratings", for the star drawn beside it; null until a note carries a rating. */
    val summary: String?
        get() {
            if (count <= 0 || average == null) return null
            return "${oneDecimal(average)} from $count ${if (count == 1) "rating" else "ratings"}"
        }

    /** How many gave [stars] stars. */
    fun countOf(stars: Int): Int = this.stars[stars.toString()] ?: 0

    /** How many of [countOf] came with words kept private. */
    fun privateCountOf(stars: Int): Int = privateStars[stars.toString()] ?: 0

    /** [countOf] as a share of [count], 0 to 1; 0 while nothing is rated. */
    fun shareOf(stars: Int): Float = if (count <= 0) 0f else countOf(stars).toFloat() / count

    /** [shareOf] as a whole percentage, "75%"; the five need not add up to exactly 100. */
    fun percentOf(stars: Int): String = "${(shareOf(stars) * 100).roundToInt()}%"
}

/** A public note, as `GET /notes` gives it: the visitor's words and name, never their contact. */
@Immutable
@Serializable
data class PublicNote(
    val id: Long,
    val name: String? = null,
    val rating: Int? = null,
    val message: String,
    /** The day it was left, `YYYY-MM-DD`, in the owner's time zone. */
    val date: String? = null,
    /** How the writer knows the owner, a [Relation]'s wire value; null when they did not say (and for older notes). */
    val relation: String? = null,
    /** The owner pinned it: it comes before the others, everywhere notes are listed. */
    val pinned: Boolean = false,
) {
    /** The name as the card's title: the one given, or "A visitor". */
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: ANONYMOUS

    /** The card's date line, "Oct 23, 2026"; null without a date. */
    val dateLine: String? get() = date?.let(::fullDate)

    /** The relation worth a badge: a colleague, a friend, a special someone; not "Other", not unsaid. */
    val badge: Relation? get() = Relation.of(relation)?.takeIf { it != Relation.Other }

    companion object {
        const val ANONYMOUS = "A visitor"
    }
}

/**
 * What `GET /notes` answers: the visits so far, the rating (always the whole site's), and a page of the public
 * notes, pinned first, then newest first: all of them, or one star's, and of those one relation's. [total] is how
 * many public notes match; [more] says another page follows.
 */
@Immutable
@Serializable
data class SiteNotes(
    val visits: VisitCount,
    val ratings: RatingSummary = RatingSummary(),
    val notes: List<PublicNote> = emptyList(),
    val total: Int = notes.size,
    val more: Boolean = false,
    /**
     * How many public notes (of the star asked for, if one was) each [Relation] has, by wire value; a note that
     * says nothing counts as [Relation.Other]. The sheet's sorter shows these beside its chips.
     */
    val relations: Map<String, Int> = emptyMap(),
) {
    /** How many public notes [relation] has, by [relations]. */
    fun countOf(relation: Relation): Int = relations[relation.wire] ?: 0
}

/**
 * The page bar's proof: the notes' rating and the visit count, each only once it is big enough to count as proof.
 * Either is null until the backend answers (or when it never does), and an answer can still hold too little to show.
 *
 * A small number on the first screen works against the page: a single 5.0 reads as too good to be true (Spiegel
 * Research Center: purchase likelihood falls as averages near 5.0, and the first five reviews matter most), and a
 * low count tells the reader that few others came (Cialdini's negative social proof). So the rating shows from
 * [MIN_RATINGS] ratings and the visits from [MIN_VISITS]; until then the bar says where the owner is instead.
 */
@Immutable
data class LiveStats(val visits: VisitCount? = null, val ratings: RatingSummary? = null) {
    /** The average to show, once [MIN_RATINGS] notes carry a rating; null before. */
    val average: Double? get() = ratings?.takeIf { it.count >= MIN_RATINGS }?.average

    /** How many rated; 0 while there is no [average]. */
    val ratingCount: Int get() = if (average == null) 0 else ratings?.count ?: 0

    /** The visits counted so far; 0 before the first, and before the backend answers. */
    val visitTotal: Long get() = visits?.total?.coerceAtLeast(0) ?: 0

    val hasRating: Boolean get() = average != null
    val hasVisits: Boolean get() = visitTotal >= MIN_VISITS
    val isEmpty: Boolean get() = !hasRating && !hasVisits

    /** Both answers are in, whatever they hold: nothing more is coming that would change what can be shown. */
    val settled: Boolean get() = visits != null && ratings != null

    companion object {
        /** The fewest ratings the bar shows an average for. */
        const val MIN_RATINGS = 5

        /** The fewest visits the bar shows. */
        const val MIN_VISITS = 1000L
    }
}

/**
 * The words and numbers of the bar's proof chip, shared by the app and the static page so both say the same: the
 * average and the visits in bold, each with its muted word, and one sentence for a screen reader. Each can be written
 * at a [progress] of the way up from 0 (0..1), though the chip itself shows them final from its first frame.
 */
object ProofCopy {
    /** The average to one decimal, [progress] of the way up from 0.0: 4.8 at 1, "2.4" halfway. */
    fun average(value: Double, progress: Float = 1f): String = oneDecimal(value * progress.coerceIn(0f, 1f))

    /** The visits with grouped digits, [progress] of the way up from 0: "1,234" at 1, "617" halfway. */
    fun visits(total: Long, progress: Float = 1f): String =
        groupDigits((total.toDouble() * progress.coerceIn(0f, 1f)).roundToLong())

    /** After the average, muted: "· 1 rating", "· 14 ratings". */
    fun ratingCount(count: Int): String = "· $count ${if (count == 1) "rating" else "ratings"}"

    /** After the visits, muted: "visits" ("visit" for one). */
    fun visitsWord(total: Long): String = if (total == 1L) "visit" else "visits"

    /**
     * The chip as a screen reader hears it, the numbers in full however narrow the bar: "Rated 5.0 out of 5 from 1
     * rating, 35 visits. Read the reviews." Without a rating, the visits and "See the notes."; null with nothing to say.
     */
    fun label(stats: LiveStats): String? {
        val rating = stats.average?.let { mean ->
            "Rated ${average(mean)} out of 5 from ${stats.ratingCount} ${if (stats.ratingCount == 1) "rating" else "ratings"}"
        }
        val visits = if (stats.hasVisits) "${visits(stats.visitTotal)} ${visitsWord(stats.visitTotal)}" else null
        return when {
            rating != null && visits != null -> "$rating, $visits. $READ_REVIEWS"
            rating != null -> "$rating. $READ_REVIEWS"
            visits != null -> "$visits. $SEE_NOTES"
            else -> null
        }
    }

    private const val READ_REVIEWS = "Read the reviews."
    private const val SEE_NOTES = "See the notes."
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
    val match = IsoDay.matchEntire(day) ?: return null
    val month = match.groupValues[2].toInt().takeIf { it in 1..12 } ?: return null
    return "${Months[month - 1]} ${match.groupValues[1]}"
}

/** "2026-10-23" → "Oct 23, 2026"; null for anything that is not such a date. */
fun fullDate(day: String): String? {
    val match = IsoDay.matchEntire(day) ?: return null
    val month = match.groupValues[2].toInt().takeIf { it in 1..12 } ?: return null
    return "${Months[month - 1]} ${match.groupValues[3].toInt()}, ${match.groupValues[1]}"
}

private val Months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/**
 * A `YYYY-MM-DD` day: year, month and day as groups 1 to 3. Compiled once: [PublicNote.dateLine] runs in every note
 * card's composition, and a pattern built per call is compiled per call.
 */
private val IsoDay = Regex("""(\d{4})-(\d{2})-(\d{2})""")

/**
 * How a note's writer knows the owner, as the note form asks (optional). The star sheet sorts notes by it, and a
 * card shows it as a badge (except [Other]). The wire values are the backend's (`worker/src/handler.js`, `RELATIONS`).
 */
enum class Relation(val wire: String, val label: String, val plural: String) {
    Colleague("colleague", "Colleague", "Colleagues"),
    Friend("friend", "Friend", "Friends"),
    Special("special", "Special someone", "Special someone"),
    Other("other", "Other", "Others"),
    ;

    companion object {
        /**
         * The relations the note form and the sorter offer. "Special someone" is no longer one: on a page for
         * recruiters it invites notes that read as unprofessional (Stanford web credibility: suit the site's
         * purpose). A note that already says it still shows its badge.
         */
        val offered: List<Relation> get() = entries - Special

        /** The relation [wire] names, whatever its case and spaces; null for none, or for anything else. */
        fun of(wire: String?): Relation? = wire?.trim()?.lowercase()?.let { value -> entries.firstOrNull { it.wire == value } }
    }
}

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
    /**
     * That the note may be shown on the site, with the name and never the contact. The form says every note is
     * public, so it always sends yes; the backend still keeps the notes of the 1.0 forms that sent no private.
     * Always in the JSON, default or not: a note without it would be filed as private.
     */
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    val consent: Boolean = true,
    /** How they know the owner, a [Relation]'s wire value; empty for not saying, which the JSON leaves out. */
    val relation: String = "",
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
            if (relation.isNotBlank() && Relation.of(relation) == null) add("Pick one of the ways you know me.")
        }

    /** Trimmed, as the backend stores it; the relation lowercase. */
    fun normalized(): FeedbackDraft =
        FeedbackDraft(name.trim(), contact.trim(), rating, message.trim(), consent, relation.trim().lowercase())

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
    const val TITLE = "Feedback"
    const val PUBLIC = "Public"
    /** Under the title in the Contact section, over the button that opens the form. */
    const val INTRO = "Feedback as a friend, a colleague, someone I know, or anonymously."
    /** The button in the Contact section that opens the form in a dialog. */
    const val OPEN = "Give feedback"
    /** At the top of the form's dialog: what happens to a note, before anyone writes one. */
    const val FORM_INTRO = "A bug on this site, or a thought on my work: your note appears here as soon as you send " +
        "it, with your name if you give one. Leave the name empty to stay anonymous. Your email never shows."
    const val NAME = "Name"
    const val CONTACT = "Email"
    const val RATING = "Overall impression"
    const val MESSAGE = "Message"
    const val RELATION = "How do you know me?"
    const val PINNED = "Pinned"
    const val SEND = "Send note"
    const val SENDING = "Sending…"
    const val THANKS = "Thanks, your note is up."
    const val ANOTHER = "Write another"
    const val NOTES_TITLE = "Notes from colleagues and visitors"
    const val NO_NOTES = "No notes yet. Be the first to leave one below."
    const val MORE = "Show more notes"
    const val LOADING_MORE = "Loading…"
    const val DASHBOARD = "Ratings"

    /** "from 12 ratings" under the average. */
    fun ratingsCount(count: Int): String = "from $count ${if (count == 1) "rating" else "ratings"}"

    /** Under the bars, when some ratings came with words kept private. */
    fun keptPrivate(count: Int): String =
        if (count == 1) "Every rating counts. One came with a note sent privately, before notes were public: its stars are here, its words are not."
        else "Every rating counts. $count came with notes sent privately, before notes were public: their stars are here, their words are not."



    /** A bar as one spoken line: "5 stars: 18 ratings, 75 percent". */
    fun barLabel(stars: Int, count: Int, percent: String): String =
        "$stars ${if (stars == 1) "star" else "stars"}: $count ${if (count == 1) "rating" else "ratings"}, ${percent.removeSuffix("%")} percent"

    // The star sheet, which a dashboard bar opens.

    const val ALL_RELATIONS = "All"
    const val CLOSE = "Close"
    /** When the sheet's first page could not be loaded; [TRY_AGAIN] beside it asks again. */
    const val SHEET_FAILED = "These notes could not be loaded."
    const val TRY_AGAIN = "Try again"

    /** A bar's action, for screen readers: "Show the 5-star notes". */
    fun openSheet(stars: Int): String = "Show the $stars-star notes"

    /** The sheet's title: "5-star notes". */
    fun sheetTitle(stars: Int): String = "$stars-star notes"

    /** Under the title: "4 ratings · 1 note to read". */
    fun sheetSummary(ratings: Int, readable: Int): String =
        "$ratings ${if (ratings == 1) "rating" else "ratings"} · $readable ${if (readable == 1) "note" else "notes"} to read"

    /** At the end of a star's list, for the ratings whose words are private: their stars are counted, not shown. */
    fun privateInSheet(count: Int, stars: Int): String =
        "+$count private $stars-star ${if (count == 1) "rating" else "ratings"}: sent before notes were public, so only " +
            "${if (count == 1) "its stars are" else "their stars are"} here."

    /** When the sheet's pick has nothing to read: "No notes from colleagues with 5 stars yet." */
    fun noneInSheet(relation: Relation?, stars: Int): String {
        val from = when (relation) {
            null -> ""
            Relation.Colleague -> " from colleagues"
            Relation.Friend -> " from friends"
            Relation.Special -> " from a special someone"
            Relation.Other -> " from others"
        }
        return "No notes$from with $stars ${if (stars == 1) "star" else "stars"} yet."
    }

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
    /** The button in the Contact section that opens the form in a dialog. */
    const val OPEN = "Write a message"
    const val CLOSE = "Close"
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
