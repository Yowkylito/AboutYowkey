package dev.yowkey.portfolio.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.yowkey.portfolio.model.Module
import dev.yowkey.portfolio.model.ModuleGraph
import dev.yowkey.portfolio.model.ModuleGraph.Slot
import dev.yowkey.portfolio.ui.theme.PortfolioTheme

/**
 * The module graph, drawn from [ModuleGraph.plan]: one box per module in dependency layers, the entry points on top
 * and the foundation at the bottom, with an arrow from each module down to what it depends on. An arrow that skips
 * layers runs through a lane kept free for it, so no arrow crosses a box, and a dependency already reached through
 * another module is not drawn twice (the boxes' screen-reader text still lists every direct dependency).
 *
 * Boxes are a fixed width (narrower on [compact] windows) and as tall as their text, so a long role wraps instead
 * of being cut. A layer wider than the screen scrolls sideways rather than shrinking the text; the twelve-module
 * cap keeps that to a box or two past the edge on a phone.
 *
 * The arrows are drawn behind the boxes in the draw phase from the positions the layout has just computed, so the
 * diagram is one layout node and its text, however many arrows it has.
 */
@Composable
fun ModuleDiagram(modules: List<Module>, compact: Boolean, modifier: Modifier = Modifier) {
    if (modules.isEmpty()) return
    val plan = remember(modules) { ModuleGraph.plan(modules) }
    // Top row first: the plan is foundation-first, and the diagram reads down from the entry points.
    val rows = remember(plan) { plan.layers.asReversed() }
    val nodes = remember(rows) { rows.flatMap { row -> row.filterIsInstance<Slot.Node>().map { it.module } } }
    val geometry = remember(plan) { DiagramGeometry(plan) }
    val boxWidth = if (compact) 144.dp else 172.dp
    val laneWidth = 8.dp
    val columnGap = if (compact) 12.dp else 20.dp
    val rowGap = if (compact) 40.dp else 48.dp
    val line = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val head = MaterialTheme.colorScheme.onSurfaceVariant

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val available = maxWidth
        val widest = rows.maxOf { row ->
            row.fold(columnGap * (row.size - 1)) { width, slot -> width + if (slot is Slot.Node) boxWidth else laneWidth }
        }
        val scrolls = widest > available
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Layout(
                content = { nodes.forEach { ModuleBox(it) } },
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .widthIn(min = available)
                    .drawBehind {
                        val stroke = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        val arrow = 5.dp.toPx()
                        plan.edges.indices.forEach { edge ->
                            val points = geometry.points(edge)
                            val end = points.last()
                            val path = Path().apply {
                                moveTo(points[0].x, points[0].y)
                                for (i in 1 until points.size) {
                                    val from = points[i - 1]
                                    val to = if (i == points.lastIndex) Offset(end.x, end.y - arrow) else points[i]
                                    if (from.x == to.x) {
                                        lineTo(to.x, to.y)
                                    } else {
                                        val bend = (to.y - from.y) / 2
                                        cubicTo(from.x, from.y + bend, to.x, to.y - bend, to.x, to.y)
                                    }
                                }
                            }
                            drawPath(path, line, style = stroke)
                            val tip = Path().apply {
                                moveTo(end.x, end.y)
                                lineTo(end.x - arrow * 0.7f, end.y - arrow * 1.2f)
                                lineTo(end.x + arrow * 0.7f, end.y - arrow * 1.2f)
                                close()
                            }
                            drawPath(tip, head)
                        }
                    },
            ) { measurables, constraints ->
                val box = boxWidth.roundToPx()
                val lane = laneWidth.roundToPx()
                val gap = columnGap.roundToPx()
                val vGap = rowGap.roundToPx()
                val placeables = measurables.map { it.measure(Constraints.fixedWidth(box)) }
                val rowWidth = rows.map { row -> row.sumOf { if (it is Slot.Node) box else lane } + gap * (row.size - 1) }
                val width = maxOf(rowWidth.max(), constraints.minWidth)
                val positions = IntArray(placeables.size * 2)
                var y = 0
                var node = 0
                rows.forEachIndexed { r, row ->
                    val layer = rows.lastIndex - r
                    val nodeCount = row.count { it is Slot.Node }
                    val height = (node until node + nodeCount).maxOf { placeables[it].height }
                    var x = (width - rowWidth[r]) / 2
                    row.forEach { slot ->
                        when (slot) {
                            is Slot.Node -> {
                                val top = y + (height - placeables[node].height) / 2
                                positions[node * 2] = x
                                positions[node * 2 + 1] = top
                                geometry.box(slot.module.name, x.toFloat(), top.toFloat(), box.toFloat(), placeables[node].height.toFloat())
                                x += box + gap
                                node++
                            }
                            is Slot.Lane -> {
                                geometry.lane(slot.edge, layer, x + lane / 2f)
                                x += lane + gap
                            }
                        }
                    }
                    geometry.row(layer, y.toFloat(), (y + height).toFloat())
                    y += height + vGap
                }
                layout(width, y - vGap) {
                    placeables.forEachIndexed { index, p -> p.place(positions[index * 2], positions[index * 2 + 1]) }
                }
            }
            Text(
                buildString {
                    append("Arrows point to what a module depends on; one already reached through another module is not drawn twice.")
                    if (scrolls) append(" Scroll sideways for the rest.")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One module: its build name in monospace, its role under it, and one screen-reader node for both. */
@Composable
private fun ModuleBox(module: Module, modifier: Modifier = Modifier) {
    val description = buildString {
        append("Module ${module.name}: ${module.role}.")
        if (module.dependsOn.isNotEmpty()) append(" Depends on ${module.dependsOn.joinToString()}.")
    }
    Column(
        modifier
            .clearAndSetSemantics { contentDescription = description }
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            module.name,
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(module.role, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Where the last layout put each box, lane and row, read by the arrows in the draw phase that follows it. Plain
 * maps, not state: layout always runs before draw in a frame, and a new layout invalidates the draw.
 */
private class DiagramGeometry(private val plan: ModuleGraph.Plan) {
    private val boxes = HashMap<String, FloatArray>() // x, y, width, height
    private val lanes = HashMap<Long, Float>() // (edge, layer) -> centre x
    private val rowTop = FloatArray(plan.layers.size)
    private val rowBottom = FloatArray(plan.layers.size)

    fun box(name: String, x: Float, y: Float, width: Float, height: Float) {
        boxes[name] = floatArrayOf(x, y, width, height)
    }

    fun lane(edge: Int, layer: Int, x: Float) {
        lanes[key(edge, layer)] = x
    }

    fun row(layer: Int, top: Float, bottom: Float) {
        rowTop[layer] = top
        rowBottom[layer] = bottom
    }

    /**
     * The arrow's course: off the bottom of its module, down each lane it crosses, onto the top of its target.
     * Arrows leaving (or reaching) one box spread across the middle 60% of its edge, ordered left to right by where
     * they head (or come from), so they leave side by side instead of on top of each other.
     */
    fun points(edge: Int): List<Offset> {
        val (from, to) = plan.edges[edge]
        val fromLayer = plan.layerOf.getValue(from)
        val toLayer = plan.layerOf.getValue(to)
        return buildList {
            add(Offset(anchorX(from, edge, outgoing = true), bottom(from)))
            for (layer in fromLayer - 1 downTo toLayer + 1) {
                val x = lanes.getValue(key(edge, layer))
                add(Offset(x, rowTop[layer]))
                add(Offset(x, rowBottom[layer]))
            }
            add(Offset(anchorX(to, edge, outgoing = false), boxes.getValue(to)[1]))
        }
    }

    private fun anchorX(name: String, edge: Int, outgoing: Boolean): Float {
        val siblings = plan.edges.indices
            .filter { if (outgoing) plan.edges[it].from == name else plan.edges[it].to == name }
            .sortedBy { if (outgoing) nextX(it) else previousX(it) }
        val b = boxes.getValue(name)
        val centre = b[0] + b[2] / 2
        if (siblings.size <= 1) return centre
        val spread = b[2] * 0.6f
        return centre - spread / 2 + spread * siblings.indexOf(edge) / (siblings.size - 1)
    }

    /** Where an arrow heads first: its first lane, or its target's centre. */
    private fun nextX(edge: Int): Float {
        val (from, to) = plan.edges[edge]
        return lanes[key(edge, plan.layerOf.getValue(from) - 1)] ?: centre(to)
    }

    /** Where an arrow comes in from: its last lane, or its source's centre. */
    private fun previousX(edge: Int): Float {
        val (from, to) = plan.edges[edge]
        return lanes[key(edge, plan.layerOf.getValue(to) + 1)] ?: centre(from)
    }

    private fun centre(name: String) = boxes.getValue(name).let { it[0] + it[2] / 2 }

    private fun bottom(name: String) = boxes.getValue(name).let { it[1] + it[3] }

    private fun key(edge: Int, layer: Int) = edge.toLong() shl 32 or layer.toLong()
}

private val previewModules = listOf(
    Module(":app", "Android entry point and navigation", listOf(":feature:trips", ":feature:ask", ":core:model")),
    Module(":feature:trips", "Trip planning screens", listOf(":core:data", ":core:ui")),
    Module(":feature:ask", "Camera translation and questions", listOf(":core:data", ":core:ui")),
    Module(":core:ui", "Theme and shared composables", listOf(":core:model")),
    Module(":core:data", "Room, repositories and sync", listOf(":core:model")),
    Module(":core:model", "Plain Kotlin domain types"),
    Module(":benchmark", "Macrobenchmarks for startup", listOf(":core:model")),
)

@Preview(widthDp = 412)
@Composable
private fun CompactModuleDiagramPreview() = PortfolioTheme {
    ModuleDiagram(previewModules, compact = true, modifier = Modifier.padding(20.dp))
}

@Preview(widthDp = 720)
@Composable
private fun ModuleDiagramDarkPreview() = PortfolioTheme(darkTheme = true) {
    ModuleDiagram(previewModules, compact = false, modifier = Modifier.padding(20.dp))
}
