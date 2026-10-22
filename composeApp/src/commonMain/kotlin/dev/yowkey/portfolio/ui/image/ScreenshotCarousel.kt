package dev.yowkey.portfolio.ui.image

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.yowkey.portfolio.model.Screenshot
import dev.yowkey.portfolio.ui.components.ChevronGlyph
import dev.yowkey.portfolio.ui.theme.LocalMotion
import dev.yowkey.portfolio.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private val PhoneBody = Color(0xFF15171C)
private val PhoneShape = RoundedCornerShape(22.dp)
private val PhoneScreenShape = RoundedCornerShape(16.dp)
private val BrowserShape = RoundedCornerShape(10.dp)

private const val GlowLayers = 8

private fun mix(from: Float, to: Float, fraction: Float) = from + (to - from) * fraction

/**
 * One project's screenshots as a coverflow carousel: the centre slide full size in a device frame, neighbours
 * peeking in smaller, dimmer and tilted. Swipe, the arrow buttons, or ←/→ when focused page through it; tapping
 * the centre slide calls [onOpen] with its on-screen bounds so the viewer can zoom out of it.
 *
 * Per-frame work is layer- and draw-phase only: every transform, the glow and the indicator read the pager's
 * offset inside `graphicsLayer {}` / `drawBehind {}` / `Canvas`, so paging recomposes nothing but the caption,
 * once per settled page. It autoplays every 4 s while at least half on screen, unless hovered, focused, dragged,
 * [canAutoplay] says no (the viewer is open), or the user prefers reduced motion.
 *
 * Full-size images load only for the settled page and its neighbours; every other slide shows its thumbnail.
 */
