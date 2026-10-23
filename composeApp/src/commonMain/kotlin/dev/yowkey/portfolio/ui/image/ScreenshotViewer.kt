package dev.yowkey.portfolio.ui.image

import androidx.compose.foundation.background
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.yowkey.portfolio.model.GalleryItem
import dev.yowkey.portfolio.ui.theme.LocalReducedMotion
import kotlinx.coroutines.launch

/** What the viewer is showing: one project's gallery (cover → clips → screenshots), opened at [start]. */
@Immutable
data class ViewerRequest(val title: String, val items: List<GalleryItem>, val start: Int)

private val Scrim = Color(0xF20B0D10)
private val OnScrim = Color(0xFFECEDEF)
private val OnScrimMuted = Color(0xFFB4BAC4)

/**
 * Full-screen screenshot viewer: swipe or arrow keys to page, Escape / Close / a tap on the backdrop to leave.
 * Only the visible page and its neighbours are composed, so only they load full-size images. A clip plays only
 * on the settled page and not while a swipe is moving (its decoding would compete with the swipe's frames), and
 * never under reduced motion; paging away stops its decoding.
 */
@Composable
fun ScreenshotViewer(request: ViewerRequest, onDismiss: () -> Unit) {
    val shots = request.items
    val pager = rememberPagerState(initialPage = request.start) { shots.size }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val reducedMotion = LocalReducedMotion.current
    fun go(delta: Int) {
        val target = (pager.currentPage + delta).coerceIn(0, shots.lastIndex)
        scope.launch { pager.animateScrollToPage(target) }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Scrim)
                .focusRequester(focus)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionRight -> go(1).let { true }
                        Key.DirectionLeft -> go(-1).let { true }
                        Key.Escape -> onDismiss().let { true }
                        else -> false
                    }
                },
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    request.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = OnScrim,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
                TextButton(onClick = onDismiss) { Text("Close", color = OnScrim) }
            }
            HorizontalPager(
                state = pager,
                key = { shots[it].shot.file },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                val item = shots[page]
                val shot = item.shot
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ViewerMediaImage(
                        item = item,
                        playing = !reducedMotion && pager.settledPage == page && !pager.isScrollInProgress,
                        modifier = Modifier
                            .aspectRatio(shot.aspectRatio, matchHeightConstraintsFirst = shot.aspectRatio < 1f)
                            .clip(RoundedCornerShape(12.dp))
                            // Taps on the image itself should not close the viewer.
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                    )
                }
            }
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    shots[pager.currentPage].shot.caption,
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
