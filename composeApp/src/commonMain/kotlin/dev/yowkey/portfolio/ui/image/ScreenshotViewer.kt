package dev.yowkey.portfolio.ui.image

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.yowkey.portfolio.model.Screenshot
import dev.yowkey.portfolio.ui.theme.LocalMotion
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/**
 * What the viewer is showing: one project's screenshots, opened at [start]. [origin] is the on-screen bounds of the
 * slide that was tapped, so the viewer can grow out of it; null opens from the centre.
 */
@Immutable
data class ViewerRequest(val title: String, val screenshots: List<Screenshot>, val start: Int, val origin: Rect? = null)

private val Scrim = Color(0xF20B0D10)
private val OnScrim = Color(0xFFECEDEF)
private val OnScrimMuted = Color(0xFFB4BAC4)

/**
 * Full-screen screenshot viewer: swipe or arrow keys to page, Escape / Close / a tap on the backdrop to leave.
 * The visible page and one neighbour each side are composed, so swiping lands on an already-decoded image (the
 * full-size cache holds exactly those three).
 *
 * Opening zooms the image out of the tapped slide (scale and offset from [ViewerRequest.origin]) while the scrim
 * fades in; closing plays it back. The thumbnail is drawn underneath until the full image arrives, so the zoom
 * never shows an empty frame. All of it runs in the layer and draw phases from one Animatable.
 */
@Composable
fun ScreenshotViewer(request: ViewerRequest, onDismiss: () -> Unit) {
    val shots = request.screenshots
    val pager = rememberPagerState(initialPage = request.start) { shots.size }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val images = LocalScreenshotImages.current
    val motion = LocalMotion.current
    val shown = remember { Animatable(0f) }
    val closing = remember { booleanArrayOf(false) }
    fun go(delta: Int) {
        val target = (pager.currentPage + delta).coerceIn(0, shots.lastIndex)
        scope.launch { pager.animateScrollToPage(target, animationSpec = motion.page()) }
    }
    fun dismiss() {
        if (closing[0]) return
        closing[0] = true
        scope.launch {
            shown.animateTo(0f, motion.exit())
            onDismiss()
        }
    }

    Dialog(onDismissRequest = ::dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(Unit) { shown.animateTo(1f, motion.enter()) }
        Column(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(Scrim, alpha = shown.value) }
                .focusRequester(focus)
                // Before focusable, so the keys reach this node while the viewer holds focus.
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionRight -> go(1).let { true }
                        Key.DirectionLeft -> go(-1).let { true }
                        Key.Escape -> dismiss().let { true }
                        else -> false
                    }
                }
                .focusable(),
        ) {
            val chrome = Modifier.graphicsLayer { alpha = shown.value }
            Row(
                chrome.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    request.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = OnScrim,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
                TextButton(onClick = ::dismiss) { Text("Close", color = OnScrim) }
            }
            HorizontalPager(
                state = pager,
                key = { shots[it].file },
                beyondViewportPageCount = 1,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                val shot = shots[page]
                val bounds = remember { arrayOfNulls<Rect>(1) }
                Box(
                    Modifier
                        .fillMaxSize()
                        .onPlaced { bounds[0] = it.boundsInWindow() }
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = ::dismiss)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .graphicsLayer {
                            val p = shown.value
                            val origin = request.origin
                            val box = bounds[0]
                            val swipe = pager.getOffsetDistanceInPages(page).coerceIn(-1f, 1f)
                            if (page == request.start && origin != null && box != null && !motion.reduced) {
                                // Grow out of the tapped slide: start at its size and centre, end in place.
                                val fitted = min(box.width, box.height * shot.aspectRatio)
                                val start = (origin.width / fitted).coerceIn(0.1f, 1f)
                                val scale = start + (1f - start) * p
                                scaleX = scale
                                scaleY = scale
                                translationX = (origin.center.x - box.center.x) * (1f - p)
                                translationY = (origin.center.y - box.center.y) * (1f - p)
                                alpha = (p * 4f).coerceAtMost(1f)
                            } else if (!motion.reduced) {
                                val scale = (0.94f + 0.06f * p) * (1f - 0.08f * abs(swipe))
                                scaleX = scale
                                scaleY = scale
                                alpha = p * (1f - 0.5f * abs(swipe))
                            } else {
                                alpha = p
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    ScreenshotImage(
                        path = shot.fullPath,
                        contentDescription = shot.caption,
                        loader = images?.fullSize,
                        contentScale = ContentScale.Fit,
                        previewPath = shot.thumbPath,
                        previewLoader = images?.thumbnails,
                        modifier = Modifier
                            .aspectRatio(shot.aspectRatio, matchHeightConstraintsFirst = shot.aspectRatio < 1f)
                            .clip(RoundedCornerShape(12.dp))
                            // Taps on the image itself should not close the viewer.
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                    )
                }
            }
            Column(
                chrome.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    shots[pager.currentPage].caption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnScrim,
                    modifier = Modifier.widthIn(max = 640.dp),
                )
                if (shots.size > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TextButton(onClick = { go(-1) }, enabled = pager.currentPage > 0) { Text("Previous", color = OnScrim) }
                        Text("${pager.currentPage + 1} / ${shots.size}", style = MaterialTheme.typography.labelLarge, color = OnScrimMuted)
                        TextButton(onClick = { go(1) }, enabled = pager.currentPage < shots.lastIndex) { Text("Next", color = OnScrim) }
                    }
                }
            }
        }
        // Inside the dialog, so the requester is attached to its window before focus is asked for.
        LaunchedEffect(Unit) { focus.requestFocus() }
    }
}