@Composable
fun ScreenshotCarousel(
    screenshots: List<Screenshot>,
    onOpen: (index: Int, origin: Rect?) -> Unit,
    modifier: Modifier = Modifier,
    canAutoplay: () -> Boolean = { true },
    pager: PagerState = rememberPagerState { screenshots.size },
) {
    if (screenshots.isEmpty()) return
    val aspects = remember(screenshots) { screenshots.map { it.aspectRatio } }
    val pagerHeight = remember(aspects) { { width: Float -> CarouselGeometry.of(width, aspects).pagerHeight } }
    if (screenshots.size == 1) {
        HeightForWidth(pagerHeight, modifier.fillMaxWidth()) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val geometry = remember(maxWidth, aspects) { CarouselGeometry.of(maxWidth.value, aspects) }
                Slide(screenshots[0], geometry, glow = { 1f }, fullSize = true) { origin -> onOpen(0, origin) }
            }
        }
        return
    }

    val motion = LocalMotion.current
    val scope = rememberCoroutineScope()
    val hoverSource = remember { MutableInteractionSource() }
    val focusSource = remember { MutableInteractionSource() }
    val hovered = hoverSource.collectIsHoveredAsState()
    val focused = focusSource.collectIsFocusedAsState()
    val dragged = pager.interactionSource.collectIsDraggedAsState()
    var visible by remember { mutableStateOf(false) }
    val autoplayAllowed by rememberUpdatedState(canAutoplay)
    val count = screenshots.size
    fun go(delta: Int) {
        scope.launch { pager.animateScrollToPage((pager.currentPage + delta).mod(count), animationSpec = motion.page()) }
    }

    if (motion.autoplays) {
        LaunchedEffect(pager, count) {
            // States are read here, not in composition: hovering or scrolling past never recomposes the carousel.
            snapshotFlow { visible && !hovered.value && !focused.value && !dragged.value && autoplayAllowed() }
                .collectLatest { play ->
                    while (play) {
                        delay(Motion.AutoplayMillis)
                        pager.animateScrollToPage((pager.currentPage + 1) % count, animationSpec = motion.page())
                    }
                }
        }
    }

    val focusRing = MaterialTheme.colorScheme.outline
    Column(
        modifier
            .fillMaxWidth()
            .onVisibilityChanged(minFractionVisible = 0.5f) { visible = it }
            .hoverable(hoverSource),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HeightForWidth(
            pagerHeight,
            Modifier
                .fillMaxWidth()
                // Before focusable, so the keys reach this node while the carousel holds focus.
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionRight -> go(1).let { true }
                        Key.DirectionLeft -> go(-1).let { true }
                        else -> false
                    }
                }
                .focusable(interactionSource = focusSource)
                .drawBehind {
                    if (focused.value) {
                        drawRoundRect(focusRing, cornerRadius = CornerRadius(12.dp.toPx()), style = Stroke(2.dp.toPx()))
                    }
                },
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val geometry = remember(maxWidth, aspects) { CarouselGeometry.of(maxWidth.value, aspects) }
                val sidePadding = ((maxWidth.value - geometry.pageWidth) / 2).coerceAtLeast(0f).dp
                HorizontalPager(
                    state = pager,
                    pageSize = PageSize.Fixed(geometry.pageWidth.dp),
                    contentPadding = PaddingValues(horizontal = sidePadding),
                    pageSpacing = CarouselGeometry.PageSpacing.dp,
                    beyondViewportPageCount = 1,
                    key = { screenshots[it].file },
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    // Full size while within one page of the settled page, the thumbnail otherwise. settledPage, not
                    // currentPage, so the decode runs after a swipe ends, not during it.
                    var fullSize by remember { mutableStateOf(false) }
                    LaunchedEffect(page) {
                        snapshotFlow { abs(page - pager.settledPage) <= 1 }.collect { fullSize = it }
                    }
                    val distance = { abs(pager.getOffsetDistanceInPages(page)).coerceAtMost(1f) }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val offset = pager.getOffsetDistanceInPages(page).coerceIn(-1f, 1f)
                                val d = abs(offset)
                                alpha = mix(1f, 0.5f, d)
                                if (!motion.reduced) {
                                    val scale = mix(1f, 0.85f, d)
                                    scaleX = scale
                                    scaleY = scale
                                    rotationY = -offset * 14f
                                    cameraDistance = 14f * density
                                    // Scaled neighbours leave a wider gap; pull them back toward the centre.
                                    translationX = -offset * size.width * 0.06f
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Slide(screenshots[page], geometry, glow = { 1f - distance() }, fullSize = fullSize) { origin ->
                            if (page == pager.currentPage) onOpen(page, origin) else scope.launch {
                                pager.animateScrollToPage(page, animationSpec = motion.page())
                            }
                        }
                    }
                }
            }
        }
        Controls(pager, count, onPrevious = { go(-1) }, onNext = { go(1) })
        Crossfade(targetState = pager.currentPage, animationSpec = motion.state(), label = "caption") { page ->
            Text(
                screenshots[page].caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** One framed screenshot with its glow; [glow] (0..1) is read in the draw phase. */
@Composable
private fun Slide(
    shot: Screenshot,
    geometry: CarouselGeometry,
    glow: () -> Float,
    fullSize: Boolean,
    onClick: (origin: Rect?) -> Unit,
) {
    val images = LocalScreenshotImages.current
    val (width, height) = geometry.frameSize(shot.aspectRatio)
    val accent = MaterialTheme.colorScheme.primary
    val holder = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val frame = Modifier
        .size(width.dp, height.dp)
        .drawBehind {
            // A soft glow from stacked, widening translucent outlines: blur-like, cheap (eight rects), and kept inside
            // the pager's glow padding so nothing is clipped. Not Modifier.blur, which would blur the screenshot.
            val strength = glow()
            if (strength > 0.01f) {
                val step = (CarouselGeometry.GlowPadding / GlowLayers).dp.toPx()
                val base = (if (shot.aspectRatio < 1f) 22 else 10).dp.toPx()
                for (i in 1..GlowLayers) {
                    val inflate = step * i
                    drawRoundRect(
                        color = accent,
                        topLeft = Offset(-inflate, -inflate + step),
                        size = Size(size.width + 2 * inflate, size.height + 2 * inflate - step),
                        cornerRadius = CornerRadius(base + inflate),
                        alpha = 0.028f * strength,
                    )
                }
            }
        }
        .onPlaced { holder[0] = it }
        .pointerHoverIcon(PointerIcon.Hand)
        .clickable(
            onClickLabel = "View larger",
            role = Role.Button,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) { onClick(holder[0]?.takeIf { it.isAttached }?.boundsInWindow()) }
    val image: @Composable (Modifier) -> Unit = { m ->
        ScreenshotImage(
            path = if (fullSize) shot.fullPath else shot.thumbPath,
            contentDescription = shot.caption,
            loader = if (fullSize) images?.fullSize else images?.thumbnails,
            previewPath = shot.thumbPath,
            previewLoader = images?.thumbnails,
            modifier = m,
        )
    }
    if (shot.aspectRatio < 1f) PhoneFrame(frame, image) else BrowserFrame(frame, image)
}

/** A thin device body with a bezel and a soft top-left highlight on its edge. */
@Composable
private fun PhoneFrame(modifier: Modifier, screen: @Composable (Modifier) -> Unit) {
    Box(
        modifier
            .background(PhoneBody, PhoneShape)
            .border(
                1.dp,
                Brush.linearGradient(listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.04f))),
                PhoneShape,
            )
            .padding(CarouselGeometry.PhoneBezel.dp),
    ) {
        screen(Modifier.fillMaxSize().clip(PhoneScreenShape))
    }
}

