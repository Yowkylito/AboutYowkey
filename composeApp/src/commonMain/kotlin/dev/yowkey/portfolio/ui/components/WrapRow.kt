package dev.yowkey.portfolio.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Lays children left to right, wrapping onto new lines, each line's items centred vertically.
 *
 * Like `FlowRow`, but with intrinsic sizes that match what it draws. The card grid levels a row of cards with
 * `IntrinsicSize.Min`, which asks each card how tall it is at a width before laying it out; `FlowRow` (Compose
 * 1.12) answered a line of chips short there, and the shortfall came out of the card's "View project" button. This
 * answers by running the same line breaking ([wrapLines]) on the children's own intrinsic widths.
 */
@Composable
fun WrapRow(
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 8.dp,
    verticalSpacing: Dp = 8.dp,
    content: @Composable () -> Unit,
) {
    val policy = remember(horizontalSpacing, verticalSpacing) { WrapRowPolicy(horizontalSpacing, verticalSpacing) }
    Layout(content, modifier, policy)
}

/**
 * Splits items of [widths] into lines no wider than [maxWidth], with [spacing] between neighbours. Every line holds
 * at least one item, so an item wider than [maxWidth] gets a line of its own. Returns each line's index range.
 */
fun wrapLines(widths: List<Int>, maxWidth: Int, spacing: Int): List<IntRange> {
    val lines = mutableListOf<IntRange>()
    var start = 0
    var used = 0
    widths.forEachIndexed { index, width ->
        if (index > start && used + spacing + width > maxWidth) {
            lines += start until index
            start = index
            used = width
        } else {
            used += if (index > start) spacing + width else width
        }
    }
    if (widths.isNotEmpty()) lines += start until widths.size
    return lines
}

private class WrapRowPolicy(private val horizontal: Dp, private val vertical: Dp) : MeasurePolicy {

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val gap = horizontal.roundToPx()
        val lineGap = vertical.roundToPx()
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val lines = wrapLines(placeables.map { it.width }, constraints.maxWidth, gap)
        val lineWidths = lines.map { line -> line.sumOf { placeables[it].width } + gap * (line.count() - 1) }
        val lineHeights = lines.map { line -> line.maxOf { placeables[it].height } }
        val width = (lineWidths.maxOrNull() ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (lineHeights.sum() + lineGap * (lines.size - 1).coerceAtLeast(0)).coerceIn(constraints.minHeight, constraints.maxHeight)
        return layout(width, height) {
            var y = 0
            lines.forEachIndexed { l, line ->
                var x = 0
                line.forEach { i ->
                    val p = placeables[i]
                    p.placeRelative(x, y + (lineHeights[l] - p.height) / 2)
                    x += p.width + gap
                }
                y += lineHeights[l] + lineGap
            }
        }
    }

    private fun IntrinsicMeasureScope.heightAt(measurables: List<IntrinsicMeasurable>, width: Int): Int {
        if (measurables.isEmpty()) return 0
        val gap = horizontal.roundToPx()
        val widths = measurables.map { it.maxIntrinsicWidth(Constraints.Infinity).coerceAtMost(width) }
        val lines = wrapLines(widths, width, gap)
        return lines.sumOf { line -> line.maxOf { measurables[it].minIntrinsicHeight(widths[it]) } } +
            vertical.roundToPx() * (lines.size - 1)
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        heightAt(measurables, width)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        heightAt(measurables, width)

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measurables.maxOfOrNull { it.minIntrinsicWidth(height) } ?: 0

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measurables.sumOf { it.maxIntrinsicWidth(height) } + horizontal.roundToPx() * (measurables.size - 1).coerceAtLeast(0)
}
