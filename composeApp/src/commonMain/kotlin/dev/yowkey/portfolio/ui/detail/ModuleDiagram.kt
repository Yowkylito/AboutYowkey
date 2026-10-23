package dev.yowkey.portfolio.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.yowkey.portfolio.model.Module
import dev.yowkey.portfolio.model.ModuleGraph
import dev.yowkey.portfolio.ui.components.WrapRow
import dev.yowkey.portfolio.ui.theme.PortfolioTheme

/**
 * The module graph as bands, from [ModuleGraph.layers]: the modules nothing else uses on top, the foundation at the
 * bottom, and an arrow between bands for the way use runs. A module only uses modules in the bands below it, and its
 * card names the ones it uses directly, so no arrow per dependency is drawn to tangle with the others.
 *
 * It fits any width without scrolling: every card is one width, as many to a line as fit at [MinCardWidth] (and no
 * more than the widest band needs), and a band with more modules than that wraps onto lines as even as they go
 * ([evenLines]). Lines are centred, and the cards in a line are as tall as the tallest, so a long role wraps
 * instead of being cut.
 */
@Composable
fun ModuleDiagram(modules: List<Module>, modifier: Modifier = Modifier) {
    if (modules.isEmpty()) return
    // Top band first: the layers are foundation-first, and the diagram reads down from the entry points.
    val bands = remember(modules) { ModuleGraph.layers(modules).asReversed() }
    val line = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val head = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = ((maxWidth + CardGap) / (MinCardWidth + CardGap)).toInt().coerceIn(1, bands.maxOf { it.size })
            val cardWidth = (maxWidth - CardGap * (columns - 1)) / columns
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                bands.forEachIndexed { index, band ->
                    if (index > 0) DownArrow(line, head)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(CardGap)) {
                        evenLines(band, columns).forEach { cards ->
                            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(CardGap)) {
                                cards.forEach { ModuleCard(it, Modifier.width(cardWidth).fillMaxHeight()) }
                            }
                        }
                    }
                }
            }
        }
        Text(
            "Read down: a module uses only modules in the bands below it, and its card names the ones it uses directly.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * [band] on as few lines as [columns] allows, their lengths at most one apart and the longer first: four in three
 * columns are two and two, not three and a stray one that reads as a band of its own.
 */
internal fun <T> evenLines(band: List<T>, columns: Int): List<List<T>> {
    val lines = (band.size + columns - 1) / columns
    val sizes = List(lines) { band.size / lines + if (it < band.size % lines) 1 else 0 }
    val ends = sizes.runningReduce { end, size -> end + size }
    return ends.mapIndexed { index, end -> band.subList(end - sizes[index], end) }
}

/**
 * One module: its build name in monospace, its role, and at the foot what it uses, lined up across the cards of a
 * line. One screen-reader node for all of it.
 */
@Composable
private fun ModuleCard(module: Module, modifier: Modifier = Modifier) {
    val uses = module.dependsOn.distinct()
    val description = buildString {
        append("Module ${module.name}: ${module.role}.")
        if (uses.isNotEmpty()) append(" Uses ${uses.joinToString()}.")
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val named = MaterialTheme.colorScheme.onSurface
    Column(
        modifier
            .clearAndSetSemantics { contentDescription = description }
            .background(MaterialTheme.colorScheme.surface, CardShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CardShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            module.name,
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.primary,
        )
        Text(module.role, style = MaterialTheme.typography.bodySmall, color = muted)
        if (uses.isNotEmpty()) {
            Spacer(Modifier.weight(1f))
            // One item per name, so a line breaks between names rather than inside a path like packages/cattown;
            // the no-break space keeps each dot on the line of the name it follows.
            val small = MaterialTheme.typography.bodySmall
            WrapRow(Modifier.padding(top = 4.dp), horizontalSpacing = 4.dp, verticalSpacing = 0.dp) {
                Text("uses", style = small, color = muted)
                uses.forEachIndexed { index, name ->
                    Text(
                        if (index < uses.lastIndex) "$name ·" else name,
                        style = small.copy(fontFamily = FontFamily.Monospace),
                        color = named,
                    )
                }
            }
        }
    }
}

/** The arrow between two bands, pointing down to the band the one above it builds on. */
@Composable
private fun DownArrow(line: Color, head: Color) {
    Spacer(
        Modifier.size(width = 12.dp, height = 28.dp).drawWithCache {
            val x = size.width / 2
            val arrow = 5.dp.toPx()
            val end = size.height - 5.dp.toPx()
            val tip = Path().apply {
                moveTo(x, end)
                lineTo(x - arrow * 0.7f, end - arrow * 1.2f)
                lineTo(x + arrow * 0.7f, end - arrow * 1.2f)
                close()
            }
            onDrawBehind {
                drawLine(line, Offset(x, 5.dp.toPx()), Offset(x, end - arrow), 1.2.dp.toPx(), StrokeCap.Round)
                drawPath(tip, head)
            }
        },
    )
}

private val MinCardWidth = 160.dp
private val CardGap = 12.dp
private val CardShape = RoundedCornerShape(10.dp)

private val previewModules = listOf(
    Module(":app", "Android entry point and navigation", listOf(":feature:trips", ":feature:ask", ":core:model")),
    Module(":feature:trips", "Trip planning screens", listOf(":core:data", ":core:ui")),
    Module(":feature:ask", "Camera translation and questions", listOf(":core:data", ":core:ui")),
    Module(":core:ui", "Theme and shared composables", listOf(":core:model")),
    Module(":core:data", "Room, repositories and sync", listOf(":core:model")),
    Module(":core:sync", "WorkManager jobs", listOf(":core:model")),
    Module(":core:model", "Plain Kotlin domain types"),
    Module(":benchmark", "Macrobenchmarks for startup", listOf(":core:model")),
)

@Preview(widthDp = 412)
@Composable
private fun CompactModuleDiagramPreview() = PortfolioTheme {
    ModuleDiagram(previewModules, modifier = Modifier.padding(20.dp))
}

@Preview(widthDp = 720)
@Composable
private fun ModuleDiagramDarkPreview() = PortfolioTheme(darkTheme = true) {
    ModuleDiagram(previewModules, modifier = Modifier.padding(20.dp))
}
