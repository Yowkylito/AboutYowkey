package dev.yowkey.portfolio.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * The theme change as the new colours spreading out from the switch, not a flip. [themeReveal] keeps the page's
 * last frame in a layer; [start] turns that frame into a picture, swaps the theme under it, then cuts a growing
 * circle out of the picture, centred on the switch, until none of it is left. A soft band of the accent rides the
 * circle's edge and fades as it goes. Under reduced motion nothing sweeps across the page: the old picture fades.
 *
 * A press during a reveal starts over from what is on screen then, half old and half new, so it never jumps.
 */
@Stable
class ThemeReveal internal constructor(
    internal val layer: GraphicsLayer,
    private val scope: CoroutineScope,
) {
    internal var old by mutableStateOf<ImageBitmap?>(null)
        private set
    internal var origin = Offset.Zero
        private set
    internal var fade = false
        private set
    internal val progress = Animatable(0f)
    internal val hole = Path()
    private var running: Job? = null
    private var early: Pair<ImageBitmap, TimeMark>? = null

    /**
     * Takes the page's frame as the switch goes down, so the change can start the moment it comes up: turning the
     * frame into a picture costs a few frames on the web, which a press hides and a release would not. Skipped
     * mid-reveal, where the frame would be out of date by the release.
     */
    fun prepare() {
        if (old != null || layer.size == IntSize.Zero) return
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            early = layer.toImageBitmap() to TimeSource.Monotonic.markNow()
        }
    }

    /** Lets go of the frame [prepare] took, for a press that ended without a switch. */
    fun drop() {
        early = null
    }

    /** Swaps the theme with [swap] and plays the change from [from], in the page's coordinates. */
    fun start(from: Offset, reducedMotion: Boolean, swap: () -> Unit) {
        running?.cancel()
        val taken = early?.takeIf { (_, at) -> at.elapsedNow() < EarlyFor }?.first
        early = null
        if (layer.size == IntSize.Zero) {
            swap()
            return
        }
        // Undispatched, so the theme is swapped under the picture within the release, before anything redraws.
        running = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val picture = taken ?: layer.toImageBitmap()
            origin = from
            fade = reducedMotion
            progress.snapTo(0f)
            old = picture
            swap()
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(if (reducedMotion) FadeMillis else SpreadMillis, easing = FastOutSlowInEasing),
            )
            old = null
        }
    }
}

@Composable
fun rememberThemeReveal(): ThemeReveal {
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    return remember(layer, scope) { ThemeReveal(layer, scope) }
}

/**
 * Draws the page through [reveal]'s layer, so its last frame is always at hand, with whatever is left of the old
 * theme on top. [edge] colours the band on the spreading circle's rim: the new theme's accent.
 */
fun Modifier.themeReveal(reveal: ThemeReveal, edge: Color): Modifier = drawWithContent {
    val old = reveal.old
    val progress = reveal.progress.value
    // Taken out here: asking the recording scope for its density loops between the two scopes until the stack
    // overflows.
    val band = EdgeBand.toPx()
    val radius = progress * coverRadius(reveal.origin, size)
    reveal.layer.record {
        this@drawWithContent.drawContent()
        when {
            old == null -> Unit
            reveal.fade -> drawImage(old, alpha = 1f - progress)
            else -> drawSpread(reveal, old, radius, band, progress, edge)
        }
    }
    drawLayer(reveal.layer)
}

private fun DrawScope.drawSpread(
    reveal: ThemeReveal,
    old: ImageBitmap,
    radius: Float,
    band: Float,
    progress: Float,
    edge: Color,
) {
    reveal.hole.rewind()
    reveal.hole.addOval(Rect(reveal.origin, radius))
    clipPath(reveal.hole, ClipOp.Difference) { drawImage(old) }
    if (radius > 0f) {
        drawCircle(
            brush = Brush.radialGradient(
                max(0f, 1f - band / radius) to Color.Transparent,
                1f to edge.copy(alpha = EdgeAlpha * (1f - progress)),
                center = reveal.origin,
                radius = radius,
            ),
            radius = radius,
            center = reveal.origin,
        )
    }
}

/** How far a circle centred on [origin] has to grow to cover all of [size]: to its farthest corner. */
internal fun coverRadius(origin: Offset, size: Size): Float {
    val dx = max(origin.x, size.width - origin.x)
    val dy = max(origin.y, size.height - origin.y)
    return hypot(dx, dy)
}

private const val SpreadMillis = 500
private const val FadeMillis = 260
// A tap is down for a tenth of a second or so; a frame older than this is stale by the release, so it is taken again.
private val EarlyFor = 400.milliseconds
private const val EdgeAlpha = 0.28f
private val EdgeBand = 56.dp
