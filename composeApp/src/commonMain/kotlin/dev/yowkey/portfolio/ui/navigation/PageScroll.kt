package dev.yowkey.portfolio.ui.navigation

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.yowkey.portfolio.ui.theme.LocalReducedMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/** ↓ and ↑ move this far: about three lines of body text. */
private val LineStep = 72.dp

/** A line's glide: short, so a held arrow key runs smoothly from one press into the next. */
private const val LINE_MILLIS = 140

/** A page's glide (Space, Page Down): long enough to follow, short enough not to wait for. */
private const val PAGE_MILLIS = 320

/** Far enough into the last row to be past its end: the list then settles at its very bottom. */
private const val PAST_THE_END = Int.MAX_VALUE / 4

/** After a cut the page is already "moving", so the glide only slows down. */
private val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)

/**
 * Glides the list until row [index] sits [intoPx] scrolled past its top: a section jump, Home, Back to top. The
 * time scales with the distance and a far target is cut to first (see [ScrollRules.jump]), and the jump always
 * ends with the row exactly in place, whatever resized on the way. Instant under [reducedMotion]. A touch, the
 * wheel or another jump cancels it where it is.
 */
suspend fun LazyListState.glideToItem(index: Int, intoPx: Int = 0, reducedMotion: Boolean = false) {
    val info = layoutInfo
    val target = info.visibleItemsInfo.firstOrNull { it.index == index }
    glide(
        reducedMotion,
        // The end of the list may stop it short of the row's top (the last section); glide only as far as that.
        measured = target?.let { (it.offset + intoPx).toFloat().coerceAtMost(info.roomBelow()) },
        estimate = { info.estimateTo(index) + intoPx },
        land = { scrollToItem(index, intoPx) },
    )
}

/** Glides to the very bottom of the list (End), as [glideToItem] glides to a row. */
suspend fun LazyListState.glideToEnd(reducedMotion: Boolean = false) {
    val info = layoutInfo
    val lastIndex = info.totalItemsCount - 1
    val lastShown = info.visibleItemsInfo.lastOrNull() ?: return
    glide(
        reducedMotion,
        measured = if (lastShown.index == lastIndex) info.roomBelow() else null,
        estimate = { (lastIndex - lastShown.index) * info.averageRowPx() + lastShown.offset + lastShown.size - info.viewportEndOffset },
        land = { scrollToItem(lastIndex, PAST_THE_END) },
    )
    // The list keeps its top in place, so a row the jump composed for the first time that settles a pixel taller on
    // its next frame leaves the bottom just out of view (seen under reduced motion, where End is one cut): follow it
    // down once, unless the reader has scrolled meanwhile.
    val index = firstVisibleItemIndex
    val offset = firstVisibleItemScrollOffset
    withFrameNanos { }
    if (canScrollForward && firstVisibleItemIndex == index && firstVisibleItemScrollOffset == offset) {
        scrollToItem(lastIndex, PAST_THE_END)
    }
}

/**
 * The jump itself. [measured] is the exact distance when the target is on screen; otherwise [estimate] guesses it,
 * and the plan cuts first: [land] puts the target in place, the list steps back by the glide (in pixels, so the
 * glide that follows is exact), then glides in.
 */
private suspend fun LazyListState.glide(
    reducedMotion: Boolean,
    measured: Float?,
    estimate: () -> Float,
    land: suspend () -> Unit,
) {
    val screen = layoutInfo.screenPx()
    if (reducedMotion || screen <= 0) {
        land()
        return
    }
    val plan = ScrollRules.jump(measured ?: estimate(), measured = measured != null, screen.toFloat())
    var glide = plan.glidePx
    if (plan.cutFirst) {
        land()
        // Near either end there may be less room to step back than the glide asks for.
        glide = -scrollBy(-glide)
    }
    if (abs(glide) >= 1f) {
        animateScrollBy(glide, tween(plan.millis, easing = if (plan.cutFirst) EaseOutCubic else FastOutSlowInEasing))
    }
    land()
}

private fun LazyListLayoutInfo.screenPx(): Int = viewportEndOffset - viewportStartOffset

private fun LazyListLayoutInfo.averageRowPx(): Float =
    if (visibleItemsInfo.isEmpty()) 0f else visibleItemsInfo.sumOf { it.size }.toFloat() / visibleItemsInfo.size

private fun LazyListLayoutInfo.estimateTo(index: Int): Float {
    val first = visibleItemsInfo.firstOrNull() ?: return 0f
    return ScrollRules.estimateDistance(index, first.index, -first.offset, averageRowPx())
}

/** How much further the list can scroll down, when its last row shows; unknown (infinite) before that. */
private fun LazyListLayoutInfo.roomBelow(): Float {
    val last = visibleItemsInfo.lastOrNull()
    if (last == null || last.index != totalItemsCount - 1) return Float.POSITIVE_INFINITY
    return (last.offset + last.size + afterContentPadding - viewportEndOffset).coerceAtLeast(0).toFloat()
}

/**
 * Moves a scrollable by the reading keys' steps, gliding unless reduced motion asks for none. A press during a
 * glide adds its step to what that glide had left, so a held ↓ or two quick Spaces travel the whole way instead
 * of restarting short. A touch or the wheel stops it, and the next press starts afresh.
 */