/** A minimal browser window: a bar with three dots over the screenshot. */
@Composable
private fun BrowserFrame(modifier: Modifier, page: @Composable (Modifier) -> Unit) {
    val bar = MaterialTheme.colorScheme.surfaceContainerHigh
    val dot = MaterialTheme.colorScheme.outline
    val edge = MaterialTheme.colorScheme.outlineVariant
    Column(modifier.clip(BrowserShape).border(1.dp, edge, BrowserShape).background(bar)) {
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(CarouselGeometry.BrowserBar.dp)
                .drawBehind {
                    val r = 3.dp.toPx()
                    repeat(3) { i -> drawCircle(dot, r, Offset(12.dp.toPx() + i * 10.dp.toPx(), size.height / 2)) }
                },
        )
        page(Modifier.fillMaxWidth().weight(1f))
    }
}

/** Previous / indicator / next. The indicator is one Canvas whose active pill stretches with the swipe. */
@Composable
private fun Controls(pager: PagerState, count: Int, onPrevious: () -> Unit, onNext: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    val idle = MaterialTheme.colorScheme.outline
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onPrevious,
            modifier = Modifier.size(48.dp).pointerHoverIcon(PointerIcon.Hand).semantics { contentDescription = "Previous screenshot" },
        ) { ChevronGlyph(pointsLeft = true, color = ink) }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            val dot = 6.dp
            val active = 20.dp
            val gap = 6.dp
            Canvas(
                Modifier
                    .size(width = dot * (count - 1) + active + gap * (count - 1), height = dot)
                    .semantics { contentDescription = "Screenshot ${pager.currentPage + 1} of $count" },
            ) {
                val position = pager.currentPage + pager.currentPageOffsetFraction
                var x = 0f
                repeat(count) { i ->
                    val t = (1f - abs(i - position)).coerceIn(0f, 1f)
                    val w = mix(dot.toPx(), active.toPx(), t)
                    drawRoundRect(
                        color = lerp(idle, ink, t),
                        topLeft = Offset(x, 0f),
                        size = Size(w, size.height),
                        cornerRadius = CornerRadius(size.height / 2),
                    )
                    x += w + gap.toPx()
                }
            }
        }
        IconButton(
            onClick = onNext,
            modifier = Modifier.size(48.dp).pointerHoverIcon(PointerIcon.Hand).semantics { contentDescription = "Next screenshot" },
        ) { ChevronGlyph(pointsLeft = false, color = ink) }
    }
}

/**
 * Lays out [content] at the full available width and [heightFor] (dp from dp) tall, and answers intrinsic-size
 * queries from the same function. CardRow asks for intrinsics to make cards equal height, and a pager (a lazy
 * layout) cannot answer them itself.
 */
@Composable
private fun HeightForWidth(heightFor: (widthDp: Float) -> Float, modifier: Modifier, content: @Composable () -> Unit) {
    val policy = remember(heightFor) {
        object : MeasurePolicy {
            fun Density.heightPx(widthPx: Int): Int = heightFor(widthPx / density).dp.roundToPx()

            override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
                val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
                val height = heightPx(width).coerceIn(constraints.minHeight, constraints.maxHeight)
                val placeables = measurables.map { it.measure(Constraints.fixed(width, height)) }
                return layout(width, height) { placeables.forEach { it.place(0, 0) } }
            }

            override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) = heightPx(width)

            override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) = heightPx(width)

            override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) = 0

            override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) = 0
        }
    }
    Layout(content, modifier, policy)
}
