package dev.yowkey.portfolio.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.yowkey.portfolio.ui.theme.LocalReducedMotion
import kotlinx.coroutines.delay

/*
 * The pieces of the page bar's brand: the monogram that takes the name's place at its start, the availability pill
 * shown beside it while the hero's own name is on screen, and the name with its role once that has scrolled away.
 */

/**
 * The monogram: [name]'s initials on a small rounded square of the accent, deepening toward the bottom right so it
 * reads as a mark rather than a flat button. It is the bar's Home button. To the accessibility tree it is one button
 * named "<name>, back to top"; the letters themselves are not read, since the name says them better. Focused from the
 * keyboard it wears an accent ring just outside its corners (drawn, so focus never recomposes it).
 */
@Composable
fun Monogram(name: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val initials = remember(name) { initialsOf(name) }
    val label = "$name, back to top"
    // A touch darker than the accent: in light that raises white's contrast, in dark it keeps the navy ink above 4.5:1.
    val deeper = lerp(scheme.primary, Color.Black, 0.18f)
    val top = scheme.primary
    val interaction = remember { MutableInteractionSource() }
    val focused = interaction.collectIsFocusedAsState()
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .size(MarkSize)
            .drawBehind {
                if (focused.value) {
                    val gap = 3.dp.toPx()
                    val ring = 2.dp.toPx()
                    drawRoundRect(
                        top,
                        topLeft = Offset(-gap, -gap),
                        size = Size(size.width + gap * 2, size.height + gap * 2),
                        cornerRadius = CornerRadius(MarkCorner.toPx() + gap),
                        style = Stroke(ring),
                    )
                }
            }
            .clip(RoundedCornerShape(MarkCorner))
            .drawWithCache {
                val fill = Brush.linearGradient(listOf(top, deeper), end = Offset(size.width, size.height))
                // A faint sheen over the upper half, as on a raised tile catching the light.
                val sheen = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
                    endY = size.height * 0.6f,
                )
                onDrawBehind {
                    drawRect(fill)
                    drawRect(sheen)
                }
            }
            .clickable(interactionSource = interaction, indication = ripple(), role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials,
            color = scheme.onPrimary,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = (-0.4).sp,
            ),
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/**
 * Whether the visitor can be hired, as a pill: a small green dot that pulses now and then, like a status light, and
 * [availability] in words. On a narrow bar the words shorten at a word boundary ("Open to relocation") rather than
 * ellipsize mid-word (see [shorterForms]); a screen reader always hears the whole sentence. Under reduced motion the
 * dot holds still.
 */
@Composable
fun AvailabilityPill(availability: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val green = if (dark) AvailableDark else AvailableLight
    val style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.1.sp)
    Row(
        modifier
            .clearAndSetSemantics { contentDescription = availability }
            .background(lerp(scheme.background, green, if (dark) 0.10f else 0.08f), CircleShape)
            .border(1.dp, green.copy(alpha = if (dark) 0.28f else 0.24f), CircleShape)
            .padding(start = 10.dp, end = 11.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LiveDot(green)
        FittedText(availability, style, scheme.onBackground.copy(alpha = 0.86f))
    }
}

/**
 * The status light: a solid dot, and every few seconds a ring that grows from it and fades. The ring is read only in
 * the draw phase and rests between pulses, so it neither recomposes nor keeps the page drawing frames while idle.
 */
@Composable
private fun LiveDot(color: Color) {
    val reducedMotion = LocalReducedMotion.current
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(reducedMotion) {
        pulse.snapTo(0f)
        if (reducedMotion) return@LaunchedEffect
        delay(600)
        while (true) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(1400, easing = EaseOutCubic))
            delay(1800)
        }
    }
    Spacer(
        Modifier.size(DotBox).drawBehind {
            val r = 3.5.dp.toPx()
            val p = pulse.value
            if (p > 0f && p < 1f) drawCircle(color.copy(alpha = 0.5f * (1f - p)), radius = r * (1f + 1.5f * p))
            drawCircle(color, r)
        },
    )
}

/**
 * Who, once the hero's name has scrolled under the bar: [name] over [role] (the headline's lead, see [roleOf]), each
 * on one line. One node to a screen reader, so it reads as "name, role".
 */
@Composable
fun BrandName(name: String, role: String, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) { }, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            name,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (role.isNotEmpty()) {
            FittedText(
                role,
                MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp),
                MaterialTheme.colorScheme.onSurfaceVariant,
                // A shortened role still reads in full.
                Modifier.clearAndSetSemantics { contentDescription = role },
            )
        }
    }
}

/**
 * One line of [text] in the width it is given: the longest of its [shorterForms] that fits, so a narrow bar cuts it
 * at a whole word ("Open to relocation") instead of mid-word; only when even the shortest is too wide does it
 * ellipsize. Measured once per text and width, not per frame.
 */
@Composable
private fun FittedText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val forms = remember(text) { shorterForms(text) }
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val budget = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        val shown = remember(forms, budget, style, measurer) {
            forms.firstOrNull { form -> measurer.measure(form, style, maxLines = 1, softWrap = false).size.width <= budget }
                ?: forms.last()
        }
        Text(shown, style = style, color = color, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

/** The mark's letters: the first letter of the first and the last word of [name], upper-cased ("RT"); one word, one. */
fun initialsOf(name: String): String {
    val words = name.split(' ', '\t', '\n', '-').filter { word -> word.any { it.isLetterOrDigit() } }
    val picked = if (words.size <= 1) words else listOf(words.first(), words.last())
    return picked.map { word -> word.first { it.isLetterOrDigit() }.uppercaseChar() }.joinToString("")
}

/**
 * The role a headline leads with: its text before the first " · ", where the skills begin ("Android Developer at
 * DigiPlus since 2024"). A headline without the separator is all role.
 */
fun roleOf(headline: String): String = headline.substringBefore('·').trim()

/**
 * [text], then each shorter whole-word lead of it, longest first, for a label that must fit a narrow space: never
 * cut mid-word, never ending on a small joining word or a stray separator, and never under two words. "Open to
 * relocation to any Country" gives itself and "Open to relocation".
 */
fun shorterForms(text: String): List<String> {
    val full = text.trim()
    val words = full.split(' ').filter { it.isNotEmpty() }
    val shorter = (words.size - 1 downTo 2).mapNotNull { count ->
        val lead = words.take(count)
        val last = lead.last().trimEnd(',', ';', ':', '·', '-', '–', '—')
        if (last.isEmpty() || last.lowercase() in JoiningWords) null
        else (lead.dropLast(1) + last).joinToString(" ")
    }
    return (listOf(full) + shorter).distinct()
}

private val JoiningWords = setOf(
    "a", "an", "and", "any", "as", "at", "by", "for", "from", "in", "into", "of", "on", "or", "since", "the", "to",
    "until", "with",
)

private val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)

private val MarkSize = 32.dp
private val MarkCorner = 10.dp
private val DotBox = 10.dp

// Green for "available", which the blue accent cannot say: 600 on the light page, 400 on the dark one.
private val AvailableLight = Color(0xFF16A34A)
private val AvailableDark = Color(0xFF4ADE80)
