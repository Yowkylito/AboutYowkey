package dev.yowkey.portfolio.text

/** How strongly a fact is marked: [Strong] for headline wins (53%, 2.4x, #1, +270K), [Figure] for plain counts. */
enum class EmphasisKind { Strong, Figure }

/** [start] inclusive, [end] exclusive, as in [String.substring]. */
data class EmphasisSpan(val start: Int, val end: Int, val kind: EmphasisKind)

/**
 * Finds the numeric facts in running text so the UI and the static page can make them stand out. Pure and
 * platform-free: the Compose side turns spans into an AnnotatedString, the static page into `<strong>`.
 *
 * Not facts, and so never marked: years (2026), versions (SDK 5.8: a number after an all-caps word), and digits
 * inside identifiers (V2, P0, ESP8266, :v2, 3D).
 */
object Emphasis {

    private val candidate = Regex(
        """(?:(?:Rank|Top)\s+)#?\d+""" + // Rank #2, Top 9
            """|#\d+""" + // #1
            """|\+?\d+(?:,\d{3})*(?:\.\d+)?(?:[KMB](?![A-Za-z])|%|x(?![A-Za-z])|×)?""", // +270K, 53%, 2.4x, 72.8, 450
    )

    private val markers = setOf('%', 'x', '×', 'K', 'M', 'B')

    fun spans(text: String): List<EmphasisSpan> = candidate.findAll(text).mapNotNull { match ->
        val start = match.range.first
        val end = match.range.last + 1
        val token = match.value
        if (insideIdentifier(text, start) || text.getOrNull(end)?.isLetter() == true || afterVersionWord(text, start)) {
            return@mapNotNull null
        }
        val strong = token.startsWith('#') || token.startsWith('+') || token.startsWith("Rank") ||
            token.startsWith("Top") || token.last() in markers
        when {
            strong -> EmphasisSpan(start, end, EmphasisKind.Strong)
            isYear(token) -> null
            else -> EmphasisSpan(start, end, EmphasisKind.Figure)
        }
    }.toList()

    /** [text] cut into runs, each either plain (null) or marked; joined they equal [text]. */
    fun segments(text: String): List<Pair<String, EmphasisKind?>> {
        val result = ArrayList<Pair<String, EmphasisKind?>>()
        var cursor = 0
        spans(text).forEach { span ->
            if (span.start > cursor) result += text.substring(cursor, span.start) to null
            result += text.substring(span.start, span.end) to span.kind
            cursor = span.end
        }
        if (cursor < text.length) result += text.substring(cursor) to null
        return result
    }

    private fun insideIdentifier(text: String, start: Int): Boolean {
        val before = text.getOrNull(start - 1) ?: return false
        return before.isLetterOrDigit() || before in ".:_/-"
    }

    /** "SDK 5.8", "API 34": a number right after an all-caps word is a version or a level, not a result. */
    private fun afterVersionWord(text: String, start: Int): Boolean {
        if (text.getOrNull(start - 1) != ' ') return false
        val word = text.substring(0, start - 1).takeLastWhile { it.isLetter() }
        return word.length >= 2 && word.all { it.isUpperCase() }
    }

    private fun isYear(token: String): Boolean = token.length == 4 && token.toIntOrNull() in 1900..2099
}
