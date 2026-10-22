package dev.yowkey.portfolio.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.Modifier.Node
import dev.yowkey.portfolio.ui.theme.LocalMotion
import dev.yowkey.portfolio.ui.theme.Motion
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Hover lifts the element and raises its shadow; a press scales it down slightly. Both are driven by
 * [interactionSource] (pass the one given to `clickable`/`Button`, or a `hoverable` one for static cards) and
 * applied in the layer phase: hovering never recomposes or re-measures anything. Reduced motion keeps the
 * shadow change and drops the movement.
 */
fun Modifier.lift(interactionSource: InteractionSource, shape: Shape, pressable: Boolean = true): Modifier =
    this then LiftElement(interactionSource, shape, pressable)

private data class LiftElement(
    val interactionSource: InteractionSource,
    val shape: Shape,
    val pressable: Boolean,
) : ModifierNodeElement<LiftNode>() {
    override fun create() = LiftNode(interactionSource, shape, pressable)

    override fun update(node: LiftNode) = node.update(interactionSource, shape, pressable)
}

private class LiftNode(
    private var interactionSource: InteractionSource,
    private var shape: Shape,
    private var pressable: Boolean,
) : Node(), LayoutModifierNode, CompositionLocalConsumerModifierNode {

    private val hover = Animatable(0f)
    private val press = Animatable(0f)
    private var collector: Job? = null

    // One lambda for the node's lifetime; it reads the animated values, so only the layer is invalidated.
    private val layer: GraphicsLayerScope.() -> Unit = {
        val motion = currentValueOf(LocalMotion)
        val h = hover.value
        translationY = -motion.hoverLift.toPx() * h
        shadowElevation = Motion.HoverElevation.toPx() * h
        shape = this@LiftNode.shape
        val scale = 1f - (1f - motion.pressScale) * press.value
        scaleX = scale
        scaleY = scale
    }

    override fun onAttach() = collect()

    fun update(source: InteractionSource, shape: Shape, pressable: Boolean) {
        this.shape = shape
        this.pressable = pressable
        if (source != interactionSource) {
            interactionSource = source
            collector?.cancel()
            collect()
        }
    }

    private fun collect() {
        collector = coroutineScope.launch {
            val hovers = HashSet<HoverInteraction.Enter>()
            val presses = HashSet<PressInteraction.Press>()
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is HoverInteraction.Enter -> hovers += interaction
                    is HoverInteraction.Exit -> hovers -= interaction.enter
                    is PressInteraction.Press -> presses += interaction
                    is PressInteraction.Release -> presses -= interaction.press
                    is PressInteraction.Cancel -> presses -= interaction.press
                }
                val spec = currentValueOf(LocalMotion).state<Float>()
                launch { hover.animateTo(if (hovers.isEmpty()) 0f else 1f, spec) }
                if (pressable) launch { press.animateTo(if (presses.isEmpty()) 0f else 1f, spec) }
            }
        }
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.placeWithLayer(0, 0, layerBlock = layer) }
    }

    // Pass intrinsics straight through: CardRow asks for them to make cards equal height.
    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int) =
        measurable.minIntrinsicHeight(width)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int) =
        measurable.maxIntrinsicHeight(width)

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int) =
        measurable.minIntrinsicWidth(height)

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int) =
        measurable.maxIntrinsicWidth(height)
}
