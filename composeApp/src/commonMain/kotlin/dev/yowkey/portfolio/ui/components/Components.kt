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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.yowkey.portfolio.model.Link
import dev.yowkey.portfolio.ui.theme.LocalThemeSwitch

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

/**
 * A bordered, flat card: depth from the outline, not shadows, so it reads the same in light and dark. With
 * [onClick] the whole card is one tap target, labelled [onClickLabel] for screen readers.
 */
@Composable
fun OutlinedCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        if (onClick == null) {
            content()
        } else {
            // Inside the Surface, so the ripple is clipped to its corners; min constraints pass through so the
            // content can still fill an equal-height row.
            Box(
                Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick),
                propagateMinConstraints = true,
            ) { content() }
        }
    }
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

/**
 * A small status label, such as a project's repository visibility. Outlined rather than filled, so it reads as a
 * fact about the card and not as one more stack [Tag]; not interactive either. onSurfaceVariant on the card
 * surface is 7.0:1 in light and 6.6:1 in dark, so it clears AA at this size.
 */
@Composable
fun StatusBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.2.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/**
 * Plays or pauses a clip the reader opened. Shown only when the system asks for reduced motion, where nothing plays
 * on its own; motion the reader starts is what that setting still allows, so the animations stay one tap away.
 * Drawn, not an icon font: a triangle to play, two bars to pause.
 */
@Composable
fun PlayPauseButton(playing: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val label = if (playing) "Pause the animation" else "Play the animation"
    Box(
        modifier
            .size(56.dp)
            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onToggle)
            .semantics { contentDescription = label },
    ) {
        Canvas(Modifier.size(56.dp)) {
            val c = size.center
            val r = size.minDimension / 2
            if (playing) {
                val w = r * 0.16f
                val h = r * 0.42f
                drawRect(Color.White, Offset(c.x - r * 0.26f, c.y - h), androidx.compose.ui.geometry.Size(w, h * 2))
                drawRect(Color.White, Offset(c.x + r * 0.10f, c.y - h), androidx.compose.ui.geometry.Size(w, h * 2))
            } else {
                drawPath(
                    Path().apply {
                        moveTo(c.x - r * 0.24f, c.y - r * 0.40f)
                        lineTo(c.x + r * 0.42f, c.y)
                        lineTo(c.x - r * 0.24f, c.y + r * 0.40f)
                        close()
                    },
                    Color.White,
                )
            }
        }
    }
}

/**
 * The play mark over a clip's poster: a dark disc with a white triangle, legible on any image. Drawn, not an
 * icon font; the path is built once per size in `drawWithCache`.
 */
@Composable
fun PlayGlyph(modifier: Modifier = Modifier) {
    Spacer(
        modifier.size(40.dp).drawWithCache {
            val r = size.minDimension / 2
            val center = size.center
            val triangle = Path().apply {
                moveTo(center.x - r * 0.28f, center.y - r * 0.4f)
                lineTo(center.x + r * 0.45f, center.y)
                lineTo(center.x - r * 0.28f, center.y + r * 0.4f)
                close()
            }
            onDrawBehind {
                drawCircle(Color.Black.copy(alpha = 0.6f), radius = r)
                drawPath(triangle, Color.White)
            }
        },
    )
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

/**
 * Opens [link] with the platform's handler: a new tab on the web, the default browser or mail app on desktop. Then,
 * and only then, [onOpened] hears of it (to count the click), so nothing it does can hold up or stop the link.
 */
@Composable
fun LinkButton(link: Link, modifier: Modifier = Modifier, primary: Boolean = false, onOpened: () -> Unit = {}) {
    val uriHandler = LocalUriHandler.current
    val open = {
        uriHandler.openUri(link.url)
        onOpened()
    }
    if (primary) {
        Button(onClick = open, modifier = modifier) { Text(link.label) }
    } else {
        OutlinedButton(onClick = open, modifier = modifier) { Text(link.label) }
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

/**
 * The light/dark switch in the page's bars. The glyph shows what a press gives, a moon on the light page and a sun
 * on the dark one; the label says it in words for the accessibility tree.
 */
@Composable
fun ThemeToggle(modifier: Modifier = Modifier) {
    val theme = LocalThemeSwitch.current
    val label = if (theme.dark) "Switch to the light theme" else "Switch to the dark theme"
    IconButton(onClick = theme.toggle, modifier = modifier.semantics { contentDescription = label }) {
        ThemeGlyph(dark = theme.dark, color = MaterialTheme.colorScheme.onBackground)
    }
}
