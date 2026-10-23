package dev.yowkey.portfolio.ui.navigation

import androidx.compose.ui.input.key.Key
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * How the page moves for its reader: the reading keys, the section jumps and Back to top. Pure, so the rules are
 * unit-tested; `PageScroll.kt` feeds them the list's layout and runs the motion.
 */
object ScrollRules {

    /** Space and Page Down move this share of the screen, so the last lines read stay in view as context. */
    const val PAGE_SHARE = 0.85f

    /** A jump glides at most this many screens; a longer one first cuts to this far from its target. */
    const val MAX_GLIDE_SCREENS = 1.5f

    /** The shortest glide, so a jump of a few lines still reads as motion rather than a snap. */
    const val MIN_GLIDE_MILLIS = 240

    /** A full [MAX_GLIDE_SCREENS] glide: however far the target, a jump never takes longer, so it never crawls. */
    const val MAX_GLIDE_MILLIS = 560

    /** Back to top shows once the reader is this many screens down... */
    const val BACK_TO_TOP_SHOW_SCREENS = 1.5f

    /** ...and stays until they are back above this, so scrolling to and fro around one line never flickers it. */
    const val BACK_TO_TOP_HIDE_SCREENS = 1f

    /** What a reading key asks of the page. */
    sealed interface Move {
        /** Scroll by [px], down when positive. */
        data class By(val px: Float) : Move

        data object Top : Move

        data object Bottom : Move
    }

    /**
     * The move for a key pressed with nothing editable focused, on a screen [screenPx] tall, or null for a key the
     * page leaves alone. Space and Page Down go down a page ([PAGE_SHARE]), Shift+Space and Page Up up one; Home and
     * End (Ctrl too, as on Windows) go to the top and the bottom, as Cmd+↑ and Cmd+↓ do on a Mac; ↓ and ↑ move
     * [linePx]. With Alt, or Ctrl or Cmd on the other keys, it is the browser's or the system's shortcut, not ours.
     */
    fun move(key: Key, shift: Boolean, ctrl: Boolean, alt: Boolean, meta: Boolean, screenPx: Float, linePx: Float): Move? {
        if (alt) return null
        val page = screenPx * PAGE_SHARE
        val plain = !ctrl && !meta
        return when (key) {
            Key.Spacebar -> if (plain) Move.By(if (shift) -page else page) else null
            Key.PageDown -> if (plain) Move.By(page) else null
            Key.PageUp -> if (plain) Move.By(-page) else null
            Key.MoveHome -> Move.Top
            Key.MoveEnd -> Move.Bottom
            Key.DirectionDown -> when {
                meta -> Move.Bottom
                ctrl -> null
                else -> Move.By(linePx)
            }
            Key.DirectionUp -> when {
                meta -> Move.Top
                ctrl -> null
                else -> Move.By(-linePx)
            }
            else -> null
        }
    }

    /** How a jump gets there: with [cutFirst], the page first cuts to [glidePx] short of the target; then it glides [glidePx] in [millis]. */
    data class Jump(val cutFirst: Boolean, val glidePx: Float, val millis: Int)

    /**
     * The plan for a jump of [distancePx] (up when negative) on a screen [screenPx] tall. [measured] says the target
     * is on screen, so the distance is exact; otherwise it is an estimate. A measured jump within
     * [MAX_GLIDE_SCREENS] glides the whole way. Anything else cuts to at most that far out first, so the glide that
     * follows is exact (it starts from where the cut put the target) and never longer than a full glide.
     */
    fun jump(distancePx: Float, measured: Boolean, screenPx: Float): Jump {
        val reach = MAX_GLIDE_SCREENS * screenPx.coerceAtLeast(0f)
        val glide = distancePx.coerceIn(-reach, reach)
        return Jump(cutFirst = !measured || abs(distancePx) > reach, glidePx = glide, millis = glideMillis(abs(glide), screenPx))
    }

    /**
     * How long a glide of [distancePx] takes: [MIN_GLIDE_MILLIS] for a nudge, growing with the square root of the
     * distance (a far glide moves faster, as a thrown page does), up to [MAX_GLIDE_MILLIS] for a full one.
     */
    fun glideMillis(distancePx: Float, screenPx: Float): Int {
        if (screenPx <= 0f) return MIN_GLIDE_MILLIS
        val share = (abs(distancePx) / (MAX_GLIDE_SCREENS * screenPx)).coerceIn(0f, 1f)
        return (MIN_GLIDE_MILLIS + (MAX_GLIDE_MILLIS - MIN_GLIDE_MILLIS) * sqrt(share)).roundToInt()
    }

    /**
     * The distance to the top of row [index] when it is off screen: the rows between the first visible one
     * ([firstIndex], scrolled [firstScrolledPx] into) and it, counted at the visible rows' [averageRowPx].
     */
    fun estimateDistance(index: Int, firstIndex: Int, firstScrolledPx: Int, averageRowPx: Float): Float =
        (index - firstIndex) * averageRowPx - firstScrolledPx

    /**
     * How far down the list is, in screens: exact while its first row shows; past it, the rows above the first
     * visible one counted at the visible rows' [averageRowPx]. Close enough for "more than a screen and a half".
     */
    fun scrolledScreens(firstIndex: Int, firstScrolledPx: Int, averageRowPx: Float, screenPx: Int): Float =
        if (screenPx <= 0) 0f else (firstIndex * averageRowPx + firstScrolledPx) / screenPx

    /** Whether Back to top shows [scrolledScreens] down, given whether it is [showing] now (see [BACK_TO_TOP_HIDE_SCREENS]). */
    fun backToTopShows(scrolledScreens: Float, showing: Boolean): Boolean =
        scrolledScreens > if (showing) BACK_TO_TOP_HIDE_SCREENS else BACK_TO_TOP_SHOW_SCREENS
}
