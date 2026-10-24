package dev.yowkey.portfolio.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.yowkey.portfolio.model.LiveStats
import dev.yowkey.portfolio.model.Profile
import dev.yowkey.portfolio.ui.components.BrandName
import dev.yowkey.portfolio.ui.components.MenuGlyph
import dev.yowkey.portfolio.ui.components.Monogram
import dev.yowkey.portfolio.ui.components.ProofChip
import dev.yowkey.portfolio.ui.components.ProofReveal
import dev.yowkey.portfolio.ui.components.ThemeToggle
import dev.yowkey.portfolio.ui.components.rememberProofReveal
import dev.yowkey.portfolio.ui.components.roleOf
import dev.yowkey.portfolio.ui.theme.LocalReducedMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * The sticky navigation bar. The hero already says the name in huge type, so the bar starts with a monogram (the
 * Home button) and, while the hero's name is on screen, the proof: the notes' rating and the visits ([stats], see
 * [ProofChip]), which [onOpenNotes] takes the visitor to; once [heroPassed], the proof gives way to the name and
 * role. Wide windows list the sections with an accent line that slides to the [active] one; compact ones put them in
 * a menu. The theme switch sits at the end. [proofReveal] holds the proof's entrance, so a caller that outlives the
 * bar keeps it from playing twice.
 *
 * At the top the bar is flat. Once [scrolled], a hairline fades in along its bottom edge, and over it a 2 dp accent
 * line as long as [progress] says (0..1, how far down the page the reader is). Both are read from lambdas in the draw
 * phase, so scrolling never recomposes the bar: only [active] changes do, and the brand slot alone when [heroPassed]
 * flips or [stats] change.
 */
