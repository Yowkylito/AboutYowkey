package dev.yowkey.portfolio.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.yowkey.portfolio.model.Link
import dev.yowkey.portfolio.ui.theme.Measure

@Composable
fun SectionHeader(number: Int, title: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = number.toString().padStart(2, '0'),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/**
 * A bordered card: flat at rest, depth from the outline so it reads the same in light and dark. On pointer hover
 * it lifts and gains a soft shadow; the card itself is not a button, so it never scales on press.
 */
@Composable
fun OutlinedCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = modifier.lift(hover, shape, pressable = false).hoverable(hover),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content,
    )
}

/** A non-interactive label, kept quiet (small, muted, no border) so it never competes with the content above it. */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** A list line whose numbers carry the emphasis; the marker stays neutral so the accent is left for the facts. */
@Composable
fun Bullet(text: String, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .padding(top = 9.dp)
                .size(5.dp)
                .background(MaterialTheme.colorScheme.outline, CircleShape),
        )
        EmphasizedText(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = Measure.Body),
        )
    }
}

/** Where the reader stands: availability as a quiet pill with an accent dot, the first thing the eye lands on. */
@Composable
fun StatusPill(text: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
        Row(
            Modifier.padding(start = 12.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

/**
 * Opens [link] with the platform's handler: a new tab on the web, the default browser or mail app on desktop.
 * Lifts on hover, gives a little on press, and shows the hand cursor, like a link should.
 */
@Composable
fun LinkButton(link: Link, modifier: Modifier = Modifier, primary: Boolean = false) {
    val uriHandler = LocalUriHandler.current
    val interaction = remember { MutableInteractionSource() }
    val m = modifier.lift(interaction, CircleShape).pointerHoverIcon(PointerIcon.Hand)
    if (primary) {
        Button(onClick = { uriHandler.openUri(link.url) }, modifier = m, interactionSource = interaction) { Text(link.label) }
    } else {
        OutlinedButton(onClick = { uriHandler.openUri(link.url) }, modifier = m, interactionSource = interaction) { Text(link.label) }
    }
}

/** A filled or outlined action with the same hover lift, press give and hand cursor as [LinkButton]. */
@Composable
fun ActionButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val m = modifier.lift(interaction, CircleShape).pointerHoverIcon(PointerIcon.Hand)
    if (primary) {
        Button(onClick = onClick, modifier = m, interactionSource = interaction) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = m, interactionSource = interaction) { Text(label) }
    }
}

/** A chevron drawn directly; [pointsLeft] mirrors it. Sized by the caller's touch target, drawn at 18dp. */
@Composable
fun ChevronGlyph(pointsLeft: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(18.dp)) {
        val stroke = 2.dp.toPx()
        val x0 = size.width * if (pointsLeft) 0.62f else 0.38f
        val x1 = size.width * if (pointsLeft) 0.32f else 0.68f
        drawLine(color, Offset(x0, size.height * 0.2f), Offset(x1, size.height * 0.5f), stroke, StrokeCap.Round)
        drawLine(color, Offset(x1, size.height * 0.5f), Offset(x0, size.height * 0.8f), stroke, StrokeCap.Round)
    }
}

/** Three-line menu glyph drawn directly, so it needs neither an icon library nor a font glyph. */
@Composable
fun MenuGlyph(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onBackground
    Canvas(modifier.size(20.dp)) {
        val stroke = 2.dp.toPx()
        listOf(0.25f, 0.5f, 0.75f).forEach { y ->
            drawLine(color, Offset(0f, size.height * y), Offset(size.width, size.height * y), stroke, StrokeCap.Round)
        }
    }
}
