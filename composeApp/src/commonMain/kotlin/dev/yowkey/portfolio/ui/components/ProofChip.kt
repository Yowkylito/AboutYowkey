package dev.yowkey.portfolio.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.yowkey.portfolio.model.LiveStats
import dev.yowkey.portfolio.model.ProofCopy
import dev.yowkey.portfolio.ui.theme.LocalReducedMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The bar's proof, while the hero's name is on screen: the notes' rating (five small stars lit to the fraction, the
 * average in bold, how many rated) and, past a hairline, the visits (an eye, the total in bold), on a pill of the
 * accent's faintest tint, as the notes' dashboard is lit. It is the way to that dashboard ([onClick]), and one button
 * to a screen reader, named in full by [ProofCopy.label].
 *
 * A narrow bar drops the muted words first, then four of the stars ([ProofFit]). Nothing is drawn until there is
 * something to say. Then the chip fades in and its numbers count up from 0, once ([reveal]); with only one of the two
 * answers in, it waits a moment for the other, so the chip does not arrive in two pieces. Hover and focus are only
 * drawn, and the counting only redraws the numbers and the stars, so none of it recomposes.
 */
@Composable
fun ProofChip(stats: LiveStats, reveal: ProofReveal, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = remember(stats) { ProofCopy.label(stats) } ?: return
    val scheme = MaterialTheme.colorScheme
    val reducedMotion = LocalReducedMotion.current
    LaunchedEffect(reveal, reducedMotion, stats.settled) {
        if (!stats.settled && !reveal.started) delay(ProofReveal.SETTLE_WAIT_MS)
        reveal.play(reducedMotion)
    }
    val interaction = remember { MutableInteractionSource() }
    val hovered = interaction.collectIsHoveredAsState()
    val focused = interaction.collectIsFocusedAsState()
    // The dashboard card's language: the accent at a few percent over the page, and the cards' hairline. Hovered, the
    // hairline takes the accent and the ripple's state layer (the accent too) deepens the tint.
    val tint = lerp(scheme.background, scheme.primary, 0.07f)
    val line = scheme.outlineVariant
    val lineHovered = scheme.primary.copy(alpha = 0.45f)
    val ring = scheme.primary
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .graphicsLayer { alpha = reveal.shown.value }
            .drawBehind {
                if (focused.value) {
                    val gap = 3.dp.toPx()
                    drawRoundRect(
                        ring,
                        topLeft = Offset(-gap, -gap),
                        size = Size(size.width + gap * 2, size.height + gap * 2),
                        cornerRadius = CornerRadius(size.height / 2 + gap),
                        style = Stroke(2.dp.toPx()),
                    )
                }
            }
            .height(ChipHeight)
            .clip(CircleShape)
            .drawBehind {
                val hairline = 1.dp.toPx()
                drawRoundRect(tint, cornerRadius = CornerRadius(size.height / 2))
                drawRoundRect(
                    if (hovered.value) lineHovered else line,
                    topLeft = Offset(hairline / 2, hairline / 2),
                    size = Size(size.width - hairline, size.height - hairline),
                    cornerRadius = CornerRadius((size.height - hairline) / 2),
                    style = Stroke(hairline),
                )
            }
            .clickable(interactionSource = interaction, indication = ripple(color = ring), role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        ProofParts(stats, count = { reveal.count.value }, Modifier.clearAndSetSemantics { })
    }
}

/**
 * The chip's entrance: its fade and its count, 0 to 1. Keep one per page visit, above everything that comes and goes
 * (the bar's crossfade, the list itself while a project is open), so scrolling back to the hero or coming back from a
 * project never plays it again, and a later answer (a new note, another visit) only changes the numbers.
 */
@Stable
class ProofReveal {
    internal val shown = Animatable(0f)
    internal val count = Animatable(0f)
    internal var started = false
        private set

