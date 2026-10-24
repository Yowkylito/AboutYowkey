package dev.yowkey.portfolio.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

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

    /**
     * Whether a row [size] px tall at [offset] stays above the bottom [keepClearPx] of a viewport ending at
     * [viewportEnd]: the corner where Back to top floats. On the web a playing cover is an HTML `<video>` laid over
     * the canvas, which Compose cannot draw on top of, so a cover that could pass under the button does not play
     * while the button shows; it starts once its row has risen clear. Nothing to keep clear (0) clears every row.
     */
    fun clearOf(offset: Int, size: Int, viewportEnd: Int, keepClearPx: Int): Boolean =
        keepClearPx <= 0 || offset + size <= viewportEnd - keepClearPx

    /** The share of an item [size] px tall at [offset] that lies inside the viewport [viewportStart, viewportEnd). */
    fun visibleFraction(offset: Int, size: Int, viewportStart: Int, viewportEnd: Int): Float {
        if (size <= 0) return 0f
        val shown = minOf(offset + size, viewportEnd) - maxOf(offset, viewportStart)
        return (shown.coerceAtLeast(0).toFloat() / size).coerceAtMost(1f)
    }
}

/**
 * The ids of the covers in [listState]'s rows ([items]) that should play now: [CoverPlayback.select] over the rows on
 * screen, none unless [active], and none in a row reaching into the bottom [keepClearPx] (see [CoverPlayback.clearOf]).
 * Worked out on every scroll frame (a few visible rows), but readers hear of it only when the set of ids changes.
 * [keepClearPx] is read as it is now: the page passes a new one when that corner moves or the page is covered, and the
 * first one, kept, would go on clearing a corner that has moved.
 */
@Composable
internal fun rememberPlayingCovers(
    listState: LazyListState,
    items: List<PageItem>,
    active: Boolean,
    keepClearPx: () -> Int,
): State<Set<String>> {
    val keepClear by rememberUpdatedState(keepClearPx)
    return remember(listState, items, active) {
        derivedStateOf {
            if (!active) return@derivedStateOf emptySet()
            val info = listState.layoutInfo
            CoverPlayback.select(
                info.visibleItemsInfo.mapNotNull { visible ->
                    val row = items.getOrNull(visible.index) as? PageItem.Projects ?: return@mapNotNull null
                    val clear = CoverPlayback.clearOf(visible.offset, visible.size, info.viewportEndOffset, keepClear())
                    CoverPlayback.VisibleRow(
                        coverClipIds = row.projects.filter { it.coverClip != null }.map { it.id },
                        visibleFraction = if (!clear) 0f else CoverPlayback.visibleFraction(
                            visible.offset, visible.size, info.viewportStartOffset, info.viewportEndOffset,
                        ),
                    )
                },
            )
        }
    }
}