@Composable
fun TopBar(
    profile: Profile,
    sections: List<Section>,
    active: Section,
    compact: Boolean,
    gutter: Dp,
    maxWidth: Dp,
    scrolled: () -> Boolean,
    heroPassed: () -> Boolean,
    progress: () -> Float,
    stats: LiveStats,
    onOpenNotes: () -> Unit,
    onNavigate: (Section) -> Unit,
    modifier: Modifier = Modifier,
    proofReveal: ProofReveal = rememberProofReveal(),
) {
    val background = MaterialTheme.colorScheme.background
    val divider = MaterialTheme.colorScheme.outlineVariant
    val accent = MaterialTheme.colorScheme.primary
    val edge = rememberEdgeAlpha(scrolled)
    Box(
        modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(background)
                val shown = edge.value
                if (shown > 0f) {
                    val hairline = 1.dp.toPx()
                    drawRect(divider.copy(alpha = shown), Offset(0f, size.height - hairline), Size(size.width, hairline))
                    val read = progress().coerceIn(0f, 1f)
                    if (read > 0f) {
                        val line = ProgressLine.toPx()
                        drawRect(accent.copy(alpha = shown), Offset(0f, size.height - line), Size(size.width * read, line))
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            // Both ends line up with the page's gutter: the mark's 32 dp sits 8 dp inside its 48 dp touch target, the
            // menu's glyph 14 dp inside its button, and the theme switch is as wide as it looks.
            Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .height(64.dp)
                .padding(start = gutter - 8.dp, end = if (compact) gutter - 14.dp else gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Monogram(profile.name, onClick = { onNavigate(Section.Home) })
            Spacer(Modifier.width(6.dp))
            // Its end padding keeps the proof or a long role from running into what follows.
            BrandSlot(profile, heroPassed, stats, proofReveal, onOpenNotes, Modifier.weight(1f).padding(end = 8.dp))
            if (compact) {
                ThemeToggle()
                CompactMenu(sections, active, onNavigate)
            } else {
                NavLinks(sections, active, onNavigate)
                Spacer(Modifier.width(6.dp))
                ThemeToggle()
            }
        }
    }
}

/**
 * The hairline's opacity: 0 at the very top, 1 once scrolled, faded over 180 ms between (instant under reduced
 * motion, and on the first reading, so a page that opens scrolled shows its line at once). Followed in a coroutine,
 * never in composition.
 */
@Composable
private fun rememberEdgeAlpha(scrolled: () -> Boolean): Animatable<Float, *> {
    val reducedMotion = LocalReducedMotion.current
    val isScrolled by rememberUpdatedState(scrolled)
    val edge = remember { Animatable(0f) }
    LaunchedEffect(reducedMotion) {
        var first = true
        snapshotFlow { isScrolled() }.collect { on ->
            val target = if (on) 1f else 0f
            if (first || reducedMotion) edge.snapTo(target) else edge.animateTo(target, tween(180))
            first = false
        }
    }
    return edge
}

/**
 * The proof or the name, crossfading as [heroPassed] flips. It is the only part of the bar that reads [heroPassed],
 * so only it recomposes then (and when [stats] change). Going down the page the name rises into place as the hero's
 * own slides under the bar; going back up it drops away. Under reduced motion the swap is instant. Neither state is a
 * live region: the swap follows scrolling, and announcing it would only talk over what the reader is reading.
 */
@Composable
private fun BrandSlot(
    profile: Profile,
    heroPassed: () -> Boolean,
    stats: LiveStats,
    proofReveal: ProofReveal,
    onOpenNotes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = LocalReducedMotion.current
    val role = remember(profile.headline) { roleOf(profile.headline) }
    AnimatedContent(
        targetState = heroPassed(),
        modifier = modifier.height(48.dp),
        contentAlignment = Alignment.CenterStart,
        transitionSpec = {
            if (reducedMotion) {
                (EnterTransition.None togetherWith ExitTransition.None).using(SizeTransform(clip = false) { _, _ -> snap() })
            } else {
                val rise = if (targetState) 1 else -1
                (fadeIn(tween(220, delayMillis = 40)) + slideInVertically(tween(260, easing = EaseOutCubic)) { rise * it / 3 })
                    .togetherWith(fadeOut(tween(140)) + slideOutVertically(tween(200)) { -rise * it / 3 })
                    .using(SizeTransform(clip = false))
            }
        },
        label = "brand",
    ) { passed ->
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            if (passed) {
                BrandName(profile.name, role)
            } else {
                ProofChip(stats, proofReveal, onClick = onOpenNotes)
            }
        }
    }
}

/**
 * The wide bar's section links, with a 2 dp accent line under the active one. The line slides between links: its
 * leading edge a little ahead of its trailing one, so it stretches as it travels and settles at the label's width.
 * It fades in on the first section and out again at the top (Home has no link); under reduced motion it moves at
 * once. Every link keeps the same weight, active or not, so a change never shifts the row.
 */
@Composable
private fun NavLinks(sections: List<Section>, active: Section, onNavigate: (Section) -> Unit) {
    val reducedMotion = LocalReducedMotion.current
    val accent = MaterialTheme.colorScheme.primary
    // Each label's left and right in the row, in px: written as the links are placed, and only when they move.
    val spans = remember { mutableStateMapOf<Section, LabelSpan>() }
    val inset = with(LocalDensity.current) { LinkPadding.toPx() }
    val left = remember { Animatable(0f) }
    val right = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    val target = spans[active]
    LaunchedEffect(target, reducedMotion) {
        when {
            target == null -> if (reducedMotion) alpha.snapTo(0f) else alpha.animateTo(0f, tween(160))
            reducedMotion || alpha.value == 0f -> {
                left.snapTo(target.left)
                right.snapTo(target.right)
                if (reducedMotion) alpha.snapTo(1f) else alpha.animateTo(1f, tween(200))
            }
            else -> coroutineScope {
                val forward = target.left > left.value
                launch { left.animateTo(target.left, if (forward) Trailing else Leading) }
                launch { right.animateTo(target.right, if (forward) Leading else Trailing) }
                launch { alpha.animateTo(1f, tween(160)) }
            }
        }
    }
    Row(
        Modifier.drawWithContent {
            drawContent()
            val shown = alpha.value
            if (shown > 0f) {
                val thickness = IndicatorHeight.toPx()
                drawRoundRect(
                    accent.copy(alpha = shown),
                    topLeft = Offset(left.value, size.height / 2 + IndicatorDrop.toPx()),
                    size = Size((right.value - left.value).coerceAtLeast(0f), thickness),
                    cornerRadius = CornerRadius(thickness / 2),
                )
            }
        },
    ) {
        sections.forEach { section ->
            NavLink(
                section,
                isActive = section == active,
                onNavigate = onNavigate,
                modifier = Modifier.onPlaced { link ->
                    val x = link.positionInParent().x
                    val span = LabelSpan(x + inset, x + link.size.width - inset)
                    if (spans[section] != span) spans[section] = span
                },
            )
        }
    }
}

/** Where a link's label starts and ends in its row, in px. */
private data class LabelSpan(val left: Float, val right: Float)

@Composable
private fun NavLink(section: Section, isActive: Boolean, onNavigate: (Section) -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = { onNavigate(section) },
        modifier = modifier.semantics { selected = isActive },
        contentPadding = PaddingValues(horizontal = LinkPadding, vertical = 8.dp),
    ) {
        Text(
            section.title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = if (isActive) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CompactMenu(sections: List<Section>, active: Section, onNavigate: (Section) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = "Open navigation menu" },
        ) { MenuGlyph() }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            sections.forEach { section ->
                DropdownMenuItem(
                    text = {
                        Text(
                            section.title,
                            color = if (section == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        expanded = false
                        onNavigate(section)
                    },
                )
            }
        }
    }
}

/**
 * Whether the hero's name has gone under the bar, for [TopBar]'s `heroPassed`: a derived state over [listState], so
 * its readers hear of it only when it flips, never on each scroll frame.
 */
@Composable
fun rememberHeroPassed(listState: LazyListState): () -> Boolean {
    val passed = remember(listState) {
        derivedStateOf {
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
            BarScroll.heroPassed(
                firstIndex = listState.firstVisibleItemIndex,
                firstOffset = listState.firstVisibleItemScrollOffset,
                heroSize = if (first?.index == 0) first.size else 0,
            )
        }
    }
    return remember(passed) { { passed.value } }
}

/** [BarScroll.progress] for this layout: how far down the page the reader is, 0..1. Read it in the draw phase. */
fun LazyListLayoutInfo.readingProgress(): Float {
    val first = visibleItemsInfo.firstOrNull() ?: return 0f
    val last = visibleItemsInfo.last()
    return BarScroll.progress(
        first = BarScroll.Item(first.index, first.offset, first.size),
        last = BarScroll.Item(last.index, last.offset, last.size),
        viewportStart = viewportStartOffset,
        viewportEnd = viewportEndOffset,
        itemCount = totalItemsCount,
    )
}

/** The bar's two readings of the page's scroll, pure so they are unit-tested; the page feeds them its list's layout. */
object BarScroll {

    /**
     * How much of the hero row must have gone past the top before the name moves into the bar. The row carries the
     * hero's top spacing, portrait, name, headline, proof, buttons and summary; the name ends a fifth of the way down
     * it on a phone and two fifths down on a wide window (measured on this content). Half is past both, so the bar
     * never shows the name while any of the hero's is still in view, yet takes it up soon after.
     */
    const val HERO_PASSED_FRACTION = 0.5f

    /** A row of the list: its index, its px offset from the viewport's start (negative once partly above), its size. */
    data class Item(val index: Int, val offset: Int, val size: Int)

    /**
     * True once the hero (the first row, [heroSize] px tall, 0 when it is not laid out) has scrolled [fraction] of
     * its height past the top, or is gone altogether.
     */
    fun heroPassed(firstIndex: Int, firstOffset: Int, heroSize: Int, fraction: Float = HERO_PASSED_FRACTION): Boolean =
        firstIndex > 0 || (heroSize > 0 && firstOffset >= heroSize * fraction)

    /**
     * How far down the page the reader is, 0 at the top and 1 at the bottom, counted in rows: the rows above the
     * viewport (the first visible one in part) over those plus the rows below it (the last visible one in part).
     * Rows differ in height, so this is an estimate, but it moves smoothly and only one way as the page scrolls:
     * each row's share passes from "below" to "above" as it crosses the viewport.
     */
    fun progress(first: Item, last: Item, viewportStart: Int, viewportEnd: Int, itemCount: Int): Float {
        if (itemCount <= 0) return 0f
        val above = first.index + hiddenShare(viewportStart - first.offset, first.size)
        val below = (itemCount - 1 - last.index) + hiddenShare(last.offset + last.size - viewportEnd, last.size)
        val total = above + below
        return if (total <= 0f) 0f else (above / total).coerceIn(0f, 1f)
    }

    /** The share of a row [size] px tall that [hidden] px of it are, clamped to 0..1. */
    private fun hiddenShare(hidden: Int, size: Int): Float =
        if (size <= 0) 0f else (hidden.coerceIn(0, size).toFloat() / size)
}

private val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)

// The indicator's edges: the leading one springs quicker than the trailing one, which is what makes it stretch.
private val Leading = spring<Float>(dampingRatio = 0.9f, stiffness = 900f)
private val Trailing = spring<Float>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)

private val ProgressLine = 2.dp
private val IndicatorHeight = 2.dp
// From the link's middle down to the line: half the label's 20 dp line, then a 4 dp gap.
private val IndicatorDrop = 14.dp
// The links' horizontal padding, so the line spans the label, not the button. A little under TextButton's 12 dp, and
// no gap between links, so that at the narrowest wide window (840 dp) the proof still has room for its short form.
private val LinkPadding = 10.dp