    /**
     * Fades in over [FADE_MS] while the count eases up over [COUNT_MS]; both at once under reduced motion. Cut short
     * (the chip left mid-count), it carries on from where it stopped the next time the chip shows.
     */
    internal suspend fun play(reducedMotion: Boolean) {
        started = true
        if (reducedMotion) {
            shown.snapTo(1f)
            count.snapTo(1f)
            return
        }
        coroutineScope {
            launch { if (shown.value < 1f) shown.animateTo(1f, tween(FADE_MS)) }
            if (count.value < 1f) {
                count.animateTo(1f, tween(((1f - count.value) * COUNT_MS).roundToInt(), easing = EaseOutCubic))
            }
        }
    }

    companion object {
        const val FADE_MS = 260
        const val COUNT_MS = 900

        /** With one answer in, how long the chip waits for the other before it shows what it has. */
        const val SETTLE_WAIT_MS = 600L
    }
}

/** One [ProofReveal] for this composition's life. */
@Composable
fun rememberProofReveal(): ProofReveal = remember { ProofReveal() }

/** The chip's pieces, in the order they sit. Each one measured carries the space before it. */
enum class ProofPart { Stars, Star, Average, RatingCount, Divider, Eye, Visits, VisitsWord }

/** Which of the chip's pieces a bar of a given width shows; pure, so the fallbacks are unit-tested. */
object ProofFit {

    /**
     * Widest first: everything; then without the muted words (the stars, "5.0", the hairline, the eye, "35"); then
     * one star for five. The hairline only between a rating and the visits; no forms at all with neither.
     */
    fun forms(hasRating: Boolean, hasVisits: Boolean): List<List<ProofPart>> {
        val ratings = if (!hasRating) listOf(emptyList(), emptyList(), emptyList()) else listOf(
            listOf(ProofPart.Stars, ProofPart.Average, ProofPart.RatingCount),
            listOf(ProofPart.Stars, ProofPart.Average),
            listOf(ProofPart.Star, ProofPart.Average),
        )
        val visits = if (!hasVisits) listOf(emptyList(), emptyList(), emptyList()) else listOf(
            listOf(ProofPart.Eye, ProofPart.Visits, ProofPart.VisitsWord),
            listOf(ProofPart.Eye, ProofPart.Visits),
            listOf(ProofPart.Eye, ProofPart.Visits),
        )
        return ratings.zip(visits) { rating, visit ->
            if (rating.isNotEmpty() && visit.isNotEmpty()) rating + ProofPart.Divider + visit else rating + visit
        }.filter { it.isNotEmpty() }.distinct()
    }

    /** The first of [forms] whose pieces' [width]s fit in [budget]; when none does, the last, to be clipped. */
    fun fit(forms: List<List<ProofPart>>, budget: Int, width: (ProofPart) -> Int): List<ProofPart> =
        forms.firstOrNull { form -> form.sumOf(width) <= budget } ?: forms.lastOrNull().orEmpty()
}

/**
 * The pieces in one line, the widest [ProofFit] form that fits: every piece is measured once, and only that form's
 * are placed, so a narrower bar changes the layout, never the composition. The words sit on one baseline, the glyphs
 * on their middle.
 */
