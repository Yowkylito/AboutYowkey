package dev.yowkey.portfolio.admin

import androidx.compose.runtime.Immutable

/** The Messages screen's filters. "All" leaves out the archived ones, which have a filter of their own. */
enum class MessageFilter(val label: String) {
    All("All"),
    Hiring("Hiring"),
    Unread("Unread"),
    Archived("Archived");

    fun matches(message: AdminMessage): Boolean = when (this) {
        All -> !message.isArchived
        Hiring -> message.isHiring && !message.isArchived
        Unread -> !message.isRead && !message.isArchived
        Archived -> message.isArchived
    }
}

/**
 * The message as it is once the worker has done [action] at [now] (ISO 8601), so the list can show it without a
 * reload; null once deleted.
 */
fun AdminMessage.after(action: MessageAction, now: String): AdminMessage? = when (action) {
    MessageAction.Read -> copy(readAt = readAt ?: now)
    MessageAction.Unread -> copy(readAt = null)
    MessageAction.Archive -> copy(archivedAt = now)
    MessageAction.Unarchive -> copy(archivedAt = null)
    MessageAction.Delete -> null
}

/** The note as it is once the worker has done [action] at [now], by the inbox's rules; null once deleted. */
fun AdminNote.after(action: NoteAction, now: String): AdminNote? = when (action) {
    // Publishing also brings a hidden note back, as the worker does.
    NoteAction.Publish -> copy(publishedAt = now, hiddenAt = null)
    NoteAction.Unpublish -> copy(publishedAt = null)
    NoteAction.Hide -> copy(hiddenAt = now)
    NoteAction.Unhide -> copy(hiddenAt = null)
    NoteAction.Delete -> null
}

/** One bar of the dashboard's visits chart. */
@Immutable
data class DayVisits(val day: String, val visits: Int, val isToday: Boolean)

/**
 * The [count] days up to and including [today] (`YYYY-MM-DD`, the owner's day), oldest first, each with its visits
 * from [days] or zero: `GET /admin/days` leaves out the days with no data at all.
 */
fun visitSeries(days: List<DayStats>, today: String, count: Int = 30): List<DayVisits> {
    val end = epochDay(today) ?: return days.take(count).reversed().map { DayVisits(it.day, it.visits, it.day == today) }
    val byDay = days.associate { it.day to it.visits }
    return (count - 1 downTo 0).map { back ->
        val day = isoDay(end - back)
        DayVisits(day, byDay[day] ?: 0, back == 0)
    }
}

/** The countries the visits came from over [days], most first, at most [limit]; ties in alphabetical order. */
fun topCountries(days: List<DayStats>, limit: Int = 5): List<CountryCount> =
    days.flatMap { it.countries }
        .filter { it.country.isNotBlank() }
        .groupBy { it.country.uppercase() }
        .map { (country, counts) -> CountryCount(country, counts.sumOf { it.count }) }
        .sortedWith(compareByDescending<CountryCount> { it.count }.thenBy { it.country })
        .take(limit)

/** The flag for a two-letter country code ("PH" → 🇵🇭); empty for Cloudflare's "XX"/"T1" and anything else. */
fun flagOf(country: String): String {
    val code = country.uppercase()
    if (code.length != 2 || code.any { it !in 'A'..'Z' } || code == "XX") return ""
    return buildString {
        code.forEach { letter ->
            val point = 0x1F1E6 + (letter - 'A')
            append(Char(0xD800 + ((point - 0x10000) shr 10)))
            append(Char(0xDC00 + ((point - 0x10000) and 0x3FF)))
        }
    }
}

/** What one run of the backup check does: the alerts to show, oldest first, and where the next run starts. */
@Immutable
data class PollPlan(val show: List<AdminNotification>, val cursor: Long)

/**
 * The backup check's bookkeeping. [cursor] is the newest record id already handled, null before the first run;
 * [fetched] is what `GET /admin/notifications` answered (with `after = cursor`, or without it on the first run);
 * [shown] the ids a push already put up, so a record pushed and dismissed does not come back.
 *
 * The first run only notes where the records stand: alerts from before the app was signed in are not news.
 */
fun planPoll(cursor: Long?, fetched: List<AdminNotification>, shown: Set<Long>): PollPlan {
    val newest = fetched.maxOfOrNull { it.id }
    if (cursor == null) return PollPlan(emptyList(), newest ?: 0)
    val fresh = fetched.filter { it.id > cursor && it.id !in shown }.sortedBy { it.id }
    return PollPlan(fresh, maxOf(cursor, newest ?: cursor))
}

/** [ids] with [id] added at the end, keeping the newest [keep]: the record of which alerts are up already. */
fun rememberShown(ids: List<Long>, id: Long, keep: Int = 200): List<Long> = (ids.filter { it != id } + id).takeLast(keep)

/** Days since 1970-01-01 for `YYYY-MM-DD`; null when it is not one. (Howard Hinnant's `days_from_civil`.) */
internal fun epochDay(iso: String): Long? {
    val parts = iso.trim().take(10).split('-')
    if (parts.size != 3) return null
    val y0 = parts[0].toLongOrNull() ?: return null
    val m = parts[1].toLongOrNull()?.takeIf { it in 1..12 } ?: return null
    val d = parts[2].toLongOrNull()?.takeIf { it in 1..31 } ?: return null
    val y = if (m <= 2) y0 - 1 else y0
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097 + doe - 719468
}

/** `YYYY-MM-DD` for days since 1970-01-01. (Howard Hinnant's `civil_from_days`.) */
internal fun isoDay(epochDay: Long): String {
    val z = epochDay + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    val y = yoe + era * 400 + if (m <= 2) 1 else 0
    return "${y.toString().padStart(4, '0')}-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
}