internal class KeyGlide(private val state: ScrollableState, private val scope: CoroutineScope) {
    private var asked = 0f // pressed for, not yet taken up by a glide
    private var left = 0f // what the running glide has still to cover
    private var newest = 0 // the latest press: the glide it replaces leaves [asked] and [left] to it

    fun by(px: Float, millis: Int, reducedMotion: Boolean) {
        asked += px
        val press = ++newest
        scope.launch {
            try {
                state.scroll {
                    val total = left + asked
                    asked = 0f
                    left = total
                    if (reducedMotion) {
                        scrollBy(total)
                        left = 0f
                        return@scroll
                    }
                    var covered = 0f
                    AnimationState(0f).animateTo(total, tween(millis, easing = EaseOutCubic)) {
                        val step = value - covered
                        val used = scrollBy(step)
                        covered += used
                        left = total - covered
                        // At the top or the bottom: there is nothing left to cover.
                        if (abs(step - used) > 0.5f) {
                            left = 0f
                            cancelAnimation()
                        }
                    }
                }
            } finally {
                // Stopped by a touch, the wheel or a jump rather than by a newer press: nothing carries over.
                if (press == newest) {
                    asked = 0f
                    left = 0f
                }
            }
        }
    }
}

/**
 * The page's reading keys and Back to top, and where the focus is for them. Remembered with the list's state above
 * the list/detail switch, so a return from a project finds both as they were.
 */
@Stable
class PageReading(private val list: LazyListState, private val scope: CoroutineScope) {
    /** The page's frame ([ReadingFrame]): focused whenever nothing in it is, since keys only go to the focus and its ancestors. */
    val focus = FocusRequester()
    private val keys = KeyGlide(list, scope)
    internal var hasFocus = false

    /** Glides to row [index], [intoPx] past its top (see [glideToItem]). */
    fun jumpTo(index: Int, intoPx: Int, reducedMotion: Boolean) {
        scope.launch { list.glideToItem(index, intoPx, reducedMotion) }
    }

    /** Back to the top, with the focus on the page: the button it came from is about to go. */
    fun backToTop(reducedMotion: Boolean) {
        jumpTo(0, 0, reducedMotion)
        focus.requestFocus()
    }

    internal fun onKey(event: KeyEvent, linePx: Float, reducedMotion: Boolean): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val screen = list.layoutInfo.screenPx().toFloat()
        val move = ScrollRules.move(
            event.key, event.isShiftPressed, event.isCtrlPressed, event.isAltPressed, event.isMetaPressed, screen, linePx,
        ) ?: return false
        when (move) {
            is ScrollRules.Move.By -> keys.by(move.px, if (abs(move.px) > linePx) PAGE_MILLIS else LINE_MILLIS, reducedMotion)
            ScrollRules.Move.Top -> jumpTo(0, 0, reducedMotion)
            ScrollRules.Move.Bottom -> scope.launch { list.glideToEnd(reducedMotion) }
        }
        return true
    }
}

@Composable
fun rememberPageReading(list: LazyListState): PageReading {
    val scope = rememberCoroutineScope()
    return remember(list, scope) { PageReading(list, scope) }
}

/**
 * The page's frame for [reading]. It takes the focus as the page shows, and handles the reading keys nothing inside
 * it used: Compose hands a key to the focused node first and then to its ancestors, so a focused button keeps Space
 * and a text field keeps everything. A click on the page itself, on nothing that takes clicks, brings back a focus
 * that went elsewhere, so the keys read again. The forms are in dialogs over the page, so no field is ever in it;
 * [enabled] is false while something covers the page.
 */
@Composable
fun ReadingFrame(
    reading: PageReading,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val reducedMotion = LocalReducedMotion.current
    val linePx = with(LocalDensity.current) { LineStep.toPx() }
    val active by rememberUpdatedState(enabled)
    Box(
        modifier
            .fillMaxSize()
            .onFocusChanged { reading.hasFocus = it.hasFocus }
            .focusRequester(reading.focus)
            .focusTarget()
            .onKeyEvent { event -> active && reading.onKey(event, linePx, reducedMotion) }
            .pointerInput(reading) {
                awaitEachGesture {
                    // The final pass: whatever was under the pointer has had it, and consumed it if it took the click.
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                    if (down.isConsumed || reading.hasFocus) return@awaitEachGesture
                    waitForUpOrCancellation(PointerEventPass.Final) ?: return@awaitEachGesture
                    // Unless the click itself gave the page the focus back, bring it back.
                    if (!reading.hasFocus) reading.focus.requestFocus()
                }
            },
        content = content,
    )
    LaunchedEffect(reading) { reading.focus.requestFocus() }
}

/**
 * Whether Back to top should show for [list] ([ScrollRules.backToTopShows]). Worked out on every scroll frame, but
 * the state only changes when the answer flips, so its reader never recomposes while the page scrolls.
 */
@Composable
fun rememberBackToTopShown(list: LazyListState): State<Boolean> {
    val shown = remember(list) { mutableStateOf(false) }
    LaunchedEffect(list) {
        snapshotFlow { list.layoutInfo.scrolledScreens() }
            .collect { screens -> shown.value = ScrollRules.backToTopShows(screens, shown.value) }
    }
    return shown
}

private fun LazyListLayoutInfo.scrolledScreens(): Float {
    val first = visibleItemsInfo.firstOrNull() ?: return 0f
    return ScrollRules.scrolledScreens(first.index, -first.offset, averageRowPx(), screenPx())
}