@Composable
private fun ProofParts(stats: LiveStats, count: () -> Float, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val strong = MaterialTheme.typography.labelLarge.copy(
        fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 0.sp, fontFeatureSettings = "tnum",
    )
    val muted = MaterialTheme.typography.labelLarge.copy(
        fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.sp, fontFeatureSettings = "tnum",
    )
    // A touch firmer than the pill's own hairline, which it would otherwise vanish against.
    val divider = lerp(scheme.outlineVariant, scheme.outline, 0.35f)
    val forms = remember(stats.hasRating, stats.hasVisits) { ProofFit.forms(stats.hasRating, stats.hasVisits) }
    Layout(
        modifier = modifier,
        content = {
            val average = stats.average
            if (average != null) {
                StarMeter(
                    average, Modifier.layoutId(ProofPart.Stars), glyphSize = StarSize, spacing = 1.5.dp, progress = count,
                )
                StarGlyph(
                    filled = true, color = scheme.primary, modifier = Modifier.layoutId(ProofPart.Star), glyphSize = StarSize,
                )
                CountedText(
                    final = ProofCopy.average(average),
                    shown = { ProofCopy.average(average, it) },
                    progress = count,
                    style = strong,
                    color = scheme.onBackground,
                    modifier = Modifier.layoutId(ProofPart.Average).padding(start = 6.dp),
                )
                Words(
                    ProofCopy.ratingCount(stats.ratingCount), muted, scheme.onSurfaceVariant,
                    Modifier.layoutId(ProofPart.RatingCount).padding(start = 5.dp),
                )
            }
            if (stats.hasVisits) {
                val total = stats.visitTotal
                EyeGlyph(scheme.primary, Modifier.layoutId(ProofPart.Eye), glyphSize = EyeSize)
                CountedText(
                    final = ProofCopy.visits(total),
                    shown = { ProofCopy.visits(total, it) },
                    progress = count,
                    style = strong,
                    color = scheme.onBackground,
                    modifier = Modifier.layoutId(ProofPart.Visits).padding(start = 6.dp),
                )
                Words(
                    ProofCopy.visitsWord(total), muted, scheme.onSurfaceVariant,
                    Modifier.layoutId(ProofPart.VisitsWord).padding(start = 5.dp),
                )
            }
            Spacer(
                Modifier
                    .layoutId(ProofPart.Divider)
                    .padding(horizontal = 10.dp)
                    .size(width = 1.dp, height = 14.dp)
                    .drawBehind { drawRect(divider) },
            )
        },
    ) { measurables, constraints ->
        val loose = Constraints()
        val measured = measurables.associate { it.layoutId as ProofPart to it.measure(loose) }
        val parts = ProofFit.fit(forms, constraints.maxWidth) { part -> measured[part]?.width ?: 0 }
        val shown = parts.mapNotNull { part -> measured[part] }
        val width = shown.sumOf { it.width }.coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (shown.maxOfOrNull { it.height } ?: 0).coerceIn(constraints.minHeight, constraints.maxHeight)
        // The tallest line of words is centred; the others share its baseline.
        val words = shown.filter { it[FirstBaseline] != AlignmentLine.Unspecified }
        val baseline = words.maxByOrNull { it.height }?.let { tallest -> (height - tallest.height) / 2 + tallest[FirstBaseline] }
        layout(width, height) {
            var x = 0
            shown.forEach { piece ->
                val first = piece[FirstBaseline]
                val y = if (baseline != null && first != AlignmentLine.Unspecified) baseline - first
                else (height - piece.height) / 2
                piece.place(x, y)
                x += piece.width
            }
        }
    }
}

/** A muted word or two, on one line. */
@Composable
private fun Words(text: String, style: TextStyle, color: Color, modifier: Modifier) {
    Text(text, modifier, style = style, color = color, maxLines = 1, softWrap = false)
}

/**
 * A number as the count has it: laid out as [final] (tabular figures, so no step of the count is wider) and, until
 * the count is done, drawn as [shown] writes it at the current [progress], flush right, so the digits grow leftward
 * as a counter's do and the word after it never moves. Only the drawing follows the count.
 */
@Composable
private fun CountedText(
    final: String,
    shown: (Float) -> String,
    progress: () -> Float,
    style: TextStyle,
    color: Color,
    modifier: Modifier,
) {
    val measurer = rememberTextMeasurer()
    Text(
        final,
        modifier.drawWithContent {
            val at = progress()
            if (at >= 1f) {
                drawContent()
            } else {
                val step = measurer.measure(shown(at), style, maxLines = 1, softWrap = false)
                drawText(step, color = color, topLeft = Offset(size.width - step.size.width, 0f))
            }
        },
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
    )
}

private val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)

private val ChipHeight = 32.dp
private val StarSize = 13.dp
private val EyeSize = 15.dp
