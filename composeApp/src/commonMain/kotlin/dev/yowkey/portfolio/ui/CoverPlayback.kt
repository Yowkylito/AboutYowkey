package dev.yowkey.portfolio.ui

/**
 * Which card covers may play. Decoding is the expensive part of a clip (7–8 ms a frame on the web), so only covers
 * on screen play, the most visible first, and never more than [MAX_PLAYING_COVERS] at once. Pure, so the rules are
 * unit-tested; the page feeds it its lazy list's layout info.
 */
object CoverPlayback {

    /**
     * Two, measured in Chrome 156: [dev.yowkey.portfolio.ui.image.ClipDecodeGate] lets only one clip decode in any
     * frame, so a second cover adds frames that decode, not longer ones; the worst frame stays that of the dearer
     * clip alone (see READINESS.md, "Cover clips").
     */
    const val MAX_PLAYING_COVERS = 2

    /** One lazy row on screen: the ids of its projects that have a cover clip, and how much of the row shows. */
    data class VisibleRow(val coverClipIds: List<String>, val visibleFraction: Float)

    /**
     * The ids that should play: rows with any of the row on screen, most visible first (ties keep page order,
     * then card order), cut to [max]. Both cards of a two-column row share its fraction, so a fully visible row
     * of two plays both.
     */
    fun select(rows: List<VisibleRow>, max: Int = MAX_PLAYING_COVERS): Set<String> =
        rows.withIndex()
            .filter { (_, row) -> row.visibleFraction > 0f && row.coverClipIds.isNotEmpty() }
            .sortedWith(compareByDescending<IndexedValue<VisibleRow>> { it.value.visibleFraction }.thenBy { it.index })
            .flatMap { it.value.coverClipIds }
            .take(max)
            .toSet()

    /** The share of an item [size] px tall at [offset] that lies inside the viewport [viewportStart, viewportEnd). */
    fun visibleFraction(offset: Int, size: Int, viewportStart: Int, viewportEnd: Int): Float {
        if (size <= 0) return 0f
        val shown = minOf(offset + size, viewportEnd) - maxOf(offset, viewportStart)
        return (shown.coerceAtLeast(0).toFloat() / size).coerceAtMost(1f)
    }
}
