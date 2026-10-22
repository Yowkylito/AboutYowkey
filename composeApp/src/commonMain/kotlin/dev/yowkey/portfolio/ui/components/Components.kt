package dev.yowkey.portfolio.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.yowkey.portfolio.model.Link

private val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)

/**
 * A 0→1 entrance progress for the lazy row keyed [key]. Read it inside `graphicsLayer {}` so the animation only
 * re-runs the layer phase, never composition. Rows already seen (in [seen]) or under reduced motion start at 1.
 */
@Composable
fun rememberRevealProgress(key: Any, seen: MutableSet<Any>, reducedMotion: Boolean): Animatable<Float, *> {
    val progress = remember(key) { Animatable(if (reducedMotion || key in seen) 1f else 0f) }
    LaunchedEffect(key) {
        if (progress.value < 1f) {
            seen += key
            progress.animateTo(1f, tween(durationMillis = 520, easing = EaseOutCubic))
        }
    }
    return progress
}

@Composable
fun SectionHeader(number: Int, title: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = number.toString().padStart(2, '0'),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/** A bordered, flat card: depth from the outline, not shadows, so it reads the same in light and dark. */
@Composable
fun OutlinedCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content,
    )
}

/** A non-interactive label; deliberately not a Chip so it does not look tappable. */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
fun Bullet(text: String, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .padding(top = 9.dp)
                .size(5.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Opens [link] with the platform's handler: a new tab on the web, the default browser or mail app on desktop. */
@Composable
fun LinkButton(link: Link, modifier: Modifier = Modifier, primary: Boolean = false) {
    val uriHandler = LocalUriHandler.current
    if (primary) {
        Button(onClick = { uriHandler.openUri(link.url) }, modifier = modifier) { Text(link.label) }
    } else {
        OutlinedButton(onClick = { uriHandler.openUri(link.url) }, modifier = modifier) { Text(link.label) }
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
