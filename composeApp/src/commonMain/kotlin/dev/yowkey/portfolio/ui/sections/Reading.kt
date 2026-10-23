package dev.yowkey.portfolio.ui.sections

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/*
 * How long text is set, on the page and the project page alike.
 *
 * The measure: a line far past 75 characters is hard to follow back to the start of the next, so long text keeps
 * to [MEASURE_EMS] of its own size and never runs past [MaxMeasure]: bodyLarge stops at 680dp (about 78 characters
 * of this typeface), bodyMedium at 600dp (about 85, its sentences mostly one or two lines, and a tighter measure
 * only left their last word alone on a line). Counted in ems, not dp, so the line holds the same number of words at
 * any text size, a reader's larger system text included. Cards keep their grid widths: only text that could run
 * the width of a wide column is held to it.
 *
 * Muted text is onSurfaceVariant at full strength, never faded with alpha: 7.3:1 on the page and 7.6:1 on a card
 * in light, 7.5:1 and 6.9:1 in dark, 5.3:1 at least on the accent washes. All clear WCAG AA's 4.5:1.
 */

/** The widest any long text runs, whatever its size. */
val MaxMeasure: Dp = 680.dp

/** Long text runs at most this many times its font size. */
const val MEASURE_EMS = 40f

/**
 * Space between paragraphs, and between the items of a list of sentences: about 0.75em of bodyMedium, so a new
 * item reads as new without the list falling apart.
 */
val ParagraphGap: Dp = 12.dp

/**
 * The measure for text set at [fontSize], at [density]'s text scale: [MEASURE_EMS] ems, at most [MaxMeasure]. A
 * size in ems or none at all cannot be resolved here, so it gets [MaxMeasure].
 */
fun readingMeasure(fontSize: TextUnit, density: Density): Dp {
    if (!fontSize.isSp) return MaxMeasure
    val measure = with(density) { (fontSize * MEASURE_EMS).toDp() }
    return if (measure < MaxMeasure) measure else MaxMeasure
}

/**
 * Holds this element to the measure for [style]'s size ([readingMeasure]); narrower space is used as given. A
 * layout modifier rather than a composable one, so it resolves the text scale where it measures and answers the
 * intrinsic queries an equal-height card row makes.
 */
fun Modifier.readingMeasure(style: TextStyle): Modifier = layout { measurable, constraints ->
    val cap = readingMeasure(style.fontSize, this).roundToPx()
    val maxWidth = minOf(constraints.maxWidth, cap).coerceAtLeast(constraints.minWidth)
    val placeable = measurable.measure(constraints.copy(maxWidth = maxWidth))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}
