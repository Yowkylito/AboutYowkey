package dev.yowkey.portfolio.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.yowkey.portfolio.model.PlatformKind

/*
 * Labels are one line, never wrapped: a chip is a unit, and an overlong one ends in an ellipsis instead.
 *
 * Three kinds of label, each with its own look so a glance tells them apart:
 * - TechChip, filled with a tint of the accent: the headline technology ("built with");
 * - PlatformChip, an outlined pill with a drawn glyph: where it runs;
 * - Tag (Components.kt), flat grey: the full stack, on the detail screen only.
 * None is interactive, so none looks like a Material chip.
 */

/**
 * Headline technology. onPrimaryContainer on a 12% accent tint over the card surface is 10.9:1 in light and
 * 11.2:1 in dark.
 */
@Composable
fun TechChip(text: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.clearAndSetSemantics { contentDescription = "Built with $text" },
        shape = RoundedCornerShape(8.dp),
        color = scheme.primary.copy(alpha = 0.12f).compositeOver(scheme.surface),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onPrimaryContainer,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/** A platform the project ships on: outlined pill, its glyph, its name (onSurfaceVariant: 6.9:1 light, 6.7:1 dark). */
@Composable
fun PlatformChip(platform: String, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = modifier.clearAndSetSemantics { contentDescription = "Runs on $platform" },
        shape = RoundedCornerShape(50),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            Modifier.padding(start = 9.dp, end = 11.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PlatformGlyph(PlatformKind.of(platform), color)
            Text(platform, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * [builtWith] chips, then [platforms] chips, wrapping as one group. Nothing when both are empty. A [WrapRow], so
 * the card row's equal-height measurement counts every line of chips.
 */
@Composable
fun TechAndPlatforms(builtWith: List<String>, platforms: List<String>, modifier: Modifier = Modifier) {
    if (builtWith.isEmpty() && platforms.isEmpty()) return
    WrapRow(modifier) {
        builtWith.forEach { TechChip(it) }
        platforms.forEach { PlatformChip(it) }
    }
}
